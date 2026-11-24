package eu.kanade.tachiyomi.ui.player.settings

import eu.kanade.tachiyomi.ui.player.Anime4KShaderPreset
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.DecoderPreset
import eu.kanade.tachiyomi.ui.player.HdrToneMappingMode
import eu.kanade.tachiyomi.ui.player.MotionInterpolationMode
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class DecoderPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun tryHWDecoding() = preferenceStore.getBoolean("pref_try_hwdec", true)
    fun useZeroCopyHwdec() = preferenceStore.getBoolean("pref_use_zero_copy_hwdec", false)
    fun decoderPreset() = preferenceStore.getEnum("pref_decoder_preset", DecoderPreset.Device)
    fun gpuNext() = preferenceStore.getBoolean("pref_gpu_next", false)
    fun videoDebanding() = preferenceStore.getEnum("pref_video_debanding", Debanding.None)
    fun anime4kShaderPreset() = preferenceStore.getEnum("pref_anime4k_shader_preset", Anime4KShaderPreset.Off)
    fun motionInterpolationMode() = preferenceStore.getEnum(
        "pref_motion_interpolation_mode",
        MotionInterpolationMode.Off,
    )
    fun useYUV420P() = preferenceStore.getBoolean("use_yuv420p", false)
    fun hdrToneMappingMode() = preferenceStore.getEnum(
        "pref_hdr_tone_mapping_mode",
        HdrToneMappingMode.Auto,
    )
    fun matchDisplayRefreshRate() = preferenceStore.getBoolean("pref_match_display_refresh_rate", false)

    // Non-preferences

    fun brightnessFilter() = preferenceStore.getInt("pref_player_filter_brightness")
    fun saturationFilter() = preferenceStore.getInt("pref_player_filter_saturation")
    fun contrastFilter() = preferenceStore.getInt("pref_player_filter_contrast")
    fun gammaFilter() = preferenceStore.getInt("pref_player_filter_gamma")
    fun hueFilter() = preferenceStore.getInt("pref_player_filter_hue")
}
