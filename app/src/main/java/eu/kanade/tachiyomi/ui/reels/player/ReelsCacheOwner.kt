package eu.kanade.tachiyomi.ui.reels.player

/**
 * Process-wide holder for the reels disk cache.
 *
 * A player is created from a UI effect while head-prewarm runs on IO, so the first access
 * can race itself; a cache clear can also race the first initialization. Both paths run
 * through this holder: creation is serialized, and a clear only touches the directory
 * while no instance exists. The cache is intentionally process-scoped (never released).
 */
internal class ReelsCacheOwner<T : Any> {
    private val lock = Any()
    private var value: T? = null

    fun getOrCreate(factory: () -> T): T = synchronized(lock) {
        value ?: factory().also { value = it }
    }

    /** Runs [block] only while no cache was ever initialized; null once a cache exists. */
    fun <R : Any> clearIfAbsent(block: () -> R): R? = synchronized(lock) {
        if (value == null) block() else null
    }

    fun current(): T? = synchronized(lock) { value }
}
