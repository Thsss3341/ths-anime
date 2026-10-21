package eu.kanade.tachiyomi.animeextension.zh.xiaobao

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
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
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

/**
 * 小宝影院 (formerly 小新TV): films, series (US, Korean, Japanese, Chinese…), variety, anime and
 * documentaries. A MacCMS site with the "myui" theme; every play page carries a direct m3u8 link.
 */
class Xiaobao : AnimeHttpSource(), ConfigurableAnimeSource {
    override val name = "小宝影院"
    override val lang = "zh"
    override val baseUrl = "https://xiaoheimi.cc"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", DESKTOP_USER_AGENT)
        .set("Referer", "$baseUrl/")

    /** The site lists one category at a time; Popular and Latest use the one chosen in the settings. */
    private val homeCategory: String
        get() = preferences.getString(PREF_HOME_CATEGORY, DEFAULT_HOME_CATEGORY) ?: DEFAULT_HOME_CATEGORY

    // ============================== Browse ==============================

    override fun popularAnimeRequest(page: Int) = GET(showUrl(page, homeCategory, "hits"), headers)

    override fun popularAnimeParse(response: Response) = parseShowPage(response.asJsoup())

    override fun latestUpdatesRequest(page: Int) = GET(showUrl(page, homeCategory, "time"), headers)

