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
        val categoryIds = entryCategoryIds[entryId].orEmpty()
        // Entry is visible if ALL its categories are NOT in the hidden set
        // System category (id=0) entries are always visible
        categoryIds.all { it == 0L || it !in hiddenCategoryIds }
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
        val categoryIds = entryCategoryIds[keySelector(item)].orEmpty()
        categoryIds.all { it == 0L || it !in hiddenCategoryIds }
    }
}
