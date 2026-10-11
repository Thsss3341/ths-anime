package eu.kanade.tachiyomi.animeextension.zh.cycity

import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Lets Traditional Chinese searches find the site's Simplified Chinese titles. The query is
 * searched as converted to Simplified (including Taiwanese wording) and as typed, since the site
 * also matches some aliases as typed (e.g. 間諜家家酒). When neither finds anything, Bangumi is
 * asked for the anime's Simplified Chinese name, which covers Taiwanese titles the site doesn't
 * know as aliases.
 */
object SearchTerms {
    private const val BANGUMI_SEARCH_URL = "https://api.bgm.tv/v0/search/subjects?limit=1"
    private const val TYPE_ANIME = 2
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    /** The query in Simplified Chinese first, then as typed if that differs. */
    fun variants(query: String): List<String> {
        val trimmed = query.trim()
        return listOf(ZhTwConverterUtil.toSimple(trimmed), trimmed).filter { it.isNotEmpty() }.distinct()
    }

    /** Results of every variant on one page, without duplicates, in variant order. */
    fun merge(pages: List<AnimesPage>): AnimesPage = AnimesPage(
        pages.flatMap { it.animes }.distinctBy { it.url },
        pages.any { it.hasNextPage },
    )

    /** Blocking. Bangumi's Simplified Chinese name for the best match, if it isn't one of [tried]. */
    fun bangumiName(client: OkHttpClient, keyword: String, tried: List<String>): String? {
        val body = buildJsonObject {
            put("keyword", keyword)
            putJsonObject("filter") { putJsonArray("type") { add(TYPE_ANIME) } }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val subject = client.newCall(POST(BANGUMI_SEARCH_URL, body = body)).execute().use { response ->
            if (!response.isSuccessful) return null
            json.decodeFromString<SearchResponse>(response.body.string()).data.firstOrNull()
        } ?: return null
        return subject.nameCn.ifBlank { subject.name }.trim().takeIf { it.isNotEmpty() && it !in tried }
    }

    @Serializable
    private class SearchResponse(val data: List<Subject> = emptyList())

    @Serializable
    private class Subject(val name: String = "", @SerialName("name_cn") val nameCn: String = "")
}
