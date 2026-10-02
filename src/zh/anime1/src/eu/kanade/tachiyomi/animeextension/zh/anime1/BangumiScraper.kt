package eu.kanade.tachiyomi.animeextension.zh.anime1

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.network.POST
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
import kotlin.math.abs

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
    private const val SEARCH_URL = "https://api.bgm.tv/v0/search/subjects"
    private const val TYPE_ANIME = 2
    private const val CANDIDATES_WHEN_DATED = 10
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val SEPARATOR_REGEX = Regex("[\\s\\-～~：:！!？?・·「」『』]+")

    /**
     * Blocking search. With [airQuarter] (see [airQuarter]), only a result that
     * started airing within one season of it is accepted, which picks the right
     * season of a series and avoids unrelated matches. Without it, the top result wins.
     */
    fun search(client: OkHttpClient, keyword: String, airQuarter: Int? = null): Subject? {
        for (candidate in keyword.candidates()) {
            val limit = if (airQuarter == null) 1 else CANDIDATES_WHEN_DATED
            val results = client.newCall(searchRequest(candidate, limit)).execute().use { it.parseResults() }
            if (results.isEmpty()) continue
            if (airQuarter == null) return results.first()
            val closest = results.withIndex()
                .minByOrNull { (index, subject) -> subject.distanceTo(airQuarter) * 100 + index }!!
                .value
            if (closest.distanceTo(airQuarter) <= 1) return closest
        }
        return null
    }

    /** Copies Bangumi's details onto [anime]. */
    fun Subject.applyTo(anime: SAnime, fetchType: BangumiFetchType) {
        anime.description = summary
        if (fetchType == BangumiFetchType.ALL) {
            anime.genre = buildList {
                addAll(metaTags)
                findInfo("动画制作")?.let { add(it) }
                findInfo("放送开始")?.let { add(it) }
            }.joinToString()
            anime.author = findInfo("原作")
            anime.artist = findInfo("导演") ?: findInfo("监督")
            if (findInfo("播放结束") != null) {
                anime.status = SAnime.COMPLETED
            } else if (findInfo("放送开始") != null) {
                anime.status = SAnime.ONGOING
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

    private fun searchRequest(keyword: String, limit: Int): Request {
        val body = buildJsonObject {
            put("keyword", keyword)
            putJsonObject("filter") {
                putJsonArray("type") { add(TYPE_ANIME) }
            }
        }
        return POST("$SEARCH_URL?limit=$limit", body = body.toString().toRequestBody(JSON_MEDIA_TYPE))
    }

    private fun Response.parseResults(): List<Subject> {
        if (!isSuccessful) throw Exception("Bangumi: HTTP $code")
        return json.decodeFromString<SearchResponse>(body.string()).data
    }

    private fun Subject.distanceTo(quarter: Int): Int = airQuarter?.let { abs(it - quarter) } ?: Int.MAX_VALUE / 200
}

/** Counts seasons (quarters) since year 0, so adjacent seasons differ by 1 across year boundaries. */
fun airQuarter(year: Int, month: Int): Int = year * 4 + (month - 1) / 3

@Serializable
private class SearchResponse(val data: List<Subject> = emptyList())

@Serializable
class Images(val medium: String? = null)

@Serializable
class InfoboxItem(val key: String, val value: JsonElement)

@Serializable
class Subject(
    /** Original (usually Japanese) title. */
    val name: String = "",
    /** First air date, "yyyy-MM-dd". */
    val date: String? = null,
    val summary: String? = null,
    val images: Images? = null,
    @SerialName("meta_tags") val metaTags: List<String> = emptyList(),
    val infobox: List<InfoboxItem> = emptyList(),
) {
    val airQuarter: Int?
        get() {
            val match = DATE_REGEX.find(date.orEmpty()) ?: return null
            return airQuarter(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }

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

    private companion object {
        val DATE_REGEX = Regex("^(\\d{4})-(\\d{2})")
    }
}
