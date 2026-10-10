package eu.kanade.tachiyomi.animeextension.zh.cycity

import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class VodResponse(val list: List<VodInfo> = emptyList())

@Serializable
class VodInfo(
    @SerialName("vod_id") val id: Int,
    @SerialName("vod_name") val name: String,
    @SerialName("vod_pic") val pic: String? = null,
    @SerialName("vod_actor") val actor: String? = null,
) {
    fun toSAnime() = SAnime.create().apply {
        url = "/bangumi/$id.html"
        thumbnail_url = pic
        title = name
        author = actor?.replace(",,,", "")
    }
}
