package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun

/** Bounded process-only delivery/checkpoints when the user explicitly disabled disk caching. */
internal class TranslationSessionStore(private val byteLimit: Long = 32L * 1024 * 1024) {
    private data class Key(val id: Long, val generation: Long)
    private data class Value(
        val chapterId: Long,
        val profileFingerprint: String?,
        val fragments: List<TranslationFragment>,
        val result: GeminiTranslationCacheEntry? = null,
    ) {
        val bytes: Long get() = fragments.sumOf {
            (it.sourceText.length.toLong() + (it.translatedText?.length ?: 0)) * 2
        } +
            (result?.translatedByIndex?.values?.sumOf { it.length.toLong() * 2 } ?: 0)
    }
    private val values = LinkedHashMap<Key, Value>(16, 0.75f, true)

    @Synchronized
    fun checkpoint(run: TranslationRun, fragments: List<TranslationFragment>) {
        val key = Key(run.id, run.generation)
        val value = Value(run.chapterId, run.profileFingerprint, fragments.toList(), values[key]?.result)
        require(value.bytes <= byteLimit) { "Session translation exceeds the memory budget; enable persistent storage" }
        evictCompletedToFit(keep = key, replacement = value)
        require(projectedBytes(key, value) <= byteLimit) {
            "Session translation exceeds the memory budget; enable persistent storage"
        }
        values[key] = value
    }

    @Synchronized
    fun fragments(run: TranslationRun): List<TranslationFragment>? = values[Key(run.id, run.generation)]?.fragments

    @Synchronized
    fun reusable(chapterId: Long, profileFingerprint: String?): List<TranslationFragment>? =
        values.values.lastOrNull { it.chapterId == chapterId && it.profileFingerprint == profileFingerprint }?.fragments

    @Synchronized
    fun publish(run: TranslationRun, entry: GeminiTranslationCacheEntry) {
        require(entry.isTranslationComplete) { "Cannot publish an incomplete session translation" }
        val key = Key(run.id, run.generation)
        val old = values[key] ?: Value(run.chapterId, run.profileFingerprint, emptyList())
        var value = old.copy(result = entry)
        evictCompletedToFit(keep = key, replacement = value)
        if (projectedBytes(key, value) > byteLimit && value.fragments.isNotEmpty()) {
            value = value.copy(fragments = emptyList())
            evictCompletedToFit(keep = key, replacement = value)
        }
        require(projectedBytes(key, value) <= byteLimit) { "Session result exceeds the memory budget" }
        values[key] = value
    }

    @Synchronized
    fun result(chapterId: Long): GeminiTranslationCacheEntry? =
        values.values.lastOrNull { it.chapterId == chapterId && it.result != null }?.result

    @Synchronized
    fun clearChapter(chapterId: Long) {
        values.entries.removeAll { it.value.chapterId == chapterId }
    }

    @Synchronized
    fun clearAll() {
        values.clear()
    }

    private fun projectedBytes(key: Key, replacement: Value): Long {
        var total = replacement.bytes
        for ((existingKey, existingValue) in values) {
            if (existingKey != key) {
                total += existingValue.bytes
            }
        }
        return total
    }

    private fun evictCompletedToFit(keep: Key, replacement: Value) {
        var projected = projectedBytes(keep, replacement)
        if (projected <= byteLimit) return
        val iterator = values.entries.iterator()
        while (projected > byteLimit && iterator.hasNext()) {
            val (entryKey, entryValue) = iterator.next()
            if (entryKey != keep && entryValue.result != null) {
                projected -= entryValue.bytes
                iterator.remove()
            }
        }
    }
}

internal object TranslationSessionResults {
    val store = TranslationSessionStore()
}
