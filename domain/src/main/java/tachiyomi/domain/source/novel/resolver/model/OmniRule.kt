package tachiyomi.domain.source.novel.resolver.model

enum class PaginationType { NONE, AJAX_SELECT, NEXT_LINK }

data class OmniRule(
    val domain: String,
    val titleSelector: String?,
    val authorSelector: String?,
    val coverSelector: String?,
    val descriptionSelector: String?,
    val chapterListSelector: String,
    val chapterNameSelector: String?,
    val chapterUrlSelector: String?,
    val paginationSelector: String?,
    val paginationType: PaginationType,
    val contentSelector: String,
    val removeSelectors: String?
)
