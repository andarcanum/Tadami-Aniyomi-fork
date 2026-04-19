package eu.kanade.tachiyomi.data.library

import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.delay
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
