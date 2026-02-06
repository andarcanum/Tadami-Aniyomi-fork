package tachiyomi.domain.items.novelchapter.model

data class NovelChapterUpdate(
    val id: Long,
    val novelId: Long? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    val lastPageRead: Long? = null,
    val dateFetch: Long? = null,
    val sourceOrder: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val dateUpload: Long? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val version: Long? = null,
)

fun NovelChapter.toNovelChapterUpdate(): NovelChapterUpdate {
    return NovelChapterUpdate(
        id,
        novelId,
        read,
        bookmark,
        lastPageRead,
        dateFetch,
        sourceOrder,
        url,
        name,
        dateUpload,
        chapterNumber,
        scanlator,
        version,
    )
}