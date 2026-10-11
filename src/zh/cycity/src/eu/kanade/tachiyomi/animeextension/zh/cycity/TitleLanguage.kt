package eu.kanade.tachiyomi.animeextension.zh.cycity

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.animesource.model.SAnime

/** How anime are titled, to make MyAnimeList tracking easier. Same options as the Anime1.me extension. */
enum class TitleLanguage(val label: String) {
    CHINESE("中文（网站原标题）"),
    CHINESE_WITH_ID("中文，简介里加上MAL ID"),
    ROMAJI("罗马拼音（MAL标题）"),
    ENGLISH("英文（没有英文名时用罗马拼音）"),
    ;

    val needsMal get() = this != CHINESE

    /**
     * Renames [anime] to its MAL title (romaji/English options) and puts the MAL title and ID at the
     * top of its description. The ID gets a line of its own so a long press selects just "id:123",
     * which pasted into the MAL tracker search gives an exact match.
     */
    fun applyTo(anime: SAnime, mal: MalTitle?) {
        val malId = mal?.malId ?: return
        when (this) {
            ROMAJI -> mal.romaji ?: mal.english
            ENGLISH -> mal.english ?: mal.romaji
            else -> null
        }?.let { anime.title = it }
        val name = mal.romaji ?: mal.english ?: anime.title
        anime.description = listOfNotNull(
            "MAL: $name\nid:$malId",
            anime.description?.takeIf { it.isNotBlank() },
        ).joinToString("\n\n")
    }

    companion object {
        private const val PREF_KEY = "PREF_KEY_TITLE_LANGUAGE"

        fun from(preferences: SharedPreferences): TitleLanguage =
            preferences.getString(PREF_KEY, null)
                ?.let { saved -> entries.firstOrNull { it.name == saved } }
                ?: CHINESE

        fun preference(context: Context, preferences: SharedPreferences) = ListPreference(context).apply {
            key = PREF_KEY
            title = "标题语言（方便MAL追踪）"
            entries = TitleLanguage.entries.map { it.label }.toTypedArray()
            entryValues = TitleLanguage.entries.map { it.name }.toTypedArray()
            setDefaultValue(CHINESE.name)
            summary = summaryFor(from(preferences))
            setOnPreferenceChangeListener { _, value ->
                summary = summaryFor(TitleLanguage.entries.first { it.name == value })
                true
            }
        }

        private fun summaryFor(language: TitleLanguage): String = when (language) {
            CHINESE -> language.label
            CHINESE_WITH_ID -> "${language.label}\n标题保持中文，打开动画页面时在简介最上面加上MAL标题和「id:12345」，" +
                "可复制到MAL追踪搜索。"
            else -> "${language.label}\n打开动画页面时改名，让MAL追踪可直接搜索到。" +
                "已收藏的动画需在App设置开启「Update library anime titles to match source」，再下拉刷新该动画。"
        }
    }
}
