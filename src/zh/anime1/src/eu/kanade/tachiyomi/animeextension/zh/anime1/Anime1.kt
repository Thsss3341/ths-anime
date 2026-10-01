package eu.kanade.tachiyomi.animeextension.zh.anime1

import android.app.Application
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
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
    private var data: JsonArray? = null
    private val cookieManager
        get() = CookieManager.getInstance()
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun animeDetailsParse(response: Response) = throw UnsupportedOperationException()

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        if (bangumiEnable) {
            val details = runCatching {
                BangumiScraper.fetchDetail(network.client, anime.title.toBangumiKeyword(), bangumiFetchType)
            }.getOrNull()
            if (details != null) {
                if (details.thumbnail_url.isNullOrBlank()) details.thumbnail_url = coverUrl(anime.title)
                return details
            }
        }
        // Also replaces the old placeholder cover on entries already in the library.
        anime.thumbnail_url = coverUrl(anime.title)
        return anime
    }

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

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        // Refresh the list whenever the first page is requested.
        val list = data.takeIf { page > 1 } ?: json.decodeFromString<JsonArray>(
            client.newCall(GET("$animeListUrl?_=${System.currentTimeMillis()}", headers))
                .awaitSuccess().body.string(),
        ).also { data = it }
        val start = ((page - 1) * PAGE_SIZE).coerceAtMost(list.size)
        val items = list.subList(start, (page * PAGE_SIZE).coerceAtMost(list.size))
        return AnimesPage(
            items.map {
                SAnime.create().apply {
                    val array = it.jsonArray
                    val id = array.getContent(0)!!
                    url = "?cat=$id"
                    title = array.getContent(1)!!
                    if (id == "0" || title.contains("</a>")) {
                        val doc = Jsoup.parse(title)
                        doc.selectFirst("a")?.let { link ->
                            url = link.attr("href")
                        }
                        title = doc.text()
                    }
                    status = if (array.getContent(2)?.contains("連載中") == true) {
                        SAnime.ONGOING
                    } else {
                        SAnime.COMPLETED
                    }
                    genre = listOfNotNull(
                        array.getContent(3),
                        array.getContent(4),
                        array.getContent(5),
                    ).filter { g -> g.isNotBlank() }.joinToString()
                    thumbnail_url = coverUrl(title)
                }
            },
            start + items.size < list.size,
        )
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return getLatestUpdates(page)
    }

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularAnimeParse(response: Response) = throw UnsupportedOperationException()
    override fun popularAnimeRequest(page: Int) = throw UnsupportedOperationException()

    override fun searchAnimeParse(response: Response): AnimesPage {
        // The search result is episode
        val document = response.asJsoup()
        val items = document.select("article.post .entry-title a").map {
            SAnime.create().apply {
                setUrlWithoutDomain(it.attr("href"))
                title = it.ownText()
                thumbnail_url = coverUrl(title)
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

    override fun getAnimeUrl(anime: SAnime): String = anime.url.toAbsoluteUrl()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val bangumiCover = CheckBoxPreference(screen.context).apply {
            key = PREF_KEY_BANGUMI_COVER
            title = "使用Bangumi封面"
            summary = "從Bangumi搜尋每部動畫的封面，取代預設圖片"
            setDefaultValue(true)
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
            addPreference(bangumiScraper)
            addPreference(bangumiFetchType)
        }
    }

    private val bangumiCoverEnable: Boolean
        get() = preferences.getBoolean(PREF_KEY_BANGUMI_COVER, true)
    private val bangumiEnable: Boolean
        get() = preferences.getBoolean(PREF_KEY_BANGUMI, false)
    private val bangumiFetchType: BangumiFetchType
        get() = when (preferences.getString(PREF_KEY_BANGUMI_FETCH_TYPE, null)) {
            BangumiFetchType.ALL.name -> BangumiFetchType.ALL
            else -> BangumiFetchType.SHORT
        }

    /**
     * anime1.me has no covers. Thumbnails point at a placeholder URL that
     * [coverInterceptor] resolves to the Bangumi cover only when the image is
     * actually loaded, so browsing doesn't fire a search per entry up front.
     */
    private fun coverUrl(title: String): String {
        if (!bangumiCoverEnable) return FIX_COVER
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(COVER_PATH)
            .addQueryParameter("q", title)
            .build()
            .toString()
    }

    // Title -> image URL. Only definitive answers are cached; network errors are retried next time.
    private val coverCache = ConcurrentHashMap<String, String>()

    private fun coverInterceptor(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.host != COVER_HOST || url.pathSegments != listOf(COVER_PATH)) {
            return chain.proceed(chain.request())
        }
        val title = url.queryParameter("q").orEmpty()
        val imageUrl = coverCache[title] ?: runCatching {
            BangumiScraper.search(network.client, title.toBangumiKeyword())?.images?.medium ?: FIX_COVER
        }.onSuccess { coverCache[title] = it }.getOrDefault(FIX_COVER)
        // A fresh request: Bangumi's image host doesn't need anime1's referer or cookies.
        return chain.proceed(GET(imageUrl))
    }

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

        private const val COVER_HOST = "anime1.me"
        private const val COVER_PATH = "__bangumi_cover__"

        private val TIMEZONE_COLON_REGEX = Regex("([+-]\\d{2}):(\\d{2})$")
    }
}

@Serializable
data class VideoSource(val src: String, val type: String)

@Serializable
data class VideoResponse(val s: List<VideoSource>)
