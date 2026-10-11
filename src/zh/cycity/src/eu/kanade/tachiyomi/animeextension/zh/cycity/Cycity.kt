package eu.kanade.tachiyomi.animeextension.zh.cycity

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * 次元城动漫. The site was rebuilt in 2026 as a JavaScript app: every page, old or new, is now the
 * same empty shell that loads its content from a JSON API, so the HTML the yuzono extension parsed
 * no longer exists. This source talks to that API directly (the one the official app uses).
 *
 * Browsing, search, details and episode lists work without an account. Play URLs require a logged-in
 * 次元城 account, the same as in the official app, so playback uses the credentials from the settings.
 */
class Cycity : AnimeHttpSource(), ConfigurableAnimeSource {
    // Keep name/lang identical to yuzono's Cycity so the source ID (and library entries) stay the same.
    override val baseUrl = "https://www.cycani.org"
    override val name = "次元城动漫"
    override val lang = "zh"
    override val supportsLatest = true

    private val apiUrl = "https://mapi.cycback.org"

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
        .set("Accept", "application/json")
        .set("X-App-Name", "cyc_web")
        .set("X-App-Version", "cycweb")
        .set("X-Time-Zone", "Asia/Shanghai")

    private val videoHeaders: Headers by lazy {
        Headers.Builder()
            .set("User-Agent", DESKTOP_USER_AGENT)
            .set("Referer", "$baseUrl/")
            .build()
    }

    // ============================== Browse ==============================

    override suspend fun getPopularAnime(page: Int): AnimesPage =
        if (preferences.getBoolean(POPULAR_PREF, false)) {
            weekdaySchedule()
        } else {
            listVideos(page, AnimeFilterList(SortFilter(state = SORT_BY_HITS)))
        }

