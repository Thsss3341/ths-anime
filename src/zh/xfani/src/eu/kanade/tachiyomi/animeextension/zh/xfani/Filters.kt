package eu.kanade.tachiyomi.animeextension.zh.xfani

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import java.util.Calendar

open class SelectFilter<T>(name: String, private val options: List<Pair<String, T>>) :
    AnimeFilter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected: T get() = options[state].second
}

/** The site's channels (`anime_types`). */
class TypeFilter :
    SelectFilter<Int?>(
        "频道",
        listOf("全部" to null, "连载新番" to 1, "完结旧番" to 2, "剧场版" to 3, "美漫" to 4),
    )

class SortFilter(state: Int = 0) :
    SelectFilter<String>(
        "排序",
        listOf("默认" to "created_at", "热度" to "view_count", "评分" to "bangumi_score", "最新" to "release_date"),
    ) {
    init {
        this.state = state
    }
}

class YearFilter :
    SelectFilter<Int?>(
        "年份",
        listOf("全部" to null) + (Calendar.getInstance().get(Calendar.YEAR) downTo 1969).map { "$it" to it },
    )
