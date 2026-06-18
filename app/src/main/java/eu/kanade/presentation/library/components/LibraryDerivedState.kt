package eu.kanade.presentation.library.components

internal inline fun <T> List<T>.containsAtLeastMatches(
    requiredCount: Int,
    predicate: (T) -> Boolean,
): Boolean {
    if (requiredCount <= 0) return true

    var matches = 0
    for (item in this) {
        if (predicate(item) && ++matches >= requiredCount) {
            return true
        }
    }
    return false
}

internal inline fun <T> List<T>.idsToHashSet(
    crossinline idSelector: (T) -> Long,
): Set<Long> {
    return buildSet(capacity = size) {
        this@idsToHashSet.forEach { add(idSelector(it)) }
    }
}