    override fun latestUpdatesParse(response: Response) = parseShowPage(response.asJsoup())

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val variants = SearchTerms.variants(query)
        if (variants.isEmpty()) {
            val url = showUrl(
                page,
                category = filters.selected<CategoryFilter>() ?: homeCategory,
                by = filters.selected<SortFilter>() ?: "time",
                area = filters.selected<AreaFilter>(),
                lang = filters.selected<LangFilter>(),
                year = filters.selected<YearFilter>(),
            )
            return parseShowPage(client.newCall(GET(url, headers)).awaitSuccess().asJsoup())
        }
        return SearchTerms.merge(
            variants.map { keyword ->
                val url = "$baseUrl/index.php/vod/search/page/$page/wd/${keyword.encodePath()}.html"
                parseSearchPage(client.newCall(GET(url, headers)).awaitSuccess().asJsoup())
            },
        )
    }

    override fun getFilterList() = AnimeFilterList(
        CategoryFilter(CategoryFilter.indexOf(homeCategory)),
        SortFilter(),
        AreaFilter(),
        LangFilter(),
        YearFilter(),
    )

    private inline fun <reified T : PathFilter> AnimeFilterList.selected() = filterIsInstance<T>().firstOrNull()?.selected

    /** e.g. /index.php/vod/show/area/美国/by/hits/id/2/year/2025/page/1.html */
    private fun showUrl(
        page: Int,
        category: String,
        by: String,
        area: String? = null,
        lang: String? = null,
        year: String? = null,
    ) = buildString {
        append("$baseUrl/index.php/vod/show")
        area?.let { append("/area/${it.encodePath()}") }
        append("/by/$by/id/$category")
        lang?.let { append("/lang/${it.encodePath()}") }
        year?.let { append("/year/$it") }
        append("/page/$page.html")
    }

    private fun String.encodePath() = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun parseShowPage(document: Document): AnimesPage {
        val animes = document.select(".myui-vodlist__box a.myui-vodlist__thumb[href*=/vod/detail/]").map { it.toSAnime() }
        return AnimesPage(animes.distinctBy { it.url }, document.hasNextPage())
    }

    private fun parseSearchPage(document: Document): AnimesPage {
        // With no results, the page says so and shows unrelated suggestions instead.
        if (document.selectFirst("#searchList")?.text()?.contains("没有找到") == true) return AnimesPage(emptyList(), false)
        if (document.selectFirst("#searchList") == null && document.text().contains("验证码")) {
            throw Exception("网站要求输入验证码，请在 WebView 中搜索一次后再试")
        }
        val animes = document.select("#searchList li a.myui-vodlist__thumb[href*=/vod/detail/]").map { it.toSAnime() }
        return AnimesPage(animes.distinctBy { it.url }, document.hasNextPage())
    }

    private fun org.jsoup.nodes.Element.toSAnime() = SAnime.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = attr("title").trim()
        thumbnail_url = attr("data-original").ifBlank { null }
    }

    /** The "下一页" link points back at the current page on the last page. */
    private fun Document.hasNextPage(): Boolean {
        val current = location().let { PAGE_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 1
        val next = select(".myui-page a:contains(下一页)").attr("href")
            .let { PAGE_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: return false
        return next > current
    }

    // ============================== Details =============================

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        val data = document.select(".myui-content__detail p.data")
        fun field(label: String) = data.firstOrNull { it.text().contains(label) }
            ?.text()?.substringAfter(label)?.substringBefore("　")?.trim()?.ifBlank { null }
        return SAnime.create().apply {
            title = document.selectFirst(".myui-content__detail h1.title")?.text()?.trim().orEmpty()
            thumbnail_url = document.selectFirst(".myui-content__thumb img")?.attr("data-original")?.ifBlank { null }
            val info = data.firstOrNull { it.text().contains("分类：") }
            val category = info?.select("a")?.firstOrNull()?.text()
            val area = info?.select("a")?.getOrNull(1)?.text()
            val year = info?.select("a")?.getOrNull(2)?.text()
            genre = listOfNotNull(category, area).joinToString().ifEmpty { null }
            author = data.firstOrNull { it.text().contains("导演：") }?.select("a")?.joinToString { it.text() }?.ifBlank { null }
            artist = data.firstOrNull { it.text().contains("主演：") }?.select("a")?.joinToString { it.text() }?.ifBlank { null }
            val update = field("更新：")
            status = when {
                update == null -> SAnime.UNKNOWN
                "完结" in update -> SAnime.COMPLETED
                else -> SAnime.ONGOING
            }
            val synopsis = (document.selectFirst("#desc span.data") ?: document.selectFirst("#desc .sketch"))
                ?.text()?.trim()
            description = listOfNotNull(
                synopsis?.ifBlank { null },
                listOfNotNull(year, area, category).joinToString(" / ").ifEmpty { null },
                update?.let { "更新：$it" },
            ).joinToString("\n\n").ifEmpty { null }
        }
    }

    // ============================= Episodes =============================

    /** Every line repeats the episode list; the one with the most episodes is used. */
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val longest = document.select("[id^=playlist] ul").maxByOrNull { it.select("li a").size } ?: return emptyList()
        return longest.select("li a").mapIndexed { index, link ->
            SEpisode.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text().trim()
                episode_number = EPISODE_NUMBER_REGEX.matchEntire(name)?.groupValues?.get(1)?.toFloatOrNull()
                    ?: (index + 1).toFloat()
            }
        }.reversed()
    }

    // =============================== Video ==============================

    /** The episode on its own line first, then the same episode on any other line. */
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val playUrl = response.request.url.toString()
        val match = PLAY_URL_REGEX.find(playUrl) ?: throw Exception("请刷新剧集列表")
        val (vodId, sid, nid) = match.destructured
        // Play pages show each line as a "#playerN" tab pane, with tab links only when there are several.
        val lineNames = document.select("a[href^=#player]")
            .associate { it.attr("href").removePrefix("#player") to it.text().trim() }
        val lineIds = (document.select("div.tab-pane[id^=player]").map { it.id().removePrefix("player") } + lineNames.keys)
            .filter { it.all(Char::isDigit) && it.isNotEmpty() }.distinct()
        val videos = mutableListOf<Video>()
        document.playerUrl()?.let { videos += Video(it, lineNames[sid] ?: "线路$sid", it, headers) }
        for (otherSid in lineIds.filter { it != sid }) {
            val url = "$baseUrl/index.php/vod/play/id/$vodId/sid/$otherSid/nid/$nid.html"
            val stream = runCatching { client.newCall(GET(url, headers)).execute().asJsoup().playerUrl() }.getOrNull()
                ?: continue
            videos += Video(stream, lineNames[otherSid] ?: "线路$otherSid", stream, headers)
        }
        return videos.ifEmpty { throw Exception("找不到视频") }
    }

    /** MacCMS player data: `var player_aaaa = {"url": ..., "encrypt": 0|1|2, ...}`. */
    private fun Document.playerUrl(): String? {
        val script = select("script").map { it.data() }.firstOrNull { "player_aaaa" in it } ?: return null
        val objectText = script.substringAfter("player_aaaa").substringAfter("=").trim()
        val data = json.decodeFromString<JsonObject>(objectText.substringBeforeLast("}") + "}")
        val raw = data["url"]?.jsonPrimitive?.contentOrNull ?: return null
        val url = when (data["encrypt"]?.jsonPrimitive?.intOrNull) {
            1 -> unescape(raw)
            2 -> unescape(String(Base64.decode(raw, Base64.DEFAULT)))
            else -> raw
        }
        return url.takeIf { it.startsWith("http") }
    }

    /** JavaScript's unescape(): %XX and %uXXXX. */
    private fun unescape(text: String): String = UNESCAPE_REGEX.replace(text) {
        val hex = it.groupValues[1].ifEmpty { it.groupValues[2] }
        hex.toInt(16).toChar().toString()
    }

    // Search goes through getSearchAnime, which may run several requests.
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        throw UnsupportedOperationException()
    override fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()

    // ============================= Settings =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_HOME_CATEGORY
            title = "热门/最新显示的分类"
            entries = HOME_CATEGORIES.map { it.first }.toTypedArray()
            entryValues = HOME_CATEGORIES.map { it.second }.toTypedArray()
            setDefaultValue(DEFAULT_HOME_CATEGORY)
            summary = "%s（网站一次只能列出一个分类）"
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_HOME_CATEGORY = "PREF_HOME_CATEGORY"
        private const val DEFAULT_HOME_CATEGORY = "2"
        private val HOME_CATEGORIES = listOf(
            "连续剧" to "2",
            "电影" to "1",
            "综艺" to "3",
            "动漫" to "4",
            "纪录片" to "21",
        )

        private val PAGE_REGEX = Regex("""/page/(\d+)""")
        private val PLAY_URL_REGEX = Regex("""/vod/play/id/(\d+)/sid/(\d+)/nid/(\d+)""")
        private val EPISODE_NUMBER_REGEX = Regex("""第?\s*(\d+(?:\.\d+)?)\s*[集话話期]?""")
        private val UNESCAPE_REGEX = Regex("""%u([0-9a-fA-F]{4})|%([0-9a-fA-F]{2})""")

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36"
    }
}
