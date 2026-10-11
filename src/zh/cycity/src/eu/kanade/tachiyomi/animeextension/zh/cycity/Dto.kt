package eu.kanade.tachiyomi.animeextension.zh.cycity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every API response: `code` is 0 on success, otherwise `msg` says what went wrong. */
@Serializable
class ApiResponse<T>(
    val code: Int = -1,
    val msg: String? = null,
    val data: T? = null,
)

@Serializable
class ListDto<T>(
    val list: List<T> = emptyList(),
    val pager: PagerDto? = null,
)

@Serializable
class PagerDto(
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage get() = page.toLong() * pageSize < total
}

@Serializable
class VideoCardDto(
    @SerialName("video_id") val id: Long,
    val title: String,
    @SerialName("cover_url") val coverUrl: String? = null,
)

@Serializable
class WeekdayDto(
    val weekday: Int,
    val videos: List<VideoCardDto> = emptyList(),
)

@Serializable
class VideoDetailDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val subtitle: String? = null,
    @SerialName("english_title") val englishTitle: String? = null,
    @SerialName("bangumi_id") val bangumiId: Long? = null,
    @SerialName("publish_date") val publishDate: String? = null,
    val director: List<String> = emptyList(),
    val writer: String? = null,
    val year: Int? = null,
    val tags: List<String> = emptyList(),
    val completed: Boolean = false,
    @SerialName("play_from") val playFrom: List<PlaySourceDto> = emptyList(),
)

@Serializable
class PlaySourceDto(
    val code: String,
    val title: String? = null,
)

@Serializable
class SectionDto(
    val id: Long,
    val title: String,
)

@Serializable
class PlayUrlDto(
    val name: String? = null,
    val url: String? = null,
)

@Serializable
class LoginDto(
    val token: String? = null,
)
