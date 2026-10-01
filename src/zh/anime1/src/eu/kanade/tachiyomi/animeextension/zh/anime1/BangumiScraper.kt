package eu.kanade.tachiyomi.animeextension.zh.anime1

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

enum class BangumiFetchType {
    /** Cover and summary only. */
    SHORT,

    /** Cover, summary, genres, staff and status. */
    ALL,
}

/**
 * Looks anime up on Bangumi (bgm.tv), used because anime1.me has no covers or
 * descriptions of its own. Keywords should be Simplified Chinese.
 */
object BangumiScraper {
    private const val SEARCH_URL = "https://api.bgm.tv/v0/search/subjects?limit=1"
    private const val TYPE_ANIME = 2
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val SEPARATOR_REGEX = Regex("[\\s\\-～~：:！!？?・·「」『』]+")

    /** Blocking search, for use inside interceptors. */
    fun search(client: OkHttpClient, keyword: String): Subject? {
        return keyword.candidates().firstNotNullOfOrNull { candidate ->
            client.newCall(searchRequest(candidate)).execute().use { it.parseFirst() }
        }
    }

    suspend fun fetchDetail(
        client: OkHttpClient,
        keyword: String,
        fetchType: BangumiFetchType = BangumiFetchType.SHORT,
    ): SAnime {
        var subject: Subject? = null
        for (candidate in keyword.candidates()) {
            subject = client.newCall(searchRequest(candidate)).awaitSuccess().use { it.parseFirst() }
            if (subject != null) break
        }
        if (subject == null) return SAnime.create()
        return SAnime.create().apply {
            thumbnail_url = subject.images?.medium
            description = subject.summary
            if (fetchType == BangumiFetchType.ALL) {
                genre = buildList {
                    addAll(subject.metaTags)
                    subject.findInfo("动画制作")?.let { add(it) }
                    subject.findInfo("放送开始")?.let { add(it) }
                }.joinToString()
                author = subject.findInfo("原作")
                artist = subject.findInfo("导演") ?: subject.findInfo("监督")
                if (subject.findInfo("播放结束") != null) {
                    status = SAnime.COMPLETED
                } else if (subject.findInfo("放送开始") != null) {
                    status = SAnime.ONGOING
                }
            }
        }
    }

    /**
     * The full title first, then just its first word: some titles carry subtitles
     * Bangumi doesn't know (e.g. "Clevatess II -魔兽之王与虚假的勇者传承-").
     */
    private fun String.candidates(): List<String> {
        val first = split(SEPARATOR_REGEX).first()
        return if (first.length >= 2 && first != this) listOf(this, first) else listOf(this)
    }

    private fun searchRequest(keyword: String): Request {
        val body = buildJsonObject {
            put("keyword", keyword)
            putJsonObject("filter") {
                putJsonArray("type") { add(TYPE_ANIME) }
            }
        }
        return POST(SEARCH_URL, body = body.toString().toRequestBody(JSON_MEDIA_TYPE))
    }

    private fun Response.parseFirst(): Subject? {
        if (!isSuccessful) throw Exception("Bangumi: HTTP $code")
        return json.decodeFromString<SearchResponse>(body.string()).data.firstOrNull()
    }
}

@Serializable
private class SearchResponse(val data: List<Subject> = emptyList())

@Serializable
class Images(val medium: String? = null)

@Serializable
class InfoboxItem(val key: String, val value: JsonElement)

@Serializable
class Subject(
    val summary: String? = null,
    val images: Images? = null,
    @SerialName("meta_tags") val metaTags: List<String> = emptyList(),
    val infobox: List<InfoboxItem> = emptyList(),
) {
    /** Infobox values are either a string or a list of `{"v": "..."}` objects. */
    fun findInfo(key: String): String? {
        val value = infobox.firstOrNull { it.key == key }?.value ?: return null
        return when (value) {
            is JsonPrimitive -> value.contentOrNull
            is JsonArray -> value.mapNotNull { (it as? JsonObject)?.get("v")?.jsonPrimitive?.contentOrNull }
                .joinToString()
            else -> null
        }?.takeIf { it.isNotBlank() }
    }
}
