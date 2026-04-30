package eu.kanade.domain.track.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TrackPreferencesNovelTest {

    @Test
    fun `novel preferences have stable defaults`() {
        val prefs = TrackPreferences(InMemoryPreferenceStore())

        prefs.novelUpdatesUseCustomListMapping().get() shouldBe false
        prefs.novelUpdatesCustomListMapping().get() shouldBe "{}"
        prefs.novelUpdatesCachedLists().get() shouldBe "[]"
        prefs.novelUpdatesLastListRefresh().get() shouldBe 0L
    }

    @Test
    fun `tracker pull autosync preference defaults to enabled`() {
        val prefs = TrackPreferences(InMemoryPreferenceStore())

        prefs.autoSyncProgressFromTracker().get() shouldBe true
    }
}
