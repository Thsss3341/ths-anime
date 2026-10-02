package eu.kanade.tachiyomi.animeextension.zh.anime1

import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.abs

/** The names MyAnimeList knows an anime by. */
class MalTitle(val malId: Int?, val romaji: String?, val english: String?)

/**
 * Finds the MyAnimeList entry for a Bangumi subject. AniList's search matches
 * Bangumi's original (Japanese) titles well and returns the MAL ID plus the
 * romaji title MAL itself uses.
 */
object AniListMapper {
    private const val API_URL = "https://graphql.anilist.co"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val QUOTES_REGEX = Regex("[「」『』]")
    private val SPACES_REGEX = Regex("\\s+")
    private const val QUERY = """
        query (${'$'}search: String) {
          Page(perPage: 5) {
            media(search: ${'$'}search, type: ANIME) {
              idMal
              startDate { year month }
              title { romaji english }
            }
          }
        }
    """

    /** Blocking. Returns null rather than guess when nothing aired close to [Subject.date]. */
    fun find(client: OkHttpClient, subject: Subject): MalTitle? {
        val quarter = subject.airQuarter
        for (name in subject.name.searchNames()) {
            val results = search(client, name)
            if (results.isEmpty()) continue
            val match = if (quarter == null) {
                results.first()
            } else {
                results.firstOrNull { it.startDate.isNear(quarter) } ?: continue
            }
            return MalTitle(match.idMal, match.title.romaji, match.title.english)
        }
        return null
    }

    /** AniList misses some Bangumi spellings, e.g. "劇場版 鬼滅の刃 無限列車編" vs 劇場版「鬼滅の刃」無限列車編. */
    private fun String.searchNames(): List<String> {
        val unquoted = replace(QUOTES_REGEX, " ").replace(SPACES_REGEX, " ").trim()
        return listOf(this, unquoted, unquoted.removePrefix("劇場版").trim())
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun search(client: OkHttpClient, name: String): List<Media> {
        val body = buildJsonObject {
            put("query", QUERY)
            putJsonObject("variables") { put("search", name) }
        }
        val request = POST(API_URL, body = body.toString().toRequestBody(JSON_MEDIA_TYPE))
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("AniList: HTTP ${response.code}")
            json.decodeFromString<GraphQlResponse>(response.body.string()).data?.page?.media.orEmpty()
        }
    }

    private fun FuzzyDate.isNear(quarter: Int): Boolean {
        val year = year ?: return false
        // Some entries only have a year.
        val month = month ?: return year == quarter / 4
        return abs(airQuarter(year, month) - quarter) <= 1
    }

    @Serializable
    private class GraphQlResponse(val data: Data? = null)

    @Serializable
    private class Data(@SerialName("Page") val page: Page? = null)

    @Serializable
    private class Page(val media: List<Media> = emptyList())

    @Serializable
    private class Media(
        val idMal: Int? = null,
        val startDate: FuzzyDate = FuzzyDate(),
        val title: Title = Title(),
    )

    @Serializable
    private class FuzzyDate(val year: Int? = null, val month: Int? = null)

    @Serializable
    private class Title(val romaji: String? = null, val english: String? = null)
}
