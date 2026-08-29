package eu.kanade.tachiyomi.data.library

import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.core.common.preference.Preference

class LibraryUpdatePacingPolicy(
    private val timeoutPreference: Preference<Int>,
    private val selectedSourceKeysPreference: Preference<Set<String>>,
) {

    constructor(uiPreferences: UiPreferences) : this(
        timeoutPreference = uiPreferences.libraryUpdatePacingTimeoutSeconds(),
        selectedSourceKeysPreference = uiPreferences.libraryUpdatePacingSourceKeys(),
    )

    fun timeoutSeconds(): Int = timeoutPreference.get().coerceAtLeast(0)

    fun timeoutMillis(): Long = timeoutSeconds().toLong() * 1_000L

    fun selectedSourceKeys(): Set<String> = selectedSourceKeysPreference.get()

    fun sourceKey(mediaTag: String, sourceId: Long): String = "$mediaTag:$sourceId"

    fun shouldPace(mediaTag: String, sourceId: Long): Boolean {
        val timeout = timeoutSeconds()
        if (timeout <= 0) return false
        return sourceKey(mediaTag, sourceId) in selectedSourceKeys()
    }

    suspend fun delayAfterUpdate(mediaTag: String, sourceId: Long, shouldDelay: Boolean) {
        if (!shouldDelay || !shouldPace(mediaTag, sourceId)) {
            return
        }

        delay(timeoutMillis())
    }

    companion object {
        const val MEDIA_ANIME = "anime"
        const val MEDIA_MANGA = "manga"
        const val MEDIA_NOVEL = "novel"
    }
}

/**
 * Processes [entries] of one source sequentially, acquiring a [semaphore] permit only around
 * the actual work ([process]). The pacing delay runs OUTSIDE the permit, so a paced source
 * sleeps without holding one of the job's limited slots.
 *
 * [process] returns false for an entry that was skipped (e.g. no longer in the library);
 * skipped entries and the last entry never trigger pacing.
 */
internal suspend fun <T> processEntriesWithPacing(
    entries: List<T>,
    semaphore: Semaphore,
    process: suspend (T) -> Boolean,
    paceAfter: suspend () -> Unit,
) {
    entries.forEachIndexed { index, entry ->
        val processed = semaphore.withPermit { process(entry) }
        if (processed && index != entries.lastIndex) {
            paceAfter()
        }
    }
}
