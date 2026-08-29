package eu.kanade.tachiyomi.ui.library.novel.quotes

import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter

enum class NovelQuotesSortMode(val storageKey: String) {
    DATE("DATE"),
    TITLE("TITLE"),
    ;

    companion object {
        fun from(storageKey: String?): NovelQuotesSortMode =
            entries.firstOrNull { it.storageKey.equals(storageKey, ignoreCase = true) } ?: DATE
    }
}

object NovelQuotesListOps {

    /** Осиротевшие записи (глава удалена) скрыты во всех представлениях. */
    fun visible(items: List<NovelHighlightWithChapter>): List<NovelHighlightWithChapter> =
        items.filter { it.chapterName != null }

    /** Безрегистрозависимый поиск по тексту цитаты и заметке + опциональный фильтр книги. */
    fun filter(
        items: List<NovelHighlightWithChapter>,
        query: String,
        bookFilter: String?,
    ): List<NovelHighlightWithChapter> {
        var result = items
        if (bookFilter != null) result = result.filter { it.novelTitle == bookFilter }
        val q = query.trim()
        if (q.isNotEmpty()) {
            result = result.filter { item ->
                item.highlight.normalizedText.contains(q, ignoreCase = true) ||
                    item.highlight.note.contains(q, ignoreCase = true)
            }
        }
        return result
    }

    /** DATE — свежие сверху. TITLE — книги A→Я, внутри книги свежие сверху. */
    fun sorted(
        items: List<NovelHighlightWithChapter>,
        mode: NovelQuotesSortMode,
    ): List<NovelHighlightWithChapter> = when (mode) {
        NovelQuotesSortMode.DATE -> items.sortedByDescending { it.highlight.updatedAt }
        NovelQuotesSortMode.TITLE ->
            items
                .groupBy { it.novelTitle }
                .toSortedMap(compareBy<String> { it.lowercase() })
                .flatMap { (_, group) -> group.sortedByDescending { it.highlight.updatedAt } }
    }

    /** Смежные секции уже отсортированного списка (порядок сохраняется). */
    fun sections(sortedItems: List<NovelHighlightWithChapter>): List<Section> {
        val result = mutableListOf<Section>()
        for (item in sortedItems) {
            val last = result.lastOrNull()
            if (last != null && last.title == item.novelTitle) {
                result[result.lastIndex] = last.copy(items = last.items + item)
            } else {
                result += Section(title = item.novelTitle, items = listOf(item))
            }
        }
        return result
    }

    data class Section(
        val title: String,
        val items: List<NovelHighlightWithChapter>,
    )

    fun countsByNovel(items: List<NovelHighlightWithChapter>): Map<String, Int> =
        items.groupingBy { it.novelTitle }.eachCount()
}
