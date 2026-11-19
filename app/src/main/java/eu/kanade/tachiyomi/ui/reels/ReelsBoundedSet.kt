package eu.kanade.tachiyomi.ui.reels

/**
 * Memory-bounded FIFO/LRU set for long-session Reels tracking sets (`watchedCommitted`,
 * `skippedCommitted`, `decidedIds`, etc.).
 *
 * Prevents unbounded RAM growth during hour-long sessions (500–1000+ reels) while preserving fast
 * O(1) membership checks for recently processed items; durable history still lives in SQLite.
 */
internal class ReelsBoundedSet<T>(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) : MutableSet<T> {

    private val backing = LinkedHashSet<T>()

    override val size: Int get() = backing.size

    override fun contains(element: T): Boolean = backing.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean = backing.containsAll(elements)

    override fun isEmpty(): Boolean = backing.isEmpty()

    override fun iterator(): MutableIterator<T> = backing.iterator()

    override fun add(element: T): Boolean {
        val added = backing.add(element)
        if (added) trim()
        return added
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val changed = backing.addAll(elements)
        if (changed) trim()
        return changed
    }

    override fun remove(element: T): Boolean = backing.remove(element)

    override fun removeAll(elements: Collection<T>): Boolean = backing.removeAll(elements.toSet())

    override fun retainAll(elements: Collection<T>): Boolean = backing.retainAll(elements.toSet())

    override fun clear() = backing.clear()

    private fun trim() {
        while (backing.size > maxSize) {
            val eldest = backing.firstOrNull() ?: break
            backing.remove(eldest)
        }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 2_000
    }
}
