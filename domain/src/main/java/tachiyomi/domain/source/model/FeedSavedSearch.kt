package tachiyomi.domain.source.model

data class FeedSavedSearch(
    val id: Long,
    val source: Long,
    val savedSearch: Long?,
    val global: Boolean,
    val feedOrder: Long,
)
