package eu.kanade.tachiyomi.ui.entries

internal fun mergeNewItemIds(
    existingNewItemIds: Set<Long>,
    addedItemIds: Iterable<Long>,
    clearedItemIds: Iterable<Long> = emptyList(),
): Set<Long> {
    val cleared = clearedItemIds.toSet()
    return buildSet {
        addAll(existingNewItemIds)
        addAll(addedItemIds)
        removeAll(cleared)
    }
}
