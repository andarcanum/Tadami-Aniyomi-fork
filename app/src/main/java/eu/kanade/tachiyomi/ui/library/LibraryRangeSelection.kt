package eu.kanade.tachiyomi.ui.library

internal fun <T> resolveLibraryRangeSelectionAdditions(
    selectedItems: List<T>,
    targetItem: T,
    visibleGroups: Iterable<List<T>>,
    itemId: (T) -> Long,
): List<T> {
    val targetId = itemId(targetItem)
    val selectedIds = selectedItems.mapTo(mutableSetOf(), itemId)
    val fallbackSelection = targetItem
        .takeUnless { targetId in selectedIds }
        ?.let(::listOf)
        .orEmpty()

    val visibleItems = visibleGroups.firstOrNull { group ->
        group.any { itemId(it) == targetId }
    } ?: return fallbackSelection

    val visibleIds = visibleItems.mapTo(mutableSetOf(), itemId)
    val lastSelected = selectedItems.asReversed().firstOrNull { itemId(it) in visibleIds }
        ?: return fallbackSelection

    val lastIndex = visibleItems.indexOfFirst { itemId(it) == itemId(lastSelected) }
    val targetIndex = visibleItems.indexOfFirst { itemId(it) == targetId }
    if (lastIndex < 0 || targetIndex < 0 || lastIndex == targetIndex) return emptyList()

    val start = minOf(lastIndex, targetIndex)
    val end = maxOf(lastIndex, targetIndex)
    return visibleItems
        .subList(start, end + 1)
        .filterNot { itemId(it) in selectedIds }
}
