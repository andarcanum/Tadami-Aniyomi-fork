package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal object NovelPluginFilters {
    fun decodeFilterList(
        payload: String?,
        filterMapper: NovelPluginFilterMapper,
    ): NovelFilterList {
        if (payload.isNullOrBlank() || payload == "null") return NovelFilterList()
        return runCatching { filterMapper.toFilterList(payload) }
            .getOrDefault(NovelFilterList())
    }

    fun toFilterValuesWithDefaults(
        filters: NovelFilterList,
        cachedFiltersPayload: String?,
        filterMapper: NovelPluginFilterMapper,
    ): JsonObject {
        val explicit = filterMapper.toFilterValues(filters)
        if (explicit.isNotEmpty()) return explicit

        val fallbackList = decodeFilterList(cachedFiltersPayload, filterMapper)
        if (fallbackList.isEmpty()) return buildJsonObject { }
        return filterMapper.toFilterValues(fallbackList)
    }
}
