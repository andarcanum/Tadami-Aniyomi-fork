package tachiyomi.data.translation

import kotlinx.coroutines.sync.Mutex
import tachiyomi.novel.data.NovelDatabase
import java.util.WeakHashMap

/** Shared in-process writer gate for every repository over the same authoritative database object. */
internal object NovelDatabaseMutationGate {
    private val mutexes = WeakHashMap<NovelDatabase, Mutex>()

    fun mutexFor(database: NovelDatabase): Mutex = synchronized(mutexes) {
        mutexes.getOrPut(database) { Mutex() }
    }
}
