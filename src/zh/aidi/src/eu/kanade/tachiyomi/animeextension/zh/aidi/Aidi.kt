package eu.kanade.tachiyomi.animeextension.zh.aidi

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * 爱迪影视: films, series (US, UK, Korean, Japanese…), variety and documentaries, mostly in 1080p.
 * The site runs MacCMS and leaves its JSON API open, which gives search, details and every line's
 * direct m3u8 links, so nothing here parses HTML except the ranking page used for "Popular".
 */
class Aidi : AnimeHttpSource(), ConfigurableAnimeSource {
    override val name = "爱迪影视"
    override val lang = "zh"
    override val baseUrl = "https://aidi.tv"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api.php/provide/vod/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", DESKTOP_USER_AGENT)
        .set("Referer", "$baseUrl/")

    // ============================== Browse ==============================

    /** The site's ranking page: the top 8 of films, series, variety, anime, Korean films and documentaries. */
    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val html = client.newCall(GET("$baseUrl/rank/", headers)).awaitSuccess().body.string()
        val ids = RANK_ITEM_REGEX.findAll(html).map { it.groupValues[1].toLong() }.distinct().toList()
        if (ids.isEmpty()) throw Exception("无法读取排行榜")
        // The API returns at most 20 videos per request.
        val byId = ids.chunked(API_PAGE_SIZE).flatMap { vodList(ids = it).list }.associateBy { it.id }
        return AnimesPage(ids.mapNotNull { byId[it]?.toSAnime() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = vodList(page = page).toAnimesPage()

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val category = filters.filterIsInstance<CategoryFilter>().firstOrNull()?.selected
        val variants = SearchTerms.variants(query)
        if (variants.isEmpty()) return vodList(page = page, category = category).toAnimesPage()
        return SearchTerms.merge(variants.map { vodList(page = page, keyword = it, category = category).toAnimesPage() })
    }

    override fun getFilterList() = AnimeFilterList(CategoryFilter())

    private suspend fun vodList(
        page: Int = 1,
        keyword: String? = null,
        category: String? = null,
        ids: List<Long>? = null,
    ): VodListDto {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("ac", "detail")
            .addQueryParameter("pg", "$page")
        keyword?.let { url.addQueryParameter("wd", it) }
        category?.let { url.addQueryParameter("t", it) }
        ids?.let { url.addQueryParameter("ids", it.joinToString(",")) }
        val body = client.newCall(GET(url.build(), headers)).awaitSuccess().body.string()
        return json.decodeFromString<VodListDto>(body)
    }

    private fun VodListDto.toAnimesPage() = AnimesPage(list.map { it.toSAnime() }, page < pagecount)

    private fun VodDto.toSAnime() = SAnime.create().apply {
        url = "/vod/${this@toSAnime.id}"
        title = name.replace(SPACES_REGEX, " ").trim()
        thumbnail_url = pic
        author = director?.ifBlank { null }
        artist = actor?.ifBlank { null }
        genre = (listOfNotNull(typeName) + classes.orEmpty().split(',', '，'))
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString().ifEmpty { null }
        status = when (isEnd) {
            1 -> SAnime.COMPLETED
            0 -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }
        description = buildString {
            content?.let { append(Jsoup.parse(it).text().trim()) }
            val facts = listOfNotNull(
                sub?.ifBlank { null }?.let { "别名：$it" },
                listOfNotNull(year, area, language).filter { it.isNotBlank() && it != "0" }
                    .joinToString(" / ").ifEmpty { null },
                doubanScore?.takeIf { it.toDoubleOrNull()?.let { s -> s > 0 } == true }?.let { "豆瓣：$it" },
                remarks?.ifBlank { null },
            )
            if (facts.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(facts.joinToString("\n"))
            }
        }.ifEmpty { null }
    }

    // ============================== Details =============================

    override fun getAnimeUrl(anime: SAnime): String = "$baseUrl/play/${anime.vodId()}-1-1.html"

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = vod(anime.vodId()).toSAnime()

    private suspend fun vod(id: Long): VodDto =
        vodList(ids = listOf(id)).list.firstOrNull() ?: throw Exception("这部影片已不在网站上")

    private fun SAnime.vodId(): Long =
        VOD_ID_REGEX.find(url)?.groupValues?.get(1)?.toLong() ?: throw Exception("无法识别的链接，请重新搜索")

    // ============================= Episodes =============================

    /**
     * One entry per episode. Every line has its own copy of each episode, so the episodes come
     * from the line with the most, and the video list offers the same episode on every line.
     */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val vod = vod(anime.vodId())
        val longest = vod.playLines().maxByOrNull { it.episodes.size } ?: return emptyList()
        return longest.episodes.mapIndexed { index, (name, _) ->
            SEpisode.create().apply {
                url = "/play/${vod.id}-1-${index + 1}.html"
                this.name = name
                // Only "第3集" / "03"-style names carry the number; films are named e.g. "1080P".
                episode_number = EPISODE_NUMBER_REGEX.matchEntire(name.trim())?.groupValues?.get(1)?.toFloatOrNull()
                    ?: (index + 1).toFloat()
            }
        }.reversed()
    }

