package eu.kanade.tachiyomi.animeextension.zh.cycity

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import java.util.Calendar

open class QueryFilter(name: String, private val options: Array<Pair<String, String?>>, state: Int = 0) :
    AnimeFilter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val selected get() = options[state].second
}

class ZoneFilter : QueryFilter("频道", arrayOf("TV番组" to "1", "剧场番组" to "2"))

class SortFilter(state: Int = 0) :
    QueryFilter("排序", arrayOf("最近更新" to "update_time", "人气" to "hits", "评分" to "score"), state)

class TagFilter :
    QueryFilter(
        "类型",
        arrayOf(
            "全部", "原创", "漫画改", "小说改", "游戏改", "异世界", "特摄", "热血", "穿越",
            "奇幻", "战斗", "搞笑", "日常", "科幻", "治愈", "校园", "泡面", "恋爱",
            "后宫", "少女", "百合", "魔法", "冒险", "历史", "架空", "机战", "运动",
            "励志", "音乐", "推理", "社团", "智斗", "催泪", "美食", "偶像", "乙女",
            "职场",
        ).map { it to it.takeIf { tag -> tag != "全部" } }.toTypedArray(),
    )

class YearFilter :
    QueryFilter(
        "年份",
        (listOf<Pair<String, String?>>("全部" to null) +
            (Calendar.getInstance().get(Calendar.YEAR) downTo 1980).map { "$it" to "$it" }).toTypedArray(),
    )
