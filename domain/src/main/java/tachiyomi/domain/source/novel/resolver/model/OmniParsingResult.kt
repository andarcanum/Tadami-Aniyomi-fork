package tachiyomi.domain.source.novel.resolver.model

data class OmniParsingResult(
    val title: String,
    val author: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val chapters: List<OmniChapter>
)

data class OmniChapter(
    val name: String,
    val url: String,
    val dateUpload: Long = 0,
    val chapterNumber: Float = 0f
)
