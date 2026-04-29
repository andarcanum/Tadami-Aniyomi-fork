package tachiyomi.domain.source.model

data class SavedSearch(
    val id: Long,
    val source: Long,
    val sourceType: SourceType = SourceType.MANGA,
    val name: String,
    val query: String?,
    val filtersJson: String?,
)
