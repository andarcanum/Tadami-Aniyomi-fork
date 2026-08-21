package tachiyomi.domain.book.novel.model

data class NovelHighlight(
    val id: Long,
    val novelId: Long,
    val chapterId: Long,
    val blockIndex: Int,
    val charStart: Int,
    val charEndExclusive: Int,
    val normalizedText: String,
    val colorArgb: Long,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 1-based page captured at creation time in the page reader; 0/0 = position unknown. */
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
)
