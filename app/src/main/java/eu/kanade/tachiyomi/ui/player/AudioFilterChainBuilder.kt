package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.settings.AudioChannels
import `is`.xyz.mpv.MPVLib

internal data class AudioMpvConfig(
    val audioChannelsValue: String,
    val audioFilterValue: String,
)

internal object AudioFilterChainBuilder {
    const val DYNAUDNORM_FILTER = "lavfi=[dynaudnorm=f=150:g=15:p=0.9]"

    fun build(
        audioChannels: AudioChannels,
        normalizeAudio: Boolean,
    ): AudioMpvConfig {
        val filters = mutableListOf<String>()
        val channelsValue = if (audioChannels == AudioChannels.ReverseStereo) {
            filters.add(audioChannels.value)
            AudioChannels.AutoSafe.value
        } else {
            audioChannels.value
        }

        if (normalizeAudio) {
            filters.add(DYNAUDNORM_FILTER)
        }

        return AudioMpvConfig(
            audioChannelsValue = channelsValue,
            audioFilterValue = filters.joinToString(","),
        )
    }

    fun applyOptions(
        audioChannels: AudioChannels,
        normalizeAudio: Boolean,
    ) {
        val config = build(audioChannels, normalizeAudio)
        MPVLib.setOptionString("audio-channels", config.audioChannelsValue)
        if (config.audioFilterValue.isNotEmpty()) {
            MPVLib.setOptionString("af", config.audioFilterValue)
        }
    }

    fun applyProperties(
        audioChannels: AudioChannels,
        normalizeAudio: Boolean,
    ) {
        val config = build(audioChannels, normalizeAudio)
        MPVLib.setPropertyString("audio-channels", config.audioChannelsValue)
        MPVLib.setPropertyString("af", config.audioFilterValue)
    }
}
