package eu.kanade.tachiyomi.ui.player

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class HdrToneMappingMode(
    val titleRes: StringResource,
    private val mpvToneMapping: String,
    private val computePeak: String,
) {
    Auto(
        titleRes = AYMR.strings.pref_hdr_tone_mapping_auto,
        mpvToneMapping = "auto",
        computePeak = "auto",
    ),
    Spline(
        titleRes = AYMR.strings.pref_hdr_tone_mapping_spline,
        mpvToneMapping = "spline",
        computePeak = "auto",
    ),
    Bt2446a(
        titleRes = AYMR.strings.pref_hdr_tone_mapping_bt2446a,
        mpvToneMapping = "bt.2446a",
        computePeak = "yes",
    ),
    Reinhard(
        titleRes = AYMR.strings.pref_hdr_tone_mapping_reinhard,
        mpvToneMapping = "reinhard",
        computePeak = "no",
    ),
    Clip(
        titleRes = AYMR.strings.pref_hdr_tone_mapping_clip,
        mpvToneMapping = "clip",
        computePeak = "no",
    ),
    ;

    fun mpvOptions(gpuNextEnabled: Boolean): Map<String, String> {
        // `spline` is provided by libplacebo (gpu-next); fall back cleanly to `bt.2446a` on legacy `gpu` vo.
        val resolvedToneMapping = if (this == Spline && !gpuNextEnabled) {
            "bt.2446a"
        } else {
            mpvToneMapping
        }
        return mapOf(
            "tone-mapping" to resolvedToneMapping,
            "hdr-compute-peak" to computePeak,
        )
    }
}
