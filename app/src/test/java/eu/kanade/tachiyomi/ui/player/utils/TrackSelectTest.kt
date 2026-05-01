package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TrackSelectTest {

    @Test
    fun `exact subtitle match wins when enabled`() {
        val select = createTrackSelect(exactMatch = true)
        val tracks = listOf(
            VideoTrack(1, "English (Signs)", "en"),
            VideoTrack(2, "English", "en"),
        )

        select.getPreferredTrackIndex(tracks) shouldBe tracks[1]
    }

    @Test
    fun `exact subtitle match falls back to the first locale match when disabled`() {
        val select = createTrackSelect(exactMatch = false)
        val tracks = listOf(
            VideoTrack(1, "English (Signs)", "en"),
            VideoTrack(2, "English", "en"),
        )

        select.getPreferredTrackIndex(tracks) shouldBe tracks[0]
    }

    @Test
    fun `subtitle blacklist filters unwanted tracks before matching`() {
        val select = createTrackSelect(blacklist = "Signs")
        val tracks = listOf(
            VideoTrack(1, "English (Signs)", "en"),
            VideoTrack(2, "English", "en"),
        )

        select.getPreferredTrackIndex(tracks) shouldBe tracks[1]
    }

    private fun createTrackSelect(
        exactMatch: Boolean = true,
        whitelist: String = "",
        blacklist: String = "",
    ): TrackSelect {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "pref_subtitle_lang",
                    data = "en",
                    defaultValue = "",
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "pref_subtitle_exact_match",
                    data = exactMatch,
                    defaultValue = true,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "pref_subtitle_whitelist",
                    data = whitelist,
                    defaultValue = "",
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "pref_subtitle_blacklist",
                    data = blacklist,
                    defaultValue = "",
                ),
            ),
        )

        return TrackSelect(
            subtitlePreferences = SubtitlePreferences(store),
            audioPreferences = AudioPreferences(store),
        )
    }
}
