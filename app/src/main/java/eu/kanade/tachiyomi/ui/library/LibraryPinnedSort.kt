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
