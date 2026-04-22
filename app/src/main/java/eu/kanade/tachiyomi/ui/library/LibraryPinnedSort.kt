package eu.kanade.tachiyomi.ui.library

import kotlin.random.Random

internal fun <T> List<T>.sortPinnedFirst(
    isPinned: (T) -> Boolean,
    comparator: Comparator<T>,
    randomSeed: Int? = null,
): List<T> {
    if (isEmpty()) return this

    val pinnedItems = filter(isPinned)
    val normalItems = filterNot(isPinned)

    return if (randomSeed != null) {
        pinnedItems.shuffled(Random(randomSeed)) + normalItems.shuffled(Random(randomSeed))
    } else {
        pinnedItems.sortedWith(comparator) + normalItems.sortedWith(comparator)
    }
}

internal fun <T> List<T>.sortPinnedSeriesFirst(
    isPinned: (T) -> Boolean,
    isSeries: (T) -> Boolean,
    comparator: Comparator<T>,
    randomSeed: Int? = null,
): List<T> {
    if (isEmpty()) return this

    val pinnedItems = filter(isPinned)
    val nonPinnedItems = filterNot(isPinned)
    val nonPinnedSeries = nonPinnedItems.filter(isSeries)
    val nonPinnedSingles = nonPinnedItems.filterNot(isSeries)

    return if (randomSeed != null) {
        pinnedItems.shuffled(Random(randomSeed)) +
            nonPinnedSeries.shuffled(Random(randomSeed)) +
            nonPinnedSingles.shuffled(Random(randomSeed))
    } else {
        pinnedItems.sortedWith(comparator) +
            nonPinnedSeries.sortedWith(comparator) +
            nonPinnedSingles.sortedWith(comparator)
    }
}