    override suspend fun getLatestUpdates(page: Int): AnimesPage =
        listVideos(page, AnimeFilterList(SortFilter(state = SORT_BY_UPDATE)))

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.isBlank()) return listVideos(page, filters)
        return api<ListDto<VideoCardDto>>(GET(searchUrl(query.trim(), page, PAGE_SIZE), headers)).toAnimesPage()
    }

    override fun getFilterList() = AnimeFilterList(ZoneFilter(), SortFilter(), TagFilter(), YearFilter())

    private suspend fun listVideos(page: Int, filters: AnimeFilterList): AnimesPage {
        val url = "$apiUrl/videos".toHttpUrl().newBuilder()
            .addQueryParameter("zone_id", filters.selected<ZoneFilter>() ?: DEFAULT_ZONE)
            .addQueryParameter("page", "$page")
            .addQueryParameter("page_size", "$PAGE_SIZE")
            .addQueryParameter("order_by", filters.selected<SortFilter>() ?: "update_time")
        filters.selected<TagFilter>()?.let { url.addQueryParameter("tag", it) }
        filters.selected<YearFilter>()?.let { url.addQueryParameter("year", it) }
        return api<ListDto<VideoCardDto>>(GET(url.build(), headers)).toAnimesPage()
    }

    private inline fun <reified T : QueryFilter> AnimeFilterList.selected() =
        filterIsInstance<T>().firstOrNull()?.selected

    private fun searchUrl(query: String, page: Int, pageSize: Int): HttpUrl =
        "$apiUrl/videos/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", "$page")
            .addQueryParameter("page_size", "$pageSize")
            .build()

    /** Today's entries of the airing schedule (周一 = 1 … 周日 = 7). */
    private suspend fun weekdaySchedule(): AnimesPage {
        val today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val url = "$apiUrl/index/weekday?weekday=$today"
        val days = api<ListDto<WeekdayDto>>(GET(url, headers)).list
        val videos = days.firstOrNull { it.weekday == today }?.videos.orEmpty()
        return AnimesPage(videos.map { it.toSAnime() }, false)
    }

    private fun ListDto<VideoCardDto>.toAnimesPage() =
        AnimesPage(list.map { it.toSAnime() }, pager?.hasNextPage == true)

    private fun VideoCardDto.toSAnime() = SAnime.create().apply {
        url = "/anime/${this@toSAnime.id}"
        title = this@toSAnime.title
        thumbnail_url = coverUrl
    }

    // ============================== Details =============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = videoDetail(anime).toSAnime()

    private suspend fun videoDetail(anime: SAnime): VideoDetailDto =
        api(GET("$apiUrl/videos/${resolveId(anime)}", headers))

    private fun VideoDetailDto.toSAnime() = SAnime.create().apply {
        url = "/anime/${this@toSAnime.id}"
        title = this@toSAnime.title
        thumbnail_url = coverUrl
        author = director.joinToString().ifEmpty { null }
        artist = writer?.ifBlank { null }
        genre = tags.joinToString().ifEmpty { null }
        status = if (completed) SAnime.COMPLETED else SAnime.ONGOING
        description = buildString {
            append(this@toSAnime.description.orEmpty().replace("\r\n", "\n").trim())
            val otherNames = listOfNotNull(subtitle, englishTitle).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (otherNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("别名：").append(otherNames.joinToString(" / "))
            }
        }.ifEmpty { null }
    }

    /**
     * Anime saved before the site's rebuild have URLs like "/bangumi/6017.html", whose IDs the new
     * site doesn't use. Find them again by title.
     */
    private suspend fun resolveId(anime: SAnime): Long {
        ANIME_PATH_REGEX.find(anime.url)?.let { return it.groupValues[1].toLong() }
        legacyIds[anime.url]?.let { return it }
        val results = api<ListDto<VideoCardDto>>(GET(searchUrl(anime.title, 1, 20), headers)).list
        val wanted = anime.title.normalized()
        val match = results.firstOrNull { it.title == anime.title }
            ?: results.firstOrNull { it.title.normalized() == wanted }
            ?: throw Exception("新版次元城找不到「${anime.title}」，请重新搜索并加入书架")
        legacyIds[anime.url] = match.id
        return match.id
    }

    private val legacyIds = ConcurrentHashMap<String, Long>()

    private fun String.normalized() = replace(NON_WORD_REGEX, "").lowercase()

    // ============================= Episodes =============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val detail = videoDetail(anime)
        val sources = detail.playFrom
        return sources.flatMap { source ->
            sections(detail.id, source.code).mapIndexed { index, section ->
                SEpisode.create().apply {
                    url = "/anime/${detail.id}/play/${index + 1}?section=${section.id}"
                    name = section.title
                    episode_number = EPISODE_NUMBER_REGEX.find(section.title)?.value?.toFloatOrNull()
                        ?: (index + 1).toFloat()
                    if (sources.size > 1) scanlator = source.title?.ifBlank { null } ?: source.code
                }
            }
        }.reversed()
    }

    private suspend fun sections(videoId: Long, playerCode: String): List<SectionDto> {
        val all = mutableListOf<SectionDto>()
        var page = 1
        while (true) {
            val url = "$apiUrl/videos/$videoId/sections".toHttpUrl().newBuilder()
                .addQueryParameter("player_code", playerCode)
                .addQueryParameter("page", "$page")
                .addQueryParameter("page_size", "$MAX_PAGE_SIZE")
                .build()
            val result = api<ListDto<SectionDto>>(GET(url, headers))
            all += result.list
            if (result.list.isEmpty() || result.pager?.hasNextPage != true) return all
            page++
        }
    }

    // =============================== Video ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val sectionId = (baseUrl + episode.url).toHttpUrl().queryParameter("section")
            ?: throw Exception("这一集来自旧版网站，请在动画页面下拉刷新集数列表")
        val play = withLogin(sectionId) { session ->
            val url = "${session.backend.apiUrl}/v2/sections/$sectionId/play-url"
            api<PlayUrlDto>(GET(url, session.backend.headers(session.token)))
        }
        preferences.edit().remove(PREF_LAST_ERROR).apply()
        val url = play.url?.takeIf { it.isNotBlank() } ?: throw Exception("找不到视频")
        return listOf(Video(url, play.name?.ifBlank { null } ?: "默认", url, videoHeaders))
    }

    /**
     * Where to log in and fetch play URLs. Logging in exactly like the website does comes first;
     * the official app's address is the fallback (for example when the website blocks a network).
     */
    private enum class Backend(val apiUrl: String, val label: String) {
        WEBSITE("https://www.cycani.org/api", "网站"),
        APP("https://mapi.cycback.org", "App"),
    }

    private fun Backend.headers(token: String? = null): Headers = headers.newBuilder().apply {
        when (this@headers) {
            Backend.WEBSITE -> {
                set("X-App-Name", "cyc_web")
                set("X-App-Version", "cycweb")
                set("Origin", baseUrl)
                set("Referer", "$baseUrl/")
            }
            Backend.APP -> {
                set("X-App-Name", "cyc_android")
                removeAll("X-App-Version")
            }
        }
        if (token != null) set("Authorization", "Bearer $token")
    }.build()

    private class Session(val backend: Backend, val token: String) {
        override fun toString() = "${backend.name}:$token"

        companion object {
            fun parse(saved: String?): Session? {
                val backend = saved?.substringBefore(':')?.let { name -> Backend.entries.firstOrNull { it.name == name } }
                val token = saved?.substringAfter(':', "")?.let(::bareToken)
                return if (backend != null && token != null) Session(backend, token) else null
            }

            /** The website and the app both accept tokens with or without a "Bearer " prefix. */
            fun bareToken(token: String): String? = token.trim().replace(BEARER_PREFIX, "").trim().ifEmpty { null }

            private val BEARER_PREFIX = Regex("""^Bearer\s+""", RegexOption.IGNORE_CASE)
        }
    }

    /**
     * Runs [block] with a logged-in session: the saved one, else a fresh login with the account
     * from the settings, else the login saved by the website in WebView. Every token is tried on
     * both backends, and a new session is only kept once [block] accepts it. As a last resort, the
     * older play-URL endpoint is tried without logging in.
     *
     * When everything fails, the toast gets a short message and the full report is saved for the
     * "最近一次错误" setting, since toasts cut long messages off.
     */
    private suspend fun withLogin(sectionId: String, block: suspend (Session) -> PlayUrlDto): PlayUrlDto {
        Session.parse(preferences.getString(PREF_TOKEN, null))?.let { saved ->
            try {
                return block(saved)
            } catch (e: ApiException) {
                if (e.code == 429) throw e.forPlayback()
            }
        }
        preferences.edit().remove(PREF_TOKEN).apply()

        val report = mutableListOf<String>()
        val username = preferences.getString(PREF_USERNAME, null)?.trim().orEmpty()
        val password = preferences.getString(PREF_PASSWORD, null).orEmpty()
        val hasAccount = username.isNotEmpty() && password.isNotEmpty()
        var gotToken = false

        suspend fun attempt(label: String, preferred: Backend, getToken: suspend () -> String?): PlayUrlDto? {
            val raw = try {
                getToken() ?: run {
                    report += "$label：没有找到登录"
                    return null
                }
            } catch (e: Exception) {
                report += "$label：失败（${e.message}）"
                return null
            }
            val token = Session.bareToken(raw) ?: run {
                report += "$label：登录凭证为空"
                return null
            }
            gotToken = true
            report += "$label：成功，凭证${describeToken(raw)}"
            for (backend in listOf(preferred) + Backend.entries.filter { it != preferred }) {
                val session = Session(backend, token)
                try {
                    return block(session).also { preferences.edit().putString(PREF_TOKEN, session.toString()).apply() }
                } catch (e: ApiException) {
                    if (e.code == 429) throw e.forPlayback()
                    report += "  → ${backend.label}播放地址：被拒绝（${e.message}）"
                } catch (e: Exception) {
                    report += "  → ${backend.label}播放地址：失败（${e.message}）"
                }
            }
            return null
        }

        if (hasAccount) {
            for (backend in Backend.entries) {
                attempt("${backend.label}登录", backend) { login(backend, username, password) }?.let { return it }
            }
        } else {
            report += "账号密码：未填写"
        }
        attempt("WebView 登录", Backend.WEBSITE) { webViewToken() }?.let { return it }

        for (backend in Backend.entries) {
            try {
                return api(GET("${backend.apiUrl}/sections/$sectionId/play-url", backend.headers()))
            } catch (e: Exception) {
                report += "免登录${backend.label}播放地址：失败（${e.message}）"
            }
        }

        preferences.edit().putString(PREF_LAST_ERROR, report.joinToString("\n")).apply()
        throw Exception(
            when {
                gotToken -> "次元城登录成功，但播放地址被拒绝。完整原因见扩展设置“最近一次错误”"
                hasAccount -> "次元城登录失败。完整原因见扩展设置“最近一次错误”"
                else -> "次元城要登录才能播放：请在扩展设置填写账号密码，或在 WebView 中登录"
            },
        )
    }

    /** Enough about a token to debug with, without revealing it. */
    private fun describeToken(token: String): String {
        val trimmed = token.trim()
        val bare = Session.bareToken(trimmed).orEmpty()
        val kind = when {
            bare.count { it == '.' } == 2 -> "JWT"
            bare.all { it.isLetterOrDigit() } -> "字母数字"
            else -> "其他"
        }
        val prefix = if (bare.length != trimmed.length) "，带 Bearer 前缀" else ""
        return "（$kind，${bare.length} 字符$prefix）"
    }

    private suspend fun login(backend: Backend, username: String, password: String): String {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val result = api<LoginDto>(POST("${backend.apiUrl}/auth/login", backend.headers(), body))
        return result.token?.takeIf { it.isNotBlank() } ?: throw Exception("没有返回登录凭证")
    }

    private suspend fun webViewToken(): String? = withContext(Dispatchers.IO) {
        WebViewSession("$baseUrl/", DESKTOP_USER_AGENT).readToken()
    }

    private fun ApiException.forPlayback(): Exception = when {
        isAuthError -> Exception("次元城登录失效，请检查扩展设置里的账号和密码")
        code == 429 -> Exception("播放太频繁，次元城暂时限制了这个账号，请稍后再试")
        else -> this
    }

    // ================================ API ===============================

    private class ApiException(val code: Int, message: String) : Exception(message) {
        // 401: not logged in; 1001/1002: the token expired or was replaced by a newer login.
        val isAuthError get() = code == 401 || code == 1001 || code == 1002
    }

    private suspend inline fun <reified T> api(request: Request): T {
        val response = client.newCall(request).await()
        val body = response.body.string()
        val result = try {
            json.decodeFromString<ApiResponse<T>>(body)
        } catch (e: Exception) {
            if (response.code == 429) throw ApiException(429, "HTTP 429")
            throw Exception("次元城服务暂时不可用（HTTP ${response.code}${response.request.url.host.let { "，$it" }}）")
        }
        if (result.code != 0) {
            val code = if (result.code == -1) response.code else result.code
            throw ApiException(code, "${result.msg?.trim()?.ifEmpty { null } ?: "请求失败"}（代码 $code）")
        }
        return result.data ?: throw Exception("次元城没有返回数据")
    }

    // ============================= Settings =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = POPULAR_PREF
            title = "热门动画显示番剧周表"
            summary = "开启后，“热门”内容会显示当天应该更新的动画，但不一定更新"
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_USERNAME
            title = "次元城账号"
            dialogTitle = title
            summary = accountSummary(preferences.getString(PREF_USERNAME, null))
            setOnPreferenceChangeListener { _, newValue ->
                summary = accountSummary(newValue as? String)
                preferences.edit().remove(PREF_TOKEN).apply()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "次元城密码"
            dialogTitle = title
            summary = passwordSummary(preferences.getString(PREF_PASSWORD, null))
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = passwordSummary(newValue as? String)
                preferences.edit().remove(PREF_TOKEN).apply()
                true
            }
        }.also(screen::addPreference)

        // Read-only: shows the full report of the last failed playback, which toasts cut off.
        EditTextPreference(screen.context).apply {
            key = PREF_LAST_ERROR
            title = "最近一次错误"
            dialogTitle = title
            summary = lastErrorSummary(preferences.getString(PREF_LAST_ERROR, null))
            setOnPreferenceChangeListener { _, _ -> false }
        }.also(screen::addPreference)
    }

    private fun lastErrorSummary(error: String?) =
        error?.ifBlank { null }?.let { "${it.lineSequence().first()}…（点此查看完整内容，可复制）" } ?: "无"

    private fun accountSummary(username: String?) =
        username?.trim()?.ifEmpty { null }?.let { "已填写：$it" } ?: "播放视频需要次元城账号（在网站或 App 注册）"

    private fun passwordSummary(password: String?) = if (password.isNullOrEmpty()) "未填写" else "已填写"

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
        private const val POPULAR_PREF = "POPULAR_DISPLAY"
        private const val PREF_USERNAME = "CYCITY_USERNAME"
        private const val PREF_PASSWORD = "CYCITY_PASSWORD"
        private const val PREF_TOKEN = "CYCITY_TOKEN"
        private const val PREF_LAST_ERROR = "CYCITY_LAST_ERROR"

        private const val PAGE_SIZE = 30
        private const val MAX_PAGE_SIZE = 100
        private const val DEFAULT_ZONE = "1"
        private const val SORT_BY_UPDATE = 0
        private const val SORT_BY_HITS = 1

        private val ANIME_PATH_REGEX = Regex("""^/anime/(\d+)""")
        private val EPISODE_NUMBER_REGEX = Regex("""\d+(\.\d+)?""")
        private val NON_WORD_REGEX = Regex("""[\s\p{P}\p{S}]+""")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36"
    }
}
