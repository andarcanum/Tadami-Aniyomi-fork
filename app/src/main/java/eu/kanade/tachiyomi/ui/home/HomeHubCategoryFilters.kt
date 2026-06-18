package eu.kanade.tachiyomi.ui.home

/**
 * Filters a list of entry IDs by removing entries that belong to categories
 * hidden from Home Hub.
 *
 * @param entryIds The list of entry IDs to filter (history or library items).
 * @param entryCategoryIds A map from entry ID to list of category IDs it belongs to.
 * @param hiddenCategoryIds Set of category IDs that are hidden from Home Hub.
 * @return Filtered list containing only entries whose categories are visible in Home Hub.
 */
internal fun filterHomeHubEntries(
    entryIds: List<Long>,
    entryCategoryIds: Map<Long, List<Long>>,
    hiddenCategoryIds: Set<Long>,
): List<Long> {
    if (hiddenCategoryIds.isEmpty()) return entryIds

    return entryIds.filter { entryId ->
        isHomeHubEntryVisible(
            categoryIds = entryCategoryIds[entryId],
            hiddenCategoryIds = hiddenCategoryIds,
        )
    }
}

/**
 * Filters a list of items by a key extractor and category visibility.
 *
 * @param items The list of items to filter.
 * @param keySelector Function to extract the entry ID from an item.
 * @param entryCategoryIds A map from entry ID to list of category IDs.
 * @param hiddenCategoryIds Set of category IDs hidden from Home Hub.
 * @return Filtered list of items.
 */
internal fun <T : Any> filterHomeHubEntriesBy(
    items: List<T>,
    keySelector: (T) -> Long,
    entryCategoryIds: Map<Long, List<Long>>,
    hiddenCategoryIds: Set<Long>,
): List<T> {
    if (hiddenCategoryIds.isEmpty()) return items

    return items.filter { item ->
        isHomeHubEntryVisible(
            categoryIds = entryCategoryIds[keySelector(item)],
            hiddenCategoryIds = hiddenCategoryIds,
        )
    }
}

internal fun <T : Any> filterHomeHubEntriesByDistinct(
    items: List<T>,
    keySelector: (T) -> Long,
    entryCategoryIds: Map<Long, List<Long>>,
    hiddenCategoryIds: Set<Long>,
): List<T> {
    if (items.isEmpty()) return items

    val seenEntryIds = HashSet<Long>(items.size)
    val result = ArrayList<T>(items.size)
    for (item in items) {
        val entryId = keySelector(item)
        if (entryId !in seenEntryIds && isHomeHubEntryVisible(entryCategoryIds[entryId], hiddenCategoryIds)) {
            seenEntryIds += entryId
            result += item
        }
    }
    return result
}

internal inline fun <T : Any> hiddenHomeHubCategoryIds(
    categories: List<T>,
    crossinline isHiddenFromHomeHub: (T) -> Boolean,
    crossinline idSelector: (T) -> Long,
): Set<Long> {
    if (categories.isEmpty()) return emptySet()

    return buildSet(capacity = categories.size) {
        categories.forEach { category ->
            if (isHiddenFromHomeHub(category)) {
                add(idSelector(category))
            }
        }
    }
}

internal fun <T : Any> homeHubCategoryIdsByEntryId(
    items: List<T>,
    entryIdSelector: (T) -> Long,
    categoryIdSelector: (T) -> Long,
): Map<Long, List<Long>> {
    if (items.isEmpty()) return emptyMap()

    val result = HashMap<Long, MutableList<Long>>(items.size)
    items.forEach { item ->
        result.getOrPut(entryIdSelector(item)) { ArrayList(1) } += categoryIdSelector(item)
    }
    return result
}

internal inline fun <T : Any> takeHomeHubHistoryExcluding(
    items: List<T>,
    limit: Int,
    excludedEntryId: Long?,
    crossinline entryIdSelector: (T) -> Long,
): List<T> {
    if (limit <= 0 || items.isEmpty()) return emptyList()

    val result = ArrayList<T>(limit)
    for (item in items) {
        if (excludedEntryId != null && entryIdSelector(item) == excludedEntryId) {
            continue
        }

        result += item
        if (result.size == limit) break
    }
    return result
}

private fun isHomeHubEntryVisible(
    categoryIds: List<Long>?,
    hiddenCategoryIds: Set<Long>,
): Boolean {
    if (categoryIds.isNullOrEmpty()) return true

    return categoryIds.all { categoryId ->
        categoryId == 0L || categoryId !in hiddenCategoryIds
    }
}
