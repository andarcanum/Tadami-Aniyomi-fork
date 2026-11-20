package eu.kanade.tachiyomi.ui.player

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import `is`.xyz.mpv.MPVLib
import tachiyomi.i18n.aniyomi.AYMR

data class VideoFilterSnapshot(
    val brightness: Int,
    val saturation: Int,
    val contrast: Int,
    val gamma: Int,
    val hue: Int,
)

enum class VideoFilterPreset(
    val titleRes: StringResource,
    val snapshot: VideoFilterSnapshot,
) {
    Standard(
        titleRes = AYMR.strings.player_video_filter_preset_standard,
        snapshot = VideoFilterSnapshot(brightness = 0, saturation = 0, contrast = 0, gamma = 0, hue = 0),
    ),
    AnimeVivid(
        titleRes = AYMR.strings.player_video_filter_preset_vivid,
        snapshot = VideoFilterSnapshot(brightness = 2, saturation = 18, contrast = 8, gamma = 0, hue = 0),
    ),
    Cinema(
        titleRes = AYMR.strings.player_video_filter_preset_cinema,
        snapshot = VideoFilterSnapshot(brightness = -2, saturation = -6, contrast = 10, gamma = 4, hue = 0),
    ),
    NightSoft(
        titleRes = AYMR.strings.player_video_filter_preset_night,
        snapshot = VideoFilterSnapshot(brightness = -12, saturation = -10, contrast = -6, gamma = -8, hue = 4),
    ),
    ;

    fun matches(current: VideoFilterSnapshot): Boolean = current == snapshot

    fun applyTo(
        decoderPreferences: DecoderPreferences,
        applyLiveToMpv: Boolean = true,
    ) {
        val values = mapOf(
            VideoFilters.BRIGHTNESS to snapshot.brightness,
            VideoFilters.SATURATION to snapshot.saturation,
            VideoFilters.CONTRAST to snapshot.contrast,
            VideoFilters.GAMMA to snapshot.gamma,
            VideoFilters.HUE to snapshot.hue,
        )
        values.forEach { (filter, value) ->
            filter.preference(decoderPreferences).set(value)
            if (applyLiveToMpv) {
                runCatching { MPVLib.setPropertyInt(filter.mpvProperty, value) }
            }
        }
    }

    companion object {
        fun snapshotFrom(decoderPreferences: DecoderPreferences): VideoFilterSnapshot {
            return VideoFilterSnapshot(
                brightness = decoderPreferences.brightnessFilter().get(),
                saturation = decoderPreferences.saturationFilter().get(),
                contrast = decoderPreferences.contrastFilter().get(),
                gamma = decoderPreferences.gammaFilter().get(),
                hue = decoderPreferences.hueFilter().get(),
            )
        }

        fun resolveActive(snapshot: VideoFilterSnapshot): VideoFilterPreset? {
            return entries.firstOrNull { it.matches(snapshot) }
        }
    }
}
