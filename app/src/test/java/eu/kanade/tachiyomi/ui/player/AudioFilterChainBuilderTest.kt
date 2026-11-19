package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.settings.AudioChannels
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AudioFilterChainBuilderTest {

    @Test
    fun `standard channel mode without normalization produces empty audio filter`() {
        AudioFilterChainBuilder.build(
            audioChannels = AudioChannels.AutoSafe,
            normalizeAudio = false,
        ) shouldBe AudioMpvConfig(
            audioChannelsValue = "auto-safe",
            audioFilterValue = "",
        )
    }

    @Test
    fun `normalization adds dynaudnorm filter while preserving channel layout`() {
        AudioFilterChainBuilder.build(
            audioChannels = AudioChannels.Stereo,
            normalizeAudio = true,
        ) shouldBe AudioMpvConfig(
            audioChannelsValue = "stereo",
            audioFilterValue = AudioFilterChainBuilder.DYNAUDNORM_FILTER,
        )
    }

    @Test
    fun `reverse stereo and normalization compose cleanly without overwriting each other`() {
        AudioFilterChainBuilder.build(
            audioChannels = AudioChannels.ReverseStereo,
            normalizeAudio = true,
        ) shouldBe AudioMpvConfig(
            audioChannelsValue = "auto-safe",
            audioFilterValue = "pan=[stereo|c0=c1|c1=c0],${AudioFilterChainBuilder.DYNAUDNORM_FILTER}",
        )
    }
}
