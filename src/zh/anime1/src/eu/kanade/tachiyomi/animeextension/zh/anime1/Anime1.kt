package eu.kanade.tachiyomi.animeextension.zh.anime1

import android.app.Application
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import eu.kanade.tachiyomi.animeextension.zh.anime1.BangumiScraper.applyTo
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
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class Anime1 : AnimeHttpSource(), ConfigurableAnimeSource {
    // Keep name/lang identical to the original extension so the source ID
    // (and therefore existing library entries) stays the same.
    override val baseUrl = "https://anime1.me"
    override val lang = "zh-hant"
    override val name = "Anime1.me"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder().add("referer", "$baseUrl/")

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::coverInterceptor)
        .build()

    private val videoApiUrl = "https://v.anime1.me/api"

    // The anime list used to be served from a CloudFront host (d1zquzjgwo9yb.cloudfront.net)
    // which no longer resolves. The site now serves the same data from its own domain.
    private val animeListUrl = "$baseUrl/animelist.json"

    private val uploadDateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
    }
    private var entries: List<ListEntry>? = null
    private val cookieManager
        get() = CookieManager.getInstance()
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Anime list ==============================

    /** One row of animelist.json: [id, title, status, year, season, fansub]. */
    private class ListEntry(
        val url: String,
        val title: String,
        val ongoing: Boolean,
        val year: String?,
        val season: String?,
        val fansub: String?,
    ) {
        /** See [airQuarter]. Some rows span two seasons ("2021夏/2022冬"); the first one counts. */
        val airQuarter: Int? by lazy {
            val y = year?.let { YEAR_REGEX.find(it) }?.value?.toInt() ?: return@lazy null
            val index = season?.firstNotNullOfOrNull { SEASONS.indexOf(it).takeIf { i -> i >= 0 } }
                ?: return@lazy null
            y * 4 + index
        }

        val searchKey: String by lazy { title.toSearchKey() }

        fun toSAnime(coverUrl: String) = SAnime.create().apply {
            url = this@ListEntry.url
            title = this@ListEntry.title
            status = if (ongoing) SAnime.ONGOING else SAnime.COMPLETED
            genre = listOfNotNull(year, season, fansub).filter { it.isNotBlank() }.joinToString()
            thumbnail_url = coverUrl
        }
    }

    private suspend fun loadEntries(): List<ListEntry> {
        val body = client.newCall(GET("$animeListUrl?_=${System.currentTimeMillis()}", headers))
            .awaitSuccess().body.string()
        return json.decodeFromString<JsonArray>(body).map { row ->
            val array = row.jsonArray
            val id = array.getContent(0)!!
            var url = "?cat=$id"
            var title = array.getContent(1)!!
            // Entries from sister sites (e.g. anime1.pw) come as a link with an absolute URL.
            if (id == "0" || title.contains("</a>")) {
                val doc = Jsoup.parse(title)
                doc.selectFirst("a")?.let { link -> url = link.attr("href") }
                title = doc.text()
            }
            ListEntry(
                url = url,
                title = title,
                ongoing = array.getContent(2)?.contains("連載中") == true,
                year = array.getContent(3),
                season = array.getContent(4),
                fansub = array.getContent(5),
            )
        }.also { entries = it }
    }

    private suspend fun findEntry(url: String): ListEntry? {
        val list = entries ?: loadEntries()
        return list.firstOrNull { it.url == url }
    }

    private fun List<ListEntry>.toPage(page: Int): AnimesPage {
        val start = ((page - 1) * PAGE_SIZE).coerceAtMost(size)
        val items = subList(start, (page * PAGE_SIZE).coerceAtMost(size))
        return AnimesPage(items.map { it.toSAnime(coverUrl(it.title, it.airQuarter)) }, start + items.size < size)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        // Refresh the list whenever the first page is requested.
        val list = entries.takeIf { page > 1 } ?: loadEntries()
        return list.toPage(page)
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return getLatestUpdates(page)
    }

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularAnimeParse(response: Response) = throw UnsupportedOperationException()
    override fun popularAnimeRequest(page: Int) = throw UnsupportedOperationException()

    // ================================ Search ================================

    /**
     * Searches the anime list first so results are whole anime (which can be
     * renamed and tracked), in Traditional or Simplified Chinese. Falls back to
     * the site's own search, whose results are individual episodes.
     */
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val key = query.toSearchKey()
        if (key.isNotEmpty()) {
            val list = (if (page == 1) null else entries) ?: loadEntries()
            val matches = list.filter { key in it.searchKey }
            if (matches.isNotEmpty()) return matches.toPage(page)
        }
        return super.getSearchAnime(page, query, filters)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        // The site's search results are episodes.
        val document = response.asJsoup()
        val items = document.select("article.post .entry-title a").map {
            SAnime.create().apply {
                setUrlWithoutDomain(it.attr("href"))
                title = it.ownText()
                thumbnail_url = coverUrl(title, null)
            }
        }
        val previous = document.select(".nav-previous")
        return AnimesPage(items, previous.isNotEmpty())
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (page > 1) {
            url.addPathSegments("page/$page")
        }
        url.addQueryParameter("s", query)
        return GET(url.build(), headers)
    }

    // ================================ Details ===============================

    override fun animeDetailsParse(response: Response) = throw UnsupportedOperationException()

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        // Look things up by anime1's own title, not the stored one: that may have been renamed.
        val entry = runCatching { findEntry(anime.url) }.getOrNull()
        val chineseTitle = entry?.title ?: anime.title
        val quarter = entry?.airQuarter
        anime.thumbnail_url = coverUrl(chineseTitle, quarter)

        // Only anime from the list can be renamed; others have no stable original title to come back to.
        val titleLanguage = if (entry != null) titleLanguage else TitleLanguage.CHINESE
        if (!bangumiEnable && titleLanguage == TitleLanguage.CHINESE) {
            if (entry != null) anime.title = chineseTitle
            return anime
        }

        val subject = runCatching {
            withContext(Dispatchers.IO) {
                BangumiScraper.search(network.client, chineseTitle.toBangumiKeyword(), quarter)
            }
        }.getOrNull()
        val mal = if (titleLanguage != TitleLanguage.CHINESE && subject != null) {
            runCatching { withContext(Dispatchers.IO) { AniListMapper.find(network.client, subject) } }.getOrNull()
        } else {
            null
        }

        if (entry != null) {
            anime.title = when (titleLanguage) {
                TitleLanguage.CHINESE, TitleLanguage.CHINESE_WITH_ID -> null
                TitleLanguage.ROMAJI -> mal?.romaji ?: mal?.english
                TitleLanguage.ENGLISH -> mal?.english ?: mal?.romaji
            } ?: chineseTitle
        }
        if (bangumiEnable && subject != null) subject.applyTo(anime, bangumiFetchType)
        anime.description = listOfNotNull(
            mal?.let { malLine(it, chineseTitle) },
            anime.description?.withoutMalLine()?.takeIf { it.isNotBlank() },
        ).joinToString("\n\n").ifEmpty { null }
        return anime
    }

    /**
     * Shown at the top of the description. The ID gets a line of its own so a long press selects
     * just "id:123", which pasted into the MAL tracker search gives an exact match.
     */
    private fun malLine(mal: MalTitle, chineseTitle: String): String {
        val name = mal.romaji ?: mal.english ?: chineseTitle
        return MAL_LINE_PREFIX + name + (mal.malId?.let { "\nid:$it" } ?: "")
    }

    /** The MAL block is separated from the rest by a blank line, in this and the older one-line format. */
    private fun String.withoutMalLine(): String =
        if (startsWith(MAL_LINE_PREFIX)) substringAfter("\n\n", "").trimStart() else this

    // =============================== Episodes ===============================

    // Entries from sister sites (e.g. anime1.pw) are stored with an absolute URL.
    override fun episodeListRequest(anime: SAnime): Request = GET(anime.url.toAbsoluteUrl(), headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        var document: Document? = response.asJsoup()
        val episodes = mutableListOf<SEpisode>()
        val requestUrl = response.request.url.toString()
        while (document != null) {
            val items = document.select("article.post").map {
                SEpisode.create().apply {
                    name = it.select(".entry-title").text()
                    val url = it.selectFirst(".entry-title a")?.attr("href") ?: requestUrl
                    setUrlWithoutDomain(url)
                    date_upload = it.select("time.updated").attr("datetime").toTimestamp()
                }
            }
            episodes.addAll(items)
            val previousUrl = document.select(".nav-previous a").attr("href")
            document = if (previousUrl.isBlank()) {
                null
            } else {
                client.newCall(GET(previousUrl, headers)).execute().asJsoup()
            }
        }
        return episodes
    }

    // ================================ Videos ================================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val req = document.selectFirst("video[data-apireq]")?.attr("data-apireq")
            ?: throw Exception("找不到影片")
        val apiResponse = client.newCall(
            POST(videoApiUrl, headers, FormBody.Builder().addEncoded("d", req).build()),
        ).execute()
        // The API answers with signed cookies (h, p, e) that the video host requires.
        val apiCookies = apiResponse.headers("set-cookie")
            .map { it.substringBefore(";").trim() }
            .filter { it.contains("=") }
        val videoResponse = json.decodeFromString<VideoResponse>(apiResponse.body.string())
        return videoResponse.s.map {
            val videoUrl = if (it.src.startsWith("//")) "https:${it.src}" else it.src
            val cookie = apiCookies.takeIf { c -> c.isNotEmpty() }?.joinToString("; ")
                ?: runCatching { cookieManager.getCookie(videoUrl) }.getOrNull()
            val videoHeaders = cookie?.let { c -> headers.newBuilder().set("cookie", c).build() } ?: headers
            Video(videoUrl, it.type, videoUrl, headers = videoHeaders)
        }
    }

    override fun getAnimeUrl(anime: SAnime): String = anime.url.toAbsoluteUrl()

    // ============================== Settings ================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val bangumiCover = CheckBoxPreference(screen.context).apply {
            key = PREF_KEY_BANGUMI_COVER
            title = "使用Bangumi封面"
            summary = "從Bangumi搜尋每部動畫的封面，取代預設圖片"
            setDefaultValue(true)
        }
        val titleLanguagePref = ListPreference(screen.context).apply {
            key = PREF_KEY_TITLE_LANGUAGE
            title = "標題語言（方便MAL追蹤）"
            entries = TitleLanguage.entries.map { it.label }.toTypedArray()
            entryValues = TitleLanguage.entries.map { it.name }.toTypedArray()
            setDefaultValue(TitleLanguage.CHINESE.name)
            summary = titleLanguageSummary(titleLanguage)
            setOnPreferenceChangeListener { _, value ->
                summary = titleLanguageSummary(TitleLanguage.entries.first { it.name == value })
                true
            }
        }
        val bangumiScraper = CheckBoxPreference(screen.context).apply {
            key = PREF_KEY_BANGUMI
            title = "啟用Bangumi刮削"
        }
        val bangumiFetchType = ListPreference(screen.context).apply {
            key = PREF_KEY_BANGUMI_FETCH_TYPE
            title = "詳情拉取設置"
            setVisible(bangumiEnable)
            entries = arrayOf("拉取部分數據", "拉取完整數據")
            entryValues = arrayOf(BangumiFetchType.SHORT.name, BangumiFetchType.ALL.name)
            setDefaultValue(entryValues[0])
            summary = when (bangumiFetchType) {
                BangumiFetchType.SHORT -> entries[0]
                BangumiFetchType.ALL -> entries[1]
            }
            setOnPreferenceChangeListener { _, value ->
                summary = when (value) {
                    BangumiFetchType.ALL.name -> entries[1]
                    else -> entries[0]
                }
                true
            }
        }
        bangumiScraper.setOnPreferenceChangeListener { _, value ->
            bangumiFetchType.setVisible(value as Boolean)
            true
        }
        screen.apply {
            addPreference(bangumiCover)
            addPreference(titleLanguagePref)
            addPreference(bangumiScraper)
            addPreference(bangumiFetchType)
        }
    }

    private fun titleLanguageSummary(language: TitleLanguage): String {
        if (language == TitleLanguage.CHINESE) return language.label
        if (language == TitleLanguage.CHINESE_WITH_ID) {
            return "${language.label}\n標題保持中文，打開動畫頁面時在簡介最上面加上MAL標題和「id:12345」，" +
                "可複製到MAL追蹤搜尋。"
        }
        return "${language.label}\n打開動畫頁面時改名，讓MAL追蹤可直接搜尋到。" +
            "已收藏的動畫需在App設定開啟「Update library anime titles to match source」，再下拉刷新該動畫。"
    }

    private enum class TitleLanguage(val label: String) {
        CHINESE("中文（anime1原標題）"),
        CHINESE_WITH_ID("中文，簡介裡加上MAL ID"),
        ROMAJI("羅馬拼音（MAL標題）"),
        ENGLISH("英文（沒有英文名時用羅馬拼音）"),
    }

    private val titleLanguage: TitleLanguage
        get() = preferences.getString(PREF_KEY_TITLE_LANGUAGE, null)
            ?.let { saved -> TitleLanguage.entries.firstOrNull { it.name == saved } }
            ?: TitleLanguage.CHINESE
    private val bangumiCoverEnable: Boolean
        get() = preferences.getBoolean(PREF_KEY_BANGUMI_COVER, true)
    private val bangumiEnable: Boolean
        get() = preferences.getBoolean(PREF_KEY_BANGUMI, false)
    private val bangumiFetchType: BangumiFetchType
        get() = when (preferences.getString(PREF_KEY_BANGUMI_FETCH_TYPE, null)) {
            BangumiFetchType.ALL.name -> BangumiFetchType.ALL
            else -> BangumiFetchType.SHORT
        }

    // ================================ Covers ================================

    /**
     * anime1.me has no covers. Thumbnails point at a placeholder URL that
     * [coverInterceptor] resolves to the Bangumi cover only when the image is
     * actually loaded, so browsing doesn't fire a search per entry up front.
     * [quarter] (see [airQuarter]) picks the right season of a series.
     */
    private fun coverUrl(title: String, quarter: Int?): String {
        if (!bangumiCoverEnable) return FIX_COVER
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(COVER_PATH)
            .addQueryParameter("q", title)
            .apply { if (quarter != null) addQueryParameter("t", quarter.toString()) }
            .build()
            .toString()
    }

    // Cover URL -> image URL. Only definitive answers are cached; network errors are retried next time.
    private val coverCache = ConcurrentHashMap<String, String>()

    private fun coverInterceptor(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.host != COVER_HOST || url.pathSegments != listOf(COVER_PATH)) {
            return chain.proceed(chain.request())
        }
        val title = url.queryParameter("q").orEmpty()
        val quarter = url.queryParameter("t")?.toIntOrNull()
        val key = url.toString()
        val imageUrl = coverCache[key] ?: runCatching {
            BangumiScraper.search(network.client, title.toBangumiKeyword(), quarter)?.images?.medium ?: FIX_COVER
        }.onSuccess { coverCache[key] = it }.getOrDefault(FIX_COVER)
        // A fresh request: Bangumi's image host doesn't need anime1's referer or cookies.
        return chain.proceed(GET(imageUrl))
    }

    // ================================ Utils =================================

    private fun String.toBangumiKeyword(): String = ZhTwConverterUtil.toSimple(removeSuffixMark())

    private fun String.toAbsoluteUrl(): String = if (startsWith("http")) this else baseUrl + this

    private fun String.toTimestamp(): Long {
        // SimpleDateFormat's "Z" expects "+0800", but the site uses "+08:00".
        val normalized = replace(TIMEZONE_COLON_REGEX, "$1$2")
        return runCatching { uploadDateFormat.parse(normalized)?.time }.getOrNull() ?: 0L
    }

    private fun JsonArray.getContent(index: Int): String? {
        return getOrNull(index)?.jsonPrimitive?.contentOrNull
    }

    private fun String.removeSuffixMark(): String {
        return removeBracket("(", ")").removeBracket("[", "]").trim()
    }

    private fun String.removeBracket(start: String, end: String): String {
        val seasonStart = indexOf(start)
        val seasonEnd = indexOf(end)
        if (seasonStart >= 0 && seasonEnd > seasonStart) {
            return removeRange(seasonStart, seasonEnd + 1)
        }
        return this
    }

    companion object {
        const val PAGE_SIZE = 20
        const val FIX_COVER = "https://sta.anicdn.com/playerImg/8.jpg"

        const val PREF_KEY_BANGUMI = "PREF_KEY_BANGUMI"
        const val PREF_KEY_BANGUMI_COVER = "PREF_KEY_BANGUMI_COVER"
        const val PREF_KEY_BANGUMI_FETCH_TYPE = "PREF_KEY_BANGUMI_FETCH_TYPE"
        const val PREF_KEY_TITLE_LANGUAGE = "PREF_KEY_TITLE_LANGUAGE"

        private const val COVER_HOST = "anime1.me"
        private const val COVER_PATH = "__bangumi_cover__"
        private const val MAL_LINE_PREFIX = "MAL: "

        private const val SEASONS = "冬春夏秋"
        private val YEAR_REGEX = Regex("\\d{4}")
        private val TIMEZONE_COLON_REGEX = Regex("([+-]\\d{2}):(\\d{2})$")

        /** Case-, space- and script-insensitive form used for searching titles. */
        private fun String.toSearchKey(): String =
            ZhTwConverterUtil.toSimple(lowercase()).filterNot { it.isWhitespace() }
    }
}

@Serializable
data class VideoSource(val src: String, val type: String)

@Serializable
data class VideoResponse(val s: List<VideoSource>)
