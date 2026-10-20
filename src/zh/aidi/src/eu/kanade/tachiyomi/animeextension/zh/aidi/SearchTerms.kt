package eu.kanade.tachiyomi.animeextension.zh.aidi

import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import eu.kanade.tachiyomi.animesource.model.AnimesPage

/**
 * Lets Traditional Chinese searches find the site's Simplified Chinese titles: the query is
 * searched as converted to Simplified (including Taiwanese wording) and as typed, if that differs.
 */
object SearchTerms {
    /** The query in Simplified Chinese first, then as typed if that differs. */
    fun variants(query: String): List<String> {
        val trimmed = query.trim()
        return listOf(ZhTwConverterUtil.toSimple(trimmed), trimmed).filter { it.isNotEmpty() }.distinct()
    }

    /** Results of every variant on one page, without duplicates, in variant order. */
    fun merge(pages: List<AnimesPage>): AnimesPage = AnimesPage(
        pages.flatMap { it.animes }.distinctBy { it.url },
        pages.any { it.hasNextPage },
    )
}
