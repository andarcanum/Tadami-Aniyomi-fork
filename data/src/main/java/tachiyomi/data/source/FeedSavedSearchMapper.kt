package tachiyomi.data.source

import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SourceType

object FeedSavedSearchMapper {
    fun map(
        id: Long,
        source: Long,
        sourceType: Long,
        savedSearch: Long?,
        global: Boolean,
        feedOrder: Long,
    ): FeedSavedSearch {
        return FeedSavedSearch(
            id = id,
            source = source,
            sourceType = SourceType.fromId(sourceType),
            savedSearch = savedSearch,
            global = global,
            feedOrder = feedOrder,
        )
    }
}
