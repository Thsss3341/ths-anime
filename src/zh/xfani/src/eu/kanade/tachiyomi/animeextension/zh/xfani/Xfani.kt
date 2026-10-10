package eu.kanade.tachiyomi.animeextension.zh.xfani

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
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.parser.Parser
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * 稀饭动漫. The site was rebuilt in 2026: the old MacCMS endpoints on anime.xifanacg.com are gone
 * (every request now redirects to the new homepage). The new site at next.xifanacg.com reads its
 * data from a Supabase backend at api.xifanacg.com, which this source talks to directly with the
 * same public key the website sends from every browser.
 */
class Xfani : AnimeHttpSource(), ConfigurableAnimeSource {
    // Keep name/lang identical to yuzono's Xfani so the source ID (and library entries) stay the same.
    override val name = "稀饭动漫"
    override val lang = "zh"
    override val baseUrl = "https://next.xifanacg.com"
    override val supportsLatest = true

    private val apiUrl = "https://api.xifanacg.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val apiHeaders: Headers by lazy {
        headers.newBuilder()
            .set("apikey", API_KEY)
            .set("Authorization", "Bearer $API_KEY")
            .build()
    }

    // ============================== Browse ==============================

    override suspend fun getPopularAnime(page: Int): AnimesPage =
        searchAnimes(page, "", AnimeFilterList(SortFilter(state = SORT_BY_VIEWS)))

    /** The site's "recently updated" page lists about 50 anime on a single page. */
    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val body = client.newCall(GET("$baseUrl/recent", rscHeaders)).awaitSuccess().body.string()
        val start = body.indexOf(ITEM_LIST_MARKER)
        if (start < 0) throw Exception("无法读取最近更新")
        val list = json.decodeFromString<ItemListDto>(extractJson(body, start, '{', '}'))
        val animes = list.itemListElement.mapNotNull { element ->
            val id = ANIME_ID_REGEX.find(element.item.url)?.groupValues?.get(1) ?: return@mapNotNull null
            SAnime.create().apply {
                url = "/anime/$id"
                title = element.item.name
                thumbnail_url = element.item.image
            }
        }
        return AnimesPage(animes, false)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        searchAnimes(page, query.trim(), filters)

    private suspend fun searchAnimes(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val body = buildJsonObject {
            put("search_term", query)
            put("page_number", page)
            put("items_per_page", PAGE_SIZE)
            put("sort_order", "desc")
            put("sort_by", filters.filterIsInstance<SortFilter>().firstOrNull()?.selected ?: "created_at")
            filters.filterIsInstance<TypeFilter>().firstOrNull()?.selected?.let { put("filter_type_id", it) }
            filters.filterIsInstance<YearFilter>().firstOrNull()?.selected?.let { put("filter_release_year", it) }
        }
        val results = rpc<List<AnimeDto>>("search_animes", body)
        val total = results.firstOrNull()?.totalCount ?: 0
        return AnimesPage(results.map { it.toSAnime() }, page.toLong() * PAGE_SIZE < total)
    }

    override fun getFilterList() = AnimeFilterList(TypeFilter(), SortFilter(), YearFilter())

    // ============================== Details =============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val id = resolveId(anime.url)
        val url = "$apiUrl/rest/v1/animes?id=eq.$id&select=$ANIME_COLUMNS"
        val dto = client.newCall(GET(url, apiHeaders)).awaitSuccess().parse<List<AnimeDto>>().firstOrNull()
            ?: throw Exception("找不到这部动画")
        return dto.toSAnime()
    }

    private fun AnimeDto.toSAnime() = SAnime.create().apply {
        url = "/anime/${this@toSAnime.id}"
        title = this@toSAnime.title
        thumbnail_url = coverUrl
        author = director?.takeIf { it.isNotBlank() }
        artist = actors?.filter { it.isNotBlank() }?.joinToString()?.takeIf { it.isNotBlank() }
        genre = metaTags?.joinToString()
        status = when (isFinished) {
            true -> SAnime.COMPLETED
            false -> SAnime.ONGOING
            null -> SAnime.UNKNOWN
        }
        val names = aliases.orEmpty().map { Parser.unescapeEntities(it, false) }
        description = listOfNotNull(
            this@toSAnime.description?.takeIf { it.isNotBlank() },
            titleOriginal?.takeIf { it.isNotBlank() }?.let { "原名：$it" },
            names.takeIf { it.isNotEmpty() }?.let { "别名：${it.joinToString(" / ")}" },
        ).joinToString("\n\n").ifEmpty { null }
    }

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    // ============================== Episodes ============================

    /**
     * Episodes come from the anime page's own data: each playback line lists the episodes it has.
     * (The `episodes` table also lists episodes that haven't aired yet.)
     */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val id = resolveId(anime.url)
        val body = client.newCall(GET("$baseUrl/anime/$id", rscHeaders)).awaitSuccess().body.string()
        val sources = findSources(body) ?: throw Exception("无法读取剧集列表")
        val sourceNames = HashMap<Long, MutableList<String>>()
        val episodes = LinkedHashMap<Long, EpisodeDto>()
        for (source in sources) {
            for (episode in source.episodes) {
                episodes.putIfAbsent(episode.id, episode)
                sourceNames.getOrPut(episode.id) { mutableListOf() }.add(source.name)
            }
        }
        return episodes.values
            .sortedWith(compareBy({ it.kind != "main" }, { it.episodeNumber ?: Double.MAX_VALUE }))
            .map { episode ->
                SEpisode.create().apply {
                    url = "/anime/$id/play/${episode.id}"
                    name = episode.displayName()
                    if (episode.kind == "main") episode.episodeNumber?.let { episode_number = it.toFloat() }
                    date_upload = episode.availableAt?.let(::parseDate) ?: 0L
                    scanlator = sourceNames[episode.id]?.joinToString()
                }
            }
            .reversed()
    }

    private fun EpisodeDto.displayName(): String {
        val number = episodeNumber?.let { if (it % 1.0 == 0.0) "%02d".format(it.toLong()) else it.toString() }
        val label = when {
            kind == "main" -> number?.let { "第${it}集" } ?: "正片"
            else -> listOfNotNull(KIND_LABELS[kind] ?: kind.uppercase(), number).joinToString(" ")
        }
        return listOfNotNull(label, title?.takeIf { it.isNotBlank() }).joinToString(" ")
    }

    private fun findSources(body: String): List<SourceDto>? {
        var index = body.indexOf(SOURCES_MARKER)
        while (index >= 0) {
            val array = runCatching { extractJson(body, index + SOURCES_MARKER.length - 1, '[', ']') }.getOrNull()
            val sources = array?.let { runCatching { json.decodeFromString<List<SourceDto>>(it) }.getOrNull() }
            if (!sources.isNullOrEmpty()) return sources
            index = body.indexOf(SOURCES_MARKER, index + 1)
        }
        return null
    }

    // =============================== Videos =============================

    /** One video per playback line that has this episode, the preferred line first. */
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val episodeId = EPISODE_ID_REGEX.find(episode.url)?.groupValues?.get(1)?.toLong()
            ?: throw Exception("请刷新剧集列表")
        val body = buildJsonObject {
            put("action", "fallback")
            put("episode_id", episodeId)
        }
        val request = POST("$apiUrl/functions/v1/issue-web-playback", apiHeaders, body.toRequestBody())
        val playback = client.newCall(request).execute().use { it.parse<PlaybackDto>() }
        if (!playback.ok) throw Exception("无法获取播放地址（${playback.error ?: "unknown"}）")

        val candidates = playback.candidates.ifEmpty {
            listOfNotNull(playback.url?.let { CandidateDto(playback.sourceName ?: "默认", it) })
        }
        val preferred = PREFERRED_SOURCE_SUFFIXES.getOrElse(selectedVideoSource) { PREFERRED_SOURCE_SUFFIXES[0] }
        return candidates
            .map { candidate ->
                // Some links contain raw Chinese characters; let OkHttp percent-encode them.
                val url = candidate.url.toHttpUrlOrNull()?.toString() ?: candidate.url
                Video(url, candidate.sourceName, url)
            }
            .sortedByDescending { it.quality.endsWith(preferred) }
    }

    // ============================== Settings ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_KEY_VIDEO_SOURCE
            title = "首选播放线路"
            entries = PREFERRED_SOURCE_SUFFIXES.toTypedArray()
            entryValues = PREFERRED_SOURCE_SUFFIXES.indices.map { "$it" }.toTypedArray()
            setDefaultValue(DEFAULT_VIDEO_SOURCE)
            summary = "当前选择：%s"
        }.also(screen::addPreference)
    }

    private val selectedVideoSource: Int
        get() = preferences.getString(PREF_KEY_VIDEO_SOURCE, DEFAULT_VIDEO_SOURCE)?.toIntOrNull() ?: 0

    // =============================== Utils ==============================

    // Anime saved by yuzono's build use the old site's URLs ("/bangumi/44.html"); the database keeps
    // the old ID as legacy_vod_id, so those entries keep working.
    private val legacyIds = ConcurrentHashMap<Long, Long>()

    private suspend fun resolveId(url: String): Long {
        ANIME_ID_REGEX.find(url)?.let { return it.groupValues[1].toLong() }
        val legacyId = LEGACY_ID_REGEX.find(url)?.groupValues?.get(1)?.toLong()
            ?: throw Exception("无法识别的链接，请重新搜索这部动画")
        legacyIds[legacyId]?.let { return it }
        val lookup = "$apiUrl/rest/v1/animes?legacy_vod_id=eq.$legacyId&select=id"
        val id = client.newCall(GET(lookup, apiHeaders)).awaitSuccess().parse<List<IdDto>>().firstOrNull()?.id
            ?: throw Exception("这部动画已不在网站上，请重新搜索")
        legacyIds[legacyId] = id
        return id
    }

    private suspend inline fun <reified T> rpc(function: String, body: JsonObject): T {
        val request = POST("$apiUrl/rest/v1/rpc/$function", apiHeaders, body.toRequestBody())
        return client.newCall(request).awaitSuccess().parse<T>()
    }

    private fun JsonObject.toRequestBody() = toString().toRequestBody(JSON_MEDIA_TYPE)

    private inline fun <reified T> Response.parse(): T = json.decodeFromString(body.string())

    /** Requesting a page with "RSC: 1" returns Next.js's data stream instead of HTML. */
    private val rscHeaders: Headers by lazy { headers.newBuilder().set("RSC", "1").build() }

    /** The JSON value that starts at [start] in [text], found by matching brackets outside strings. */
    private fun extractJson(text: String, start: Int, open: Char, close: Char): String {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    open -> depth++
                    close -> if (--depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        throw Exception("Unterminated JSON")
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    /** available_at is UTC, e.g. "2026-09-25T11:41:10.93172+00:00". */
    private fun parseDate(date: String): Long? = runCatching { dateFormat.parse(date.take(19))?.time }.getOrNull()

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
        /** The publishable key the website itself ships to every browser. */
        private const val API_KEY = "sb_publishable_OBIVAWACIX6lPXrO98_z24_HcsmalkA"

        private const val PAGE_SIZE = 30
        private const val SORT_BY_VIEWS = 1
        private const val ANIME_COLUMNS =
            "id,title,title_original,aliases,cover_url,description,director,actors,meta_tags,is_finished"

        // Same key and values as yuzono's build (0 = 主线-1, 1 = 主线-2, 2 = 备用-1), so the choice carries over.
        private const val PREF_KEY_VIDEO_SOURCE = "PREF_KEY_VIDEO_SOURCE"
        private const val DEFAULT_VIDEO_SOURCE = "0"
        private val PREFERRED_SOURCE_SUFFIXES = listOf("主线-1", "主线-2", "备用-1")

        private val KIND_LABELS = mapOf("sp" to "SP", "ova" to "OVA", "oad" to "OAD", "pv" to "PV", "op" to "OP", "ed" to "ED")

        private const val SOURCES_MARKER = "\"sources\":["
        private const val ITEM_LIST_MARKER = "{\"@context\":\"https://schema.org\",\"@type\":\"ItemList\""
        private val ANIME_ID_REGEX = Regex("""/anime/(\d+)""")
        private val EPISODE_ID_REGEX = Regex("""/play/(\d+)""")
        private val LEGACY_ID_REGEX = Regex("""/bangumi/(\d+)\.html""")
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
