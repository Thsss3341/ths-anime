package eu.kanade.tachiyomi.animeextension.zh.duboku

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import java.util.Calendar

open class PathFilter(name: String, private val options: List<Pair<String, String?>>, state: Int = 0) :
    AnimeFilter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val selected: String? get() = options[state].second
}

/** The site always lists one category; 连续剧 includes its sub-categories. */
class CategoryFilter(state: Int = 0) : PathFilter("分类", CATEGORIES, state) {
    companion object {
        val CATEGORIES = listOf(
            "连续剧（全部）" to "2",
            "连续剧 · 陆剧" to "13",
            "连续剧 · 日韩剧" to "15",
            "连续剧 · 台泰剧" to "14",
            "连续剧 · 短剧" to "21",
            "电影" to "1",
            "综艺" to "3",
            "动漫" to "4",
        )

        fun indexOf(id: String) = CATEGORIES.indexOfFirst { it.second == id }.coerceAtLeast(0)
    }
}

class SortFilter(state: Int = 0) :
    PathFilter("排序", listOf("最近更新" to null, "人气" to "人气", "评分" to "评分"), state)

private fun options(vararg values: String) = listOf<Pair<String, String?>>("全部" to null) + values.map { it to it }

class AreaFilter :
    PathFilter(
        "地区",
        options("大陆", "台湾", "韩国", "日本", "泰国", "新加坡", "美国", "英国", "法国", "加拿大", "澳大利亚"),
    )

/** Each category offers its own genres; one the category doesn't use finds nothing. */
class GenreFilter :
    PathFilter(
        "类型（随分类而异）",
        options(
            // 连续剧
            "爱情", "古装", "悬疑", "都市", "喜剧", "家庭", "剧情", "青春", "偶像", "奇幻", "武侠", "科幻",
            "动作", "惊悚", "历史", "年代", "宫廷", "穿越", "警匪", "谍战", "罪案", "战争", "历险", "乡村",
            "经典", "同性",
            // 电影
            "恐怖", "犯罪", "冒险", "动画",
            // 综艺
            "真人秀", "选秀", "竞演", "情感", "旅游", "音乐", "美食", "纪实", "生活", "游戏互动", "竞技",
            "搞笑", "脱口秀",
            // 动漫
            "热血", "推理", "爆笑", "校园", "机战", "少女", "格斗", "恋爱", "魔幻",
        ),
    )

class LangFilter : PathFilter("语言", options("国语", "韩语", "日语", "泰语", "英语", "法语"))

class YearFilter :
    PathFilter(
        "年份",
        options(*(Calendar.getInstance().get(Calendar.YEAR) downTo 2015).map { "$it" }.toTypedArray(), "更早"),
    )
