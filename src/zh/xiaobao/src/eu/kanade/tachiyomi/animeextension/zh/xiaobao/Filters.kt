package eu.kanade.tachiyomi.animeextension.zh.xiaobao

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import java.util.Calendar

open class PathFilter(name: String, private val options: List<Pair<String, String?>>, state: Int = 0) :
    AnimeFilter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val selected: String? get() = options[state].second
}

/** The site always lists one category; a top-level category includes its sub-categories. */
class CategoryFilter(state: Int = 0) : PathFilter("分类", CATEGORIES, state) {
    companion object {
        val CATEGORIES = listOf(
            "连续剧（全部）" to "2",
            "连续剧 · 欧美剧" to "33",
            "连续剧 · 韩国剧" to "35",
            "连续剧 · 日本剧" to "37",
            "连续剧 · 国产剧" to "32",
            "连续剧 · 香港剧" to "34",
            "连续剧 · 台湾剧" to "36",
            "连续剧 · 海外剧" to "38",
            "电影（全部）" to "1",
            "电影 · 动作片" to "24",
            "电影 · 喜剧片" to "25",
            "电影 · 科幻片" to "26",
            "电影 · 恐怖片" to "27",
            "电影 · 爱情片" to "28",
            "电影 · 剧情片" to "29",
            "电影 · 战争片" to "30",
            "电影 · 动画电影" to "31",
            "纪录片" to "21",
            "综艺" to "3",
            "动漫" to "4",
        )

        fun indexOf(id: String) = CATEGORIES.indexOfFirst { it.second == id }.coerceAtLeast(0)
    }
}

class SortFilter(state: Int = 0) :
    PathFilter("排序", listOf("最近更新" to "time", "人气" to "hits", "评分" to "score"), state)

class AreaFilter :
    PathFilter(
        "地区",
        listOf("全部" to null) + listOf(
            "美国", "英国", "韩国", "日本", "中国大陆", "中国香港", "中国台湾", "法国", "德国", "加拿大",
        ).map { it to it },
    )

class LangFilter :
    PathFilter(
        "语言",
        listOf("全部" to null) + listOf(
            "英语", "韩语", "日语", "汉语普通话", "粤语", "法语", "西班牙语", "德语", "意大利语", "俄语",
        ).map { it to it },
    )

class YearFilter :
    PathFilter(
        "年份",
        listOf<Pair<String, String?>>("全部" to null) +
            (Calendar.getInstance().get(Calendar.YEAR) downTo 2000).map { "$it" to "$it" },
    )
