package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.Anime4KShaderPreset
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.DecoderPreset
import eu.kanade.tachiyomi.ui.player.HdrToneMappingMode
import eu.kanade.tachiyomi.ui.player.MotionInterpolationMode
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsDecoderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_decoder

    @Composable
    override fun getPreferences(): List<Preference> {
        val decoderPreferences = remember { Injekt.get<DecoderPreferences>() }

        val decoderPreset = decoderPreferences.decoderPreset()
        val tryHw = decoderPreferences.tryHWDecoding()
        val useGpuNext = decoderPreferences.gpuNext()
        val debanding = decoderPreferences.videoDebanding()
        val anime4k = decoderPreferences.anime4kShaderPreset()
        val motionInterpolation = decoderPreferences.motionInterpolationMode()
        val hdrToneMapping = decoderPreferences.hdrToneMappingMode()
        val matchRefreshRate = decoderPreferences.matchDisplayRefreshRate()
        val yuv420p = decoderPreferences.useYUV420P()

        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = decoderPreset,
                entries = DecoderPreset.entries.associateWith { stringResource(it.titleRes) }
                    .toImmutableMap(),
                title = stringResource(AYMR.strings.pref_decoder_preset_title),
                subtitle = stringResource(AYMR.strings.pref_decoder_preset_subtitle),
                onValueChanged = { preset ->
                    preset.applyTo(decoderPreferences)
                    true
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = tryHw,
                title = stringResource(AYMR.strings.pref_try_hw),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = useGpuNext,
                title = stringResource(AYMR.strings.pref_gpu_next_title),
                subtitle = stringResource(AYMR.strings.pref_gpu_next_subtitle),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = debanding,
                entries = Debanding.entries.associateWith { stringResource(it.titleRes) }
                    .toImmutableMap(),
                title = stringResource(AYMR.strings.pref_debanding_title),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = anime4k,
                entries = Anime4KShaderPreset.entries.associateWith { stringResource(it.titleRes) }
                    .toImmutableMap(),
                title = stringResource(AYMR.strings.pref_anime4k_title),
                subtitle = stringResource(AYMR.strings.pref_anime4k_subtitle),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = motionInterpolation,
                entries = MotionInterpolationMode.entries.associateWith {
                    stringResource(it.titleRes)
                }.toImmutableMap(),
                title = stringResource(AYMR.strings.pref_motion_interpolation_title),
                subtitle = stringResource(AYMR.strings.pref_motion_interpolation_subtitle),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = hdrToneMapping,
                entries = HdrToneMappingMode.entries.associateWith {
                    stringResource(it.titleRes)
                }.toImmutableMap(),
                title = stringResource(AYMR.strings.pref_hdr_tone_mapping_title),
                subtitle = stringResource(AYMR.strings.pref_hdr_tone_mapping_subtitle),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = matchRefreshRate,
                title = stringResource(AYMR.strings.pref_match_display_refresh_rate_title),
                subtitle = stringResource(AYMR.strings.pref_match_display_refresh_rate_subtitle),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = yuv420p,
                title = stringResource(AYMR.strings.pref_use_yuv420p_title),
                subtitle = stringResource(AYMR.strings.pref_use_yuv420p_subtitle),
            ),
        )
    }
}
