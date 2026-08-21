package tachiyomi.domain.book.novel.model

/**
 * Пункт панели хайлайтов/цитат: выделение, соединённое с тайтлом новеллы, именем и порядком главы.
 * [chapterName] равен null только для осиротевших записей (глава удалена) — они скрываются в UI.
 */
data class NovelHighlightWithChapter(
    val highlight: NovelHighlight,
    val novelTitle: String,
    val chapterName: String?,
    val chapterSourceOrder: Long,
)
