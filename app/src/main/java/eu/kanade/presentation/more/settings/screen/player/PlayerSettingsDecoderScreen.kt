package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.player.Anime4KShaderPreset
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.DecoderPreset
import eu.kanade.tachiyomi.ui.player.DecoderPresetSnapshot
import eu.kanade.tachiyomi.ui.player.HdrToneMappingMode
import eu.kanade.tachiyomi.ui.player.MotionInterpolationMode
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsDecoderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_decoder

    @Composable
    override fun getPreferences(): List<Preference> {
        val decoderPreferences = remember { Injekt.get<DecoderPreferences>() }

        val decoderPresetPref = decoderPreferences.decoderPreset()
        val tryHw = decoderPreferences.tryHWDecoding()
        val useGpuNext = decoderPreferences.gpuNext()
        val debanding = decoderPreferences.videoDebanding()
        val anime4k = decoderPreferences.anime4kShaderPreset()
        val motionInterpolation = decoderPreferences.motionInterpolationMode()
        val hdrToneMapping = decoderPreferences.hdrToneMappingMode()
        val matchRefreshRate = decoderPreferences.matchDisplayRefreshRate()
        val yuv420p = decoderPreferences.useYUV420P()

        val selectedPreset by decoderPresetPref.collectAsState()
        val tryHwValue by tryHw.collectAsState()
        val gpuNextValue by useGpuNext.collectAsState()
        val debandingValue by debanding.collectAsState()
        val anime4kValue by anime4k.collectAsState()
        val motionValue by motionInterpolation.collectAsState()
        val yuv420pValue by yuv420p.collectAsState()

        val currentSnapshot = remember(
            tryHwValue,
            gpuNextValue,
            debandingValue,
            anime4kValue,
            motionValue,
            yuv420pValue,
        ) {
            DecoderPresetSnapshot(
                tryHwDecoding = tryHwValue,
                gpuNext = gpuNextValue,
                videoDebanding = debandingValue,
                anime4kShaderPreset = anime4kValue,
                motionInterpolationMode = motionValue,
                useYuv420p = yuv420pValue,
            )
        }
        val activePreset = remember(selectedPreset, currentSnapshot) {
            DecoderPreset.resolveActive(selectedPreset, currentSnapshot)
        }
        val featureTags = remember(currentSnapshot) {
            DecoderPreset.describeFeatureTags(currentSnapshot)
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_decoder_preset_title),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(AYMR.strings.pref_decoder_preset_title),
                        content = {
                            DecoderPresetOverviewCard(
                                activePreset = activePreset,
                                featureTags = featureTags,
                                onSelectPreset = { preset ->
                                    decoderPresetPref.set(preset)
                                    preset.applyTo(decoderPreferences)
                                },
                            )
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.player_decoder_group_core),
                preferenceItems = persistentListOf(
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
                    Preference.PreferenceItem.SwitchPreference(
                        preference = yuv420p,
                        title = stringResource(AYMR.strings.pref_use_yuv420p_title),
                        subtitle = stringResource(AYMR.strings.pref_use_yuv420p_subtitle),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.player_decoder_group_enhancement),
                preferenceItems = persistentListOf(
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
                ),
            ),
        )
    }
}

@Composable
private fun DecoderPresetOverviewCard(
    activePreset: DecoderPreset?,
    featureTags: List<String>,
    onSelectPreset: (DecoderPreset) -> Unit,
) {
    val accent = AuroraTheme.colors.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DecoderPreset.entries.forEach { preset ->
                FilterChip(
                    selected = activePreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(text = stringResource(preset.titleRes)) },
                )
            }
            if (activePreset == null) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(text = stringResource(AYMR.strings.player_decoder_preset_custom)) },
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activePreset?.let { stringResource(it.titleRes) }
                            ?: stringResource(AYMR.strings.player_decoder_preset_custom),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    if (activePreset != null) {
                        Text(
                            text = stringResource(activePreset.batteryImpactRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = activePreset?.let { stringResource(it.summaryRes) }
                        ?: stringResource(AYMR.strings.player_decoder_preset_custom_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    featureTags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
