package eu.kanade.tachiyomi.data.library

import eu.kanade.domain.ui.UiPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class LibraryUpdatePacingPolicyTest {

    @Test
    fun `zero timeout disables pacing`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val timeoutPref = prefs.libraryUpdatePacingTimeoutSeconds()
        val selectedKeysPref = prefs.libraryUpdatePacingSourceKeys()
        val policy = LibraryUpdatePacingPolicy(timeoutPref, selectedKeysPref)

        timeoutPref.set(0)
        selectedKeysPref.set(setOf(policy.sourceKey(LibraryUpdatePacingPolicy.MEDIA_ANIME, 1L)))

        policy.shouldPace(LibraryUpdatePacingPolicy.MEDIA_ANIME, 1L) shouldBe false
        policy.timeoutMillis() shouldBe 0L
    }

    @Test
    fun `selected source key enables pacing`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val timeoutPref = prefs.libraryUpdatePacingTimeoutSeconds()
        val selectedKeysPref = prefs.libraryUpdatePacingSourceKeys()
        val policy = LibraryUpdatePacingPolicy(timeoutPref, selectedKeysPref)
        val sourceKey = policy.sourceKey(LibraryUpdatePacingPolicy.MEDIA_MANGA, 42L)

        timeoutPref.set(12)
        selectedKeysPref.set(setOf(sourceKey))

        policy.shouldPace(LibraryUpdatePacingPolicy.MEDIA_MANGA, 42L) shouldBe true
        policy.timeoutMillis() shouldBe 12_000L
    }

    @Test
    fun `unselected source key does not pace`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val timeoutPref = prefs.libraryUpdatePacingTimeoutSeconds()
        val selectedKeysPref = prefs.libraryUpdatePacingSourceKeys()
        val policy = LibraryUpdatePacingPolicy(timeoutPref, selectedKeysPref)

        timeoutPref.set(7)
        selectedKeysPref.set(setOf(policy.sourceKey(LibraryUpdatePacingPolicy.MEDIA_NOVEL, 99L)))

        policy.shouldPace(LibraryUpdatePacingPolicy.MEDIA_ANIME, 99L) shouldBe false
    }

    @Test
    fun `media aware source keys do not collide`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val policy = LibraryUpdatePacingPolicy(
            prefs.libraryUpdatePacingTimeoutSeconds(),
            prefs.libraryUpdatePacingSourceKeys(),
        )

        policy.sourceKey(LibraryUpdatePacingPolicy.MEDIA_ANIME, 5L) shouldBe "anime:5"
        policy.sourceKey(LibraryUpdatePacingPolicy.MEDIA_MANGA, 5L) shouldBe "manga:5"
        policy.sourceKey(LibraryUpdatePacingPolicy.MEDIA_NOVEL, 5L) shouldBe "novel:5"
    }

    @Test
    fun `delay after update only advances for selected source`() = runTest {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val timeoutPref = prefs.libraryUpdatePacingTimeoutSeconds()
        val selectedKeysPref = prefs.libraryUpdatePacingSourceKeys()
        val policy = LibraryUpdatePacingPolicy(timeoutPref, selectedKeysPref)

        timeoutPref.set(3)
        selectedKeysPref.set(setOf(policy.sourceKey(LibraryUpdatePacingPolicy.MEDIA_ANIME, 7L)))

        policy.delayAfterUpdate(
            mediaTag = LibraryUpdatePacingPolicy.MEDIA_ANIME,
            sourceId = 7L,
            shouldDelay = true,
        )

        testScheduler.currentTime shouldBe 3_000L
    }

    @Test
    fun `delay after update skips unselected source`() = runTest {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val timeoutPref = prefs.libraryUpdatePacingTimeoutSeconds()
        val selectedKeysPref = prefs.libraryUpdatePacingSourceKeys()
        val policy = LibraryUpdatePacingPolicy(timeoutPref, selectedKeysPref)

        timeoutPref.set(3)
        selectedKeysPref.set(setOf(policy.sourceKey(LibraryUpdatePacingPolicy.MEDIA_MANGA, 7L)))

        policy.delayAfterUpdate(
            mediaTag = LibraryUpdatePacingPolicy.MEDIA_ANIME,
            sourceId = 7L,
            shouldDelay = true,
        )

        testScheduler.currentTime shouldBe 0L
    }
}
