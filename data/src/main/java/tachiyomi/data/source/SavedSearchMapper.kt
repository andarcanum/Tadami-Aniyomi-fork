package tachiyomi.data.source

import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType

object SavedSearchMapper {
    fun map(
        id: Long,
        source: Long,
        sourceType: Long,
        name: String,
        query: String?,
        filtersJson: String?,
    ): SavedSearch {
        return SavedSearch(
            id = id,
            source = source,
            sourceType = SourceType.fromId(sourceType),
            name = name,
            query = query,
            filtersJson = filtersJson,
        )
    }
}
