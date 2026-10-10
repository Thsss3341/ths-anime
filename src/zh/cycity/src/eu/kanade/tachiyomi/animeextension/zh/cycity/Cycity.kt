package eu.kanade.tachiyomi.animeextension.zh.cycity

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 次元城动漫. Based on yuzono's Cycity extension.
 *
 * The site now turns away Android devices on its website ("请使用 Android APP", HTTP 405) to push
 * its own app, and the app's default User-Agent is an Android one. Every request here, including
 * the video parser's and WebView's, goes out as desktop Chrome instead.
 */
class Cycity : AnimeHttpSource(), ConfigurableAnimeSource {
    // Keep name/lang identical to yuzono's Cycity so the source ID (and library entries) stay the same.
    override val baseUrl = "https://www.cycani.org"
    override val name = "次元城动漫"
    override val lang = "zh"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/index.php/ds_api"

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", DESKTOP_USER_AGENT)
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Accept-Language", "zh-CN,zh;q=0.9")
        .set("Referer", "$baseUrl/")

    // Also covers requests built without the source's headers, which would otherwise get the
    // app's default (Android) User-Agent.
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().header("User-Agent", DESKTOP_USER_AGENT).build()
            chain.proceed(request)
        }
        .addInterceptor(::explainRefusal)
        .build()

    /** Turns the site's refusals into messages that say which request failed and why. */
    private fun explainRefusal(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 405) return response
        val body = response.peekBody(64 * 1024).string()
        response.close()
        val what = "${request.method} ${request.url.encodedPath}"
        throw IOException(
            if ("Android" in body) {
                "网站把请求当成 Android 设备而拒绝（HTTP 405，$what）"
            } else {
                "网站拒绝了这个请求（HTTP 405，$what）"
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = POPULAR_PREF
            title = "热门动画显示番剧周表"
            summary = "开启后，“热门”内容会显示当天应该更新的动画，但不一定更新"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    /**
     * JavaScript implementation:
     *
     * function decrypt(src, key1, key2) {
     *     let prefix = new Array(key2.length);
     *     for (let i = 0; i < key2.length; i++) {
     *       prefix[key1[i]] = key2[i];
     *     }
     *     let a = CryptoJS.MD5(prefix.join("") + "YLwJVbXw77pk2eOrAnFdBo2c3mWkLtodMni2wk81GCnP94ZltW").toString(),
     *       key = CryptoJS.enc.Utf8.parse(a.substring(16)),
     *       iv = CryptoJS.enc.Utf8.parse(a.substring(0, 16)),
     *       dec = CryptoJS.AES.decrypt(src, key, {
     *         iv: iv,
     *         mode: CryptoJS.mode.CBC,
     *         padding: CryptoJS.pad.Pkcs7,
     *       });
     *     return dec.toString(CryptoJS.enc.Utf8);
     * }
     */
    private fun decrypt(url: String, k1: String, k2: String): String {
        val prefix = CharArray(k2.length)
        k1.indices.forEach { prefix[k1[it] - '0'] = k2[it] }
        val txt = "${prefix.joinToString("")}YLwJVbXw77pk2eOrAnFdBo2c3mWkLtodMni2wk81GCnP94ZltW"
        val a = MessageDigest.getInstance("MD5").digest(txt.toByteArray()).joinToString("") { "%02x".format(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(a.substring(16).toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(a.take(16).toByteArray(Charsets.UTF_8)),
        )
        return cipher.doFinal(Base64.decode(url, Base64.DEFAULT)).toString(Charsets.UTF_8)
    }

    // https://www.cycani.org/show/20/by/time/class/%E6%BC%AB%E7%94%BB%E6%94%B9/page/2/year/2024.html
    private fun vodListRequest(by: String, page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("show/20/by/$by")
            .addPathSegments("page/$page.html")
        return GET(url.build(), headers)
    }

    private fun vodListParse(response: Response) = response.asJsoup().let { doc ->
        val list = doc.select(".public-list-box").mapNotNull {
            val link = it.selectFirst(".public-list-button a") ?: return@mapNotNull null
            SAnime.create().apply {
                thumbnail_url = it.selectFirst("img")?.attr("data-src")
                title = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
            }
        }
        AnimesPage(list, doc.hasNextPage())
    }

    private fun org.jsoup.nodes.Document.hasNextPage(): Boolean {
        val pages = selectFirst(".page-tip")?.text()?.substringAfter("当前")?.substringBefore("页")?.split("/")
        return pages != null && pages.getOrNull(0) != pages.getOrNull(1)
    }

    private fun weeklyScheduleRequest(): Request {
        val weekday = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "一"
            Calendar.TUESDAY -> "二"
            Calendar.WEDNESDAY -> "三"
            Calendar.THURSDAY -> "四"
            Calendar.FRIDAY -> "五"
            Calendar.SATURDAY -> "六"
            Calendar.SUNDAY -> "日"
            else -> ""
        }
        val url = "$apiUrl/weekday".toHttpUrl().newBuilder().addQueryParameter("weekday", weekday)
        return POST(url.build().toString(), headers)
    }

    private fun weeklyScheduleParse(response: Response): AnimesPage {
        val data = json.decodeFromString<VodResponse>(response.body.string())
        return AnimesPage(data.list.map(VodInfo::toSAnime), false)
    }

    // Latest Updates ==============================================================================

    override fun latestUpdatesRequest(page: Int) = vodListRequest("time", page)

    override fun latestUpdatesParse(response: Response) = vodListParse(response)

    // Popular Anime ===============================================================================

    override fun popularAnimeRequest(page: Int): Request {
        val switch = preferences.getBoolean(POPULAR_PREF, false)
        return if (switch) weeklyScheduleRequest() else vodListRequest("hits", page)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        if (response.header("Content-Type")?.startsWith("text/html") == true) {
            return vodListParse(response)
        }
        return weeklyScheduleParse(response)
    }

    // Search Anime ================================================================================

    override fun getFilterList() = AnimeFilterList(
        AnimeFilter.Header("筛选条件（关键字搜索时无效）"),
        TypeFilter(),
        ClassFilter(),
        YearFilter(),
    )

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val activeFilters = filters.ifEmpty { getFilterList() }
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegments("search/wd").addPathSegment(query)
        } else {
            activeFilters.filterIsInstance<TypeFilter>().firstOrNull()?.let {
                url.addPathSegments("show/$it")
            }
            activeFilters.filterIsInstance<ClassFilter>().firstOrNull()?.let {
                val classFilter = it.toString()
                if (classFilter != "全部") url.addPathSegment("class").addPathSegment(classFilter)
            }
            activeFilters.filterIsInstance<YearFilter>().firstOrNull()?.let {
                val year = it.toString()
                if (year != "全部") url.addPathSegments("year/$year")
            }
        }
        url.addPathSegments("page/$page.html")
        return GET(url.build(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        if (response.request.url.pathSegments.contains("search")) {
            val document = response.asJsoup()
            document.selectFirst(".ft6")?.let { throw Exception("请在 WebView 中输入验证码") }
            val animeList = document.select(".search-list").map { item ->
                SAnime.create().apply {
                    setUrlWithoutDomain(item.select(".detail-info > a").attr("href"))
                    item.selectFirst(".detail-pic img[data-src]")?.let {
                        title = it.attr("alt")
                        thumbnail_url = it.attr("data-src")
                    }
                }
            }
            return AnimesPage(animeList, document.hasNextPage())
        }
        return vodListParse(response)
    }

    // Anime Details ===============================================================================

    override fun animeDetailsParse(response: Response) = response.asJsoup().let { doc ->
        val infos = doc.select(".slide-info")
        val remark = doc.select(".slide-info-remarks").first()?.text()
        SAnime.create().apply {
            title = doc.selectFirst(".slide-info-title")?.text() ?: throw Exception("无法读取动画信息")
            author = infos.getOrNull(1)?.selectFirst("a")?.text()
            genre = infos.getOrNull(3)?.select("a")?.joinToString { it.text() }
            description = doc.selectFirst("#height_limit.text")?.text()
            status = when {
                remark?.contains("|") == true -> SAnime.ONGOING
                remark == "已完结" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
        }
    }

    override fun episodeListRequest(anime: SAnime) = animeDetailsRequest(anime)

    override fun episodeListParse(response: Response) = response.asJsoup().let { doc ->
        val hosts = doc.select(".anthology-tab a").map {
            it.text().substringBefore(it.selectFirst("span")?.text() ?: "").trim()
        }
        doc.select(".anthology-list-play").mapIndexed { i, e ->
            e.select("a").map {
                SEpisode.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    name = it.text()
                    scanlator = hosts.getOrNull(i)
                }
            }
        }.flatten().reversed()
    }

    // Video List ==================================================================================

    override fun videoListRequest(episode: SEpisode) = GET(baseUrl + episode.url, headers)

    override fun videoListParse(response: Response): List<Video> {
        val playerHtml = response.asJsoup().select(".player-left").html()
        val origin = VIDEO_URL_REGEX.find(playerHtml)?.groupValues?.get(1) ?: throw Exception("找不到视频")
        val base64 = Base64.decode(origin, Base64.DEFAULT).toString(Charsets.UTF_8)
        // The page only has an ID for the site's player; the real URL is resolved in videoUrlParse.
        return listOf(Video(URLDecoder.decode(base64, "UTF-8"), "默认", null))
    }

    override fun videoUrlRequest(video: Video) = GET(PARSE_URL + video.url, headers)

    override fun videoUrlParse(response: Response): String {
        val body = response.body.string()
        val matches = KEY_REGEX.findAll(body).toList()
        check(matches.size == 2) { "视频URL解析失败！" }
        val url = URL_REGEX.find(body)?.groupValues?.get(1) ?: throw Exception("视频URL解析失败！")
        return decrypt(url, matches[0].groupValues[1], matches[1].groupValues[1])
    }

    companion object {
        val VIDEO_URL_REGEX = Regex("\\bplayer_aaaa[^<>]*\"url\": ?\"(.*?)\"[^<>]*\\}")
        val KEY_REGEX = Regex("now_(\\w+)")
        val URL_REGEX = Regex("\"url\": \"([^:]+?)\"")
        const val PARSE_URL = "https://player.cycanime.com/?url="
        const val POPULAR_PREF = "POPULAR_DISPLAY"

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36"
    }
}
