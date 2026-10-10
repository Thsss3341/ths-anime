package eu.kanade.tachiyomi.animeextension.zh.xfani

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row of the `animes` table, also returned by the `search_animes` RPC. */
@Serializable
class AnimeDto(
    val id: Long,
    val title: String,
    @SerialName("title_original") val titleOriginal: String? = null,
    val aliases: List<String>? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
    val director: String? = null,
    val actors: List<String>? = null,
    @SerialName("meta_tags") val metaTags: List<String>? = null,
    @SerialName("is_finished") val isFinished: Boolean? = null,
    /** Only set by `search_animes`: the number of matches across all pages. */
    @SerialName("total_count") val totalCount: Long? = null,
)

@Serializable
class IdDto(val id: Long)

/** A playback line on an anime page, with the episodes it has. */
@Serializable
class SourceDto(
    val id: Long,
    val code: String,
    val name: String,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
class EpisodeDto(
    val id: Long,
    val kind: String = "main",
    val title: String? = null,
    @SerialName("episode_number") val episodeNumber: Double? = null,
    @SerialName("available_at") val availableAt: String? = null,
)

/** Response of the `issue-web-playback` edge function. */
@Serializable
class PlaybackDto(
    val ok: Boolean = false,
    val error: String? = null,
    val url: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    val candidates: List<CandidateDto> = emptyList(),
)

@Serializable
class CandidateDto(
    @SerialName("source_name") val sourceName: String,
    val url: String,
)

/** The schema.org list embedded in the /recent page. */
@Serializable
class ItemListDto(val itemListElement: List<ListItemDto> = emptyList())

@Serializable
class ListItemDto(val item: ItemDto)

@Serializable
class ItemDto(val name: String, val url: String, val image: String? = null)
