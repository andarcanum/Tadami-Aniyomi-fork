package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlaybackPlayerVisibilityTest {

    @Test
    fun `sanitizeVisiblePlaybackPreferences keeps parlorate preferences visible`() {
        val result = sanitizeVisiblePlaybackPreferences(
            PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.PARLORATE,
                preferredDubbingParlorate = "AniLot",
                preferredQualityParlorate = "480p",
            ),
        )

        result.preferredPlayer shouldBe PlaybackPlayerPreference.PARLORATE
        result.preferredDubbingParlorate shouldBe "AniLot"
        result.preferredQualityParlorate shouldBe "480p"
    }

    @Test
    fun `hasVisiblePlaybackPreferences includes parlorate-only preferences`() {
        val result = hasVisiblePlaybackPreferences(
            PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.PARLORATE,
                preferredDubbingParlorate = "AniLot",
                preferredQualityParlorate = "480p",
            ),
        )

        result shouldBe true
    }
}
