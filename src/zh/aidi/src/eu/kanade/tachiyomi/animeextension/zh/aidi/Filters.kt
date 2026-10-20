package eu.kanade.tachiyomi.animeextension.zh.aidi

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

/**
 * The API only lists a sub-category's own videos (asking for 电影 or 连续剧 as a whole returns
 * nothing), so only the sub-categories are offered.
 */
class CategoryFilter :
    AnimeFilter.Select<String>("分类", CATEGORIES.map { it.first }.toTypedArray()) {
    val selected: String? get() = CATEGORIES[state].second

    private companion object {
        val CATEGORIES = listOf(
            "全部" to null,
            "电影 · 动作片" to "6",
            "电影 · 喜剧片" to "7",
            "电影 · 爱情片" to "8",
            "电影 · 科幻片" to "9",
            "电影 · 恐怖片" to "10",
            "电影 · 剧情片" to "11",
            "电影 · 战争片" to "12",
            "电影 · 传记" to "23",
            "韩国电影" to "20",
            "剧集 · 美剧" to "16",
            "剧集 · 英剧" to "25",
            "剧集 · 韩剧" to "15",
            "剧集 · 日剧" to "21",
            "剧集 · 国产剧" to "13",
            "剧集 · 港台剧" to "14",
            "剧集 · 泰剧" to "36",
            "剧集 · 其他剧" to "40",
            "纪录片" to "24",
            "综艺 · 韩国综艺" to "33",
            "综艺 · 日本综艺" to "41",
            "综艺 · 欧美综艺" to "39",
            "综艺 · 港台综艺" to "38",
            "动漫 · 日韩动漫" to "31",
            "动漫 · 欧美动漫" to "35",
            "动漫 · 动漫电影" to "32",
        )
    }
}
