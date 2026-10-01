package eu.kanade.tachiyomi.animeextension.zh.anime1

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

enum class BangumiFetchType {
    /** Cover and summary only. */
    SHORT,

    /** Cover, summary, genres, staff and status. */
    ALL,
}

/**
 * Fetches anime details from Bangumi (bgm.tv), used because anime1.me has no
 * covers or descriptions of its own.
 */
object BangumiScraper {
    private const val SEARCH_URL = "https://api.bgm.tv/search/subject"
    private const val SUBJECTS_URL = "https://api.bgm.tv/v0/subjects"
    private const val TYPE_ANIME = 2

    suspend fun fetchDetail(
        client: OkHttpClient,
        keyword: String,
        fetchType: BangumiFetchType = BangumiFetchType.SHORT,
    ): SAnime {
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addPathSegment(keyword)
            .addQueryParameter("responseGroup", if (fetchType == BangumiFetchType.ALL) "small" else "medium")
            .addQueryParameter("type", "$TYPE_ANIME")
            .addQueryParameter("start", "0")
            .addQueryParameter("max_results", "1")
            .build()
        val body = client.newCall(GET(url)).awaitSuccess().checkErrorMessage()
        // Bangumi returns a bare error object (no "list") when nothing matches.
        val item = runCatching { json.decodeFromString<SearchResponse>(body) }.getOrNull()
            ?.list?.firstOrNull()
            ?: return SAnime.create()
        return if (fetchType == BangumiFetchType.ALL) {
            fetchSubject(client, item.id)
        } else {
            SAnime.create().apply {
                thumbnail_url = item.images?.large
                description = item.summary
            }
        }
    }

    private suspend fun fetchSubject(client: OkHttpClient, id: Int): SAnime {
        val url = SUBJECTS_URL.toHttpUrl().newBuilder().addPathSegment("$id").build()
        val subject = json.decodeFromString<Subject>(
            client.newCall(GET(url)).awaitSuccess().checkErrorMessage(),
        )
        return SAnime.create().apply {
            thumbnail_url = subject.images?.large
            description = subject.summary
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

    private fun Response.checkErrorMessage(): String {
        val responseStr = body.string()
        val error = runCatching {
            json.parseToJsonElement(responseStr).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (error != null && !responseStr.contains("\"list\"")) {
            throw Exception("Bangumi: $error")
        }
        return responseStr
    }
}

@Serializable
private class SearchResponse(val list: List<SearchItem> = emptyList())

@Serializable
private class SearchItem(
    val id: Int,
    val summary: String? = null,
    val images: Images? = null,
)

@Serializable
private class Images(val large: String? = null)

@Serializable
private class InfoboxItem(val key: String, val value: JsonElement)

@Serializable
private class Subject(
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
