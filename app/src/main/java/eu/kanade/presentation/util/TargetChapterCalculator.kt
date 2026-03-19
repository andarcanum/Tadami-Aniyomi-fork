package eu.kanade.presentation.util

object TargetChapterCalculator {

    fun <T> calculate(
        items: List<T>,
        isRead: (T) -> Boolean,
    ): Int {
        if (items.isEmpty()) return 0

        val firstUnreadIndex = items.indexOfFirst { !isRead(it) }

        return if (firstUnreadIndex >= 0) {
            firstUnreadIndex
        } else {
            items.lastIndex
        }
    }
}
