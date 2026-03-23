package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlaybackPlayerVisibilityTest {

    @Test
    fun `sanitizeVisiblePlaybackPreferences resets hidden alloha player to auto`() {
        val result = sanitizeVisiblePlaybackPreferences(
            PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.ALLOHA,
                preferredDubbingAlloha = "AniLot",
                preferredQualityAlloha = "480p",
            ),
        )

        result.preferredPlayer shouldBe PlaybackPlayerPreference.AUTO
        result.preferredDubbingAlloha shouldBe "AniLot"
        result.preferredQualityAlloha shouldBe "480p"
    }

    @Test
    fun `hasVisiblePlaybackPreferences ignores alloha-only preferences`() {
        val result = hasVisiblePlaybackPreferences(
            PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.ALLOHA,
                preferredDubbingAlloha = "AniLot",
                preferredQualityAlloha = "480p",
            ),
        )

        result shouldBe false
    }
}
