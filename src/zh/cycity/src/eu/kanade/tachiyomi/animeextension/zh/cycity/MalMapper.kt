package eu.kanade.tachiyomi.animeextension.zh.cycity

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * Finds an anime's MyAnimeList entry. The site gives each anime's Bangumi ID, which leads to its
 * original (Japanese) title and first air date; AniList's search matches those titles well and
 * returns the MAL ID plus the romaji title MAL itself uses. The air date picks the right season
 * of a series.
 */
object MalMapper {
    private const val BANGUMI_URL = "https://api.bgm.tv/v0/subjects"
    private const val ANILIST_URL = "https://graphql.anilist.co"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val DATE_REGEX = Regex("""^(\d{4})-(\d{2})""")
    private val QUOTES_REGEX = Regex("[「」『』]")
    private val SPACES_REGEX = Regex("""\s+""")
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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Blocking. Searches by the Bangumi entry's original title first, then [otherNames]. With a
     * known air date, only an entry that started within one season of it is accepted: no MAL
     * entry is better than a wrong one.
     */
    fun find(client: OkHttpClient, bangumiId: Long?, otherNames: List<String>, airDate: String?): MalTitle? {
        val subject = bangumiId?.let { id -> runCatching { bangumiSubject(client, id) }.getOrNull() }
        val quarter = airQuarter(subject?.date) ?: airQuarter(airDate)
        val names = (listOfNotNull(subject?.name) + otherNames)
            .flatMap { it.searchNames() }
            .distinct()
        for (name in names) {
            val results = search(client, name)
            if (results.isEmpty()) continue
            val match = if (quarter == null) {
                results.first()
            } else {
                results.firstOrNull { it.startDate.isNear(quarter) } ?: continue
            }
            if (match.idMal == null) continue
            return MalTitle(match.idMal, match.title.romaji, match.title.english)
        }
        return null
    }

    private fun bangumiSubject(client: OkHttpClient, id: Long): BangumiSubject? =
        client.newCall(GET("$BANGUMI_URL/$id")).execute().use { response ->
            if (!response.isSuccessful) return null
            json.decodeFromString<BangumiSubject>(response.body.string())
        }

    /** AniList misses some spellings, e.g. "劇場版 鬼滅の刃 無限列車編" vs 劇場版「鬼滅の刃」無限列車編. */
    private fun String.searchNames(): List<String> {
        val unquoted = replace(QUOTES_REGEX, " ").replace(SPACES_REGEX, " ").trim()
        return listOf(trim(), unquoted, unquoted.removePrefix("劇場版").trim())
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun search(client: OkHttpClient, name: String): List<Media> {
        val body = buildJsonObject {
            put("query", QUERY)
            putJsonObject("variables") { put("search", name) }
        }
        val request = POST(ANILIST_URL, body = body.toString().toRequestBody(JSON_MEDIA_TYPE))
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("AniList: HTTP ${response.code}")
            json.decodeFromString<GraphQlResponse>(response.body.string()).data?.page?.media.orEmpty()
        }
    }

    /** Counts seasons (quarters) since year 0, so adjacent seasons differ by 1 across year boundaries. */
    private fun airQuarter(year: Int, month: Int): Int = year * 4 + (month - 1) / 3

    private fun airQuarter(date: String?): Int? {
        val match = DATE_REGEX.find(date.orEmpty()) ?: return null
        val month = match.groupValues[2].toInt().takeIf { it in 1..12 } ?: return null
        return airQuarter(match.groupValues[1].toInt(), month)
    }

    private fun FuzzyDate.isNear(quarter: Int): Boolean {
        val year = year ?: return false
        // Some entries only have a year.
        val month = month ?: return year == quarter / 4
        return abs(airQuarter(year, month) - quarter) <= 1
    }

    @Serializable
    private class BangumiSubject(val name: String = "", val date: String? = null)

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