    private fun VodDto.playLines(): List<PlayLine> {
        val codes = playFrom.orEmpty().split("$$$")
        val lists = playUrl.orEmpty().split("$$$")
        return codes.zip(lists).map { (code, list) ->
            PlayLine(
                code.trim(),
                list.split('#').filter { it.isNotBlank() }.map { entry ->
                    if ('$' in entry) entry.substringBefore('$').trim() to entry.substringAfter('$').trim() else "正片" to entry.trim()
                },
            )
        }.filter { line -> line.episodes.any { it.second.startsWith("http") } }
    }

    // =============================== Video ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val match = EPISODE_URL_REGEX.find(episode.url) ?: throw Exception("请刷新剧集列表")
        val vod = vod(match.groupValues[1].toLong())
        val index = match.groupValues[2].toInt() - 1
        val preferred = preferences.getString(PREF_LINE, DEFAULT_LINE)
        return vod.playLines()
            .mapNotNull { line ->
                val url = line.episodes.getOrNull(index)?.second?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                line.code to Video(url, LINE_NAMES[line.code] ?: line.code, url, headers)
            }
            .sortedByDescending { (code, _) -> code == preferred }
            .map { it.second }
            .ifEmpty { throw Exception("这一集暂时没有可播放的线路") }
    }

    // ============================= Settings =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_LINE
            title = "首选播放线路"
            entries = PREFERRED_LINES.map { LINE_NAMES.getValue(it) }.toTypedArray()
            entryValues = PREFERRED_LINES.toTypedArray()
            setDefaultValue(DEFAULT_LINE)
            summary = "当前选择：%s（其他线路仍会列在后面）"
        }.also(screen::addPreference)
    }

    // ============================== Unused ==============================

    override fun popularAnimeRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        throw UnsupportedOperationException()
    override fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()
    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()
    override fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()

    companion object {
        private const val PREF_LINE = "PREF_LINE"

        // In tests, the app line had the highest bitrate (e.g. 6.3 Mbps vs 4.7 Mbps on the VIP line).
        private const val DEFAULT_LINE = "yidongyun"
        private val PREFERRED_LINES = listOf("yidongyun", "vip", "aidiyunsi", "jisu", "adysmp4")

        // Names from the site's playerconfig.js.
        private val LINE_NAMES = mapOf(
            "yidongyun" to "APP专用线路",
            "vip" to "VIP线路",
            "aidiyunsi" to "蓝光四",
            "jisu" to "蓝光五",
            "adysmp4" to "蓝光直链",
            "qiepian" to "切片线路",
        )

        private val RANK_ITEM_REGEX = Regex("""class="vod-rank-vod[^"]*"\s+href="/[a-z]+/(\d+)\.html"""")
        private val VOD_ID_REGEX = Regex("""/(?:vod|play)/(\d+)""")
        private val EPISODE_URL_REGEX = Regex("""/play/(\d+)-\d+-(\d+)\.html""")
        private val EPISODE_NUMBER_REGEX = Regex("""第?\s*(\d+(?:\.\d+)?)\s*[集话話期]?""")
        private const val API_PAGE_SIZE = 20
        private val SPACES_REGEX = Regex("""\s+""")

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36"
    }
}
