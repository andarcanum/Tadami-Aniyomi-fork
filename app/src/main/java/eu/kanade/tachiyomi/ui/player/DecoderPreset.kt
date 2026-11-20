package eu.kanade.tachiyomi.ui.player

import android.os.Build
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import tachiyomi.i18n.aniyomi.AYMR

data class DecoderPresetSnapshot(
    val tryHwDecoding: Boolean,
    val gpuNext: Boolean,
    val videoDebanding: Debanding,
    val anime4kShaderPreset: Anime4KShaderPreset,
    val motionInterpolationMode: MotionInterpolationMode,
    val useYuv420p: Boolean,
)

enum class DecoderPreset(
    val titleRes: StringResource,
    val summaryRes: StringResource,
    val batteryImpactRes: StringResource,
) {
    Device(
        titleRes = AYMR.strings.pref_decoder_preset_device,
        summaryRes = AYMR.strings.player_decoder_preset_device_summary,
        batteryImpactRes = AYMR.strings.player_battery_impact_balanced,
    ),
    Low(
        titleRes = AYMR.strings.pref_decoder_preset_low,
        summaryRes = AYMR.strings.player_decoder_preset_low_summary,
        batteryImpactRes = AYMR.strings.player_battery_impact_low,
    ),
    Mid(
        titleRes = AYMR.strings.pref_decoder_preset_mid,
        summaryRes = AYMR.strings.player_decoder_preset_mid_summary,
        batteryImpactRes = AYMR.strings.player_battery_impact_medium,
    ),
    High(
        titleRes = AYMR.strings.pref_decoder_preset_high,
        summaryRes = AYMR.strings.player_decoder_preset_high_summary,
        batteryImpactRes = AYMR.strings.player_battery_impact_high,
    ),
    ;

    fun expectedSnapshot(
        supportsGpuNext: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    ): DecoderPresetSnapshot {
        return DecoderPresetSnapshot(
            tryHwDecoding = true,
            gpuNext = when (this) {
                Device -> supportsGpuNext
                Low -> false
                Mid, High -> true
            },
            videoDebanding = when (this) {
                Device -> if (supportsGpuNext) Debanding.GPU else Debanding.None
                Low -> Debanding.None
                Mid, High -> Debanding.GPU
            },
            anime4kShaderPreset = when (this) {
                Device, Low -> Anime4KShaderPreset.Off
                Mid -> Anime4KShaderPreset.Balanced
                High -> Anime4KShaderPreset.Quality
            },
            motionInterpolationMode = when (this) {
                Device, Low -> MotionInterpolationMode.Off
                Mid -> MotionInterpolationMode.Auto
                High -> MotionInterpolationMode.Always
            },
            useYuv420p = false,
        )
    }

    fun matches(
        snapshot: DecoderPresetSnapshot,
        supportsGpuNext: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    ): Boolean = expectedSnapshot(supportsGpuNext) == snapshot

    fun applyTo(decoderPreferences: DecoderPreferences) {
        val target = expectedSnapshot()
        decoderPreferences.tryHWDecoding().set(target.tryHwDecoding)
        decoderPreferences.gpuNext().set(target.gpuNext)
        decoderPreferences.videoDebanding().set(target.videoDebanding)
        decoderPreferences.anime4kShaderPreset().set(target.anime4kShaderPreset)
        decoderPreferences.motionInterpolationMode().set(target.motionInterpolationMode)
        decoderPreferences.useYUV420P().set(target.useYuv420p)
    }

    companion object {
        fun snapshotFrom(decoderPreferences: DecoderPreferences): DecoderPresetSnapshot {
            return DecoderPresetSnapshot(
                tryHwDecoding = decoderPreferences.tryHWDecoding().get(),
                gpuNext = decoderPreferences.gpuNext().get(),
                videoDebanding = decoderPreferences.videoDebanding().get(),
                anime4kShaderPreset = decoderPreferences.anime4kShaderPreset().get(),
                motionInterpolationMode = decoderPreferences.motionInterpolationMode().get(),
                useYuv420p = decoderPreferences.useYUV420P().get(),
            )
        }

        fun resolveActive(
            preferred: DecoderPreset,
            snapshot: DecoderPresetSnapshot,
            supportsGpuNext: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        ): DecoderPreset? {
            if (preferred.matches(snapshot, supportsGpuNext)) return preferred
            return entries.firstOrNull { it.matches(snapshot, supportsGpuNext) }
        }

        fun describeFeatureTags(snapshot: DecoderPresetSnapshot): List<String> {
            return buildList {
                add(if (snapshot.tryHwDecoding) "HW" else "SW")
                add(if (snapshot.gpuNext) "GPU-Next" else "OpenGL")
                if (snapshot.videoDebanding != Debanding.None) {
                    add("Deband: ${snapshot.videoDebanding.name}")
                }
                if (snapshot.anime4kShaderPreset != Anime4KShaderPreset.Off) {
                    add("Anime4K: ${snapshot.anime4kShaderPreset.name}")
                }
                if (snapshot.motionInterpolationMode != MotionInterpolationMode.Off) {
                    add("Motion: ${snapshot.motionInterpolationMode.name}")
                }
                if (snapshot.useYuv420p) {
                    add("YUV420P")
                }
            }
        }
    }
}
