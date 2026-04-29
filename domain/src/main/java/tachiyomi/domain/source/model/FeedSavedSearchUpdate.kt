package tachiyomi.domain.source.model

data class FeedSavedSearchUpdate(
    val id: Long,
    val source: Long? = null,
    val savedSearch: Long? = null,
    val global: Boolean? = null,
    val feedOrder: Long? = null,
)
