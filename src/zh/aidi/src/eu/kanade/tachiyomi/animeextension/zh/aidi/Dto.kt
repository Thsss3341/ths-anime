package eu.kanade.tachiyomi.animeextension.zh.aidi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The site's MacCMS video API (api.php/provide/vod). */
@Serializable
class VodListDto(
    val page: Int = 1,
    val pagecount: Int = 1,
    val list: List<VodDto> = emptyList(),
)

@Serializable
class VodDto(
    @SerialName("vod_id") val id: Long,
    @SerialName("vod_name") val name: String,
    @SerialName("vod_sub") val sub: String? = null,
    @SerialName("vod_pic") val pic: String? = null,
    @SerialName("vod_remarks") val remarks: String? = null,
    @SerialName("type_name") val typeName: String? = null,
    @SerialName("vod_class") val classes: String? = null,
    @SerialName("vod_area") val area: String? = null,
    @SerialName("vod_lang") val language: String? = null,
    @SerialName("vod_year") val year: String? = null,
    @SerialName("vod_actor") val actor: String? = null,
    @SerialName("vod_director") val director: String? = null,
    @SerialName("vod_content") val content: String? = null,
    @SerialName("vod_isend") val isEnd: Int? = null,
    @SerialName("vod_douban_score") val doubanScore: String? = null,
    @SerialName("vod_play_from") val playFrom: String? = null,
    @SerialName("vod_play_url") val playUrl: String? = null,
)

/** One playback line ("线路") and its episodes, as (name, url) pairs in order. */
class PlayLine(val code: String, val episodes: List<Pair<String, String>>)
