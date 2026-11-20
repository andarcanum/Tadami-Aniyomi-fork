package eu.kanade.presentation.more.settings.screen.player.layout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutConfig
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutOrientation
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutPreset
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutRegion
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutSlot
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PlayerSettingsLayoutScreen(
    private val orientation: PlayerLayoutOrientation,
) : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = when (orientation) {
        PlayerLayoutOrientation.Portrait -> AYMR.strings.pref_player_layout_portrait
        PlayerLayoutOrientation.Landscape -> AYMR.strings.pref_player_layout_landscape
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
        val layoutConfigPref = playerPreferences.playerLayoutConfig()
        val layoutConfigValue by layoutConfigPref.collectAsState()
        val layoutConfig = remember(layoutConfigValue) {
            PlayerLayoutConfig.fromPreferenceValue(layoutConfigValue)
        }

        val slotLabels = layoutSlotLabels()
        val regionLabels = layoutRegionLabels()
        val activePreset = remember(layoutConfig, orientation) {
            PlayerLayoutPreset.entries.firstOrNull { it.matches(layoutConfig, orientation) }
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_player_layout_preview),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(AYMR.strings.pref_player_layout_preview),
                        content = {
                            PlayerLayoutVisualPreview(
                                orientation = orientation,
                                layoutConfig = layoutConfig,
                                activePreset = activePreset,
                                slotLabels = slotLabels,
                                onSelectPreset = { preset ->
                                    val latest = PlayerLayoutConfig.fromPreferenceValue(layoutConfigPref.get())
                                    layoutConfigPref.set(preset.applyTo(latest, orientation).toPreferenceValue())
                                },
                                onReset = {
                                    val latest = PlayerLayoutConfig.fromPreferenceValue(layoutConfigPref.get())
                                    layoutConfigPref.set(
                                        PlayerLayoutPreset.Standard.applyTo(latest, orientation).toPreferenceValue(),
                                    )
                                },
                            )
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_player_layout_slots),
                preferenceItems = PlayerLayoutSlot.entries.map { slot ->
                    val currentRegion = layoutConfig.regionFor(orientation, slot)
                    val allowedRegions = slot.allowedRegions()

                    Preference.PreferenceItem.BasicListPreference(
                        value = currentRegion.name,
                        entries = allowedRegions.associate { region ->
                            region.name to (regionLabels[region] ?: region.previewLabel)
                        }.toPersistentMap(),
                        title = slotLabels[slot] ?: slot.previewLabel,
                        onValueChanged = { newValue ->
                            val latestConfig = PlayerLayoutConfig.fromPreferenceValue(
                                layoutConfigPref.get(),
                            )
                            layoutConfigPref.set(
                                latestConfig.withRegion(
                                    orientation = orientation,
                                    slot = slot,
                                    region = PlayerLayoutRegion.valueOf(newValue),
                                ).toPreferenceValue(),
                            )
                            true
                        },
                    )
                }.let { persistentListOf(*it.toTypedArray()) },
            ),
        )
    }

    @Composable
    private fun layoutSlotLabels(): Map<PlayerLayoutSlot, String> {
        return mapOf(
            PlayerLayoutSlot.PlaybackSpeed to stringResource(AYMR.strings.pref_player_layout_slot_playback_speed),
            PlayerLayoutSlot.LockControls to stringResource(AYMR.strings.pref_player_layout_slot_lock_controls),
            PlayerLayoutSlot.RotateScreen to stringResource(AYMR.strings.pref_player_layout_slot_rotate_screen),
            PlayerLayoutSlot.SkipIntro to stringResource(AYMR.strings.pref_player_layout_slot_skip_intro),
            PlayerLayoutSlot.CustomButton to stringResource(AYMR.strings.pref_player_layout_slot_custom_button),
            PlayerLayoutSlot.PictureInPicture to stringResource(
                AYMR.strings.pref_player_layout_slot_picture_in_picture,
            ),
            PlayerLayoutSlot.AspectRatio to stringResource(AYMR.strings.pref_player_layout_slot_aspect_ratio),
        )
    }

    @Composable
    private fun layoutRegionLabels(): Map<PlayerLayoutRegion, String> {
        return mapOf(
            PlayerLayoutRegion.BottomLeft to stringResource(AYMR.strings.pref_player_layout_region_bottom_left),
            PlayerLayoutRegion.BottomRight to stringResource(AYMR.strings.pref_player_layout_region_bottom_right),
            PlayerLayoutRegion.Hidden to stringResource(AYMR.strings.pref_player_layout_region_hidden),
        )
    }
}

@Composable
private fun PlayerLayoutVisualPreview(
    orientation: PlayerLayoutOrientation,
    layoutConfig: PlayerLayoutConfig,
    activePreset: PlayerLayoutPreset?,
    slotLabels: Map<PlayerLayoutSlot, String>,
    onSelectPreset: (PlayerLayoutPreset) -> Unit,
    onReset: () -> Unit,
) {
    val accent = AuroraTheme.colors.accent
    val bottomLeftSlots = layoutConfig.slotsForRegion(orientation, PlayerLayoutRegion.BottomLeft)
    val bottomRightSlots = layoutConfig.slotsForRegion(orientation, PlayerLayoutRegion.BottomRight)
    val hiddenSlots = layoutConfig.slotsForRegion(orientation, PlayerLayoutRegion.Hidden)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                PlayerLayoutPreset.entries.forEach { preset ->
                    val labelRes = when (preset) {
                        PlayerLayoutPreset.Minimal -> AYMR.strings.player_layout_preset_minimal
                        PlayerLayoutPreset.Standard -> AYMR.strings.player_layout_preset_standard
                        PlayerLayoutPreset.Pro -> AYMR.strings.player_layout_preset_pro
                    }
                    FilterChip(
                        selected = activePreset == preset,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(text = stringResource(labelRes)) },
                    )
                }
            }
            TextButton(onClick = onReset) {
                Text(text = stringResource(MR.strings.action_reset))
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10151F),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Top mockup chrome
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (orientation == PlayerLayoutOrientation.Landscape) "16:9" else "9:16",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Text(
                        text = "CC · ♪ · HD · ⋮",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }

                // Bottom-left and Bottom-right live slot clusters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (bottomLeftSlots.isEmpty()) {
                            PreviewSlotBadge(
                                text = stringResource(AYMR.strings.player_layout_region_empty),
                                color = Color.White.copy(alpha = 0.14f),
                                textColor = Color.White.copy(alpha = 0.45f),
                            )
                        } else {
                            bottomLeftSlots.forEach { slot ->
                                PreviewSlotBadge(
                                    text = slotLabels[slot] ?: slot.previewLabel,
                                    color = accent.copy(alpha = 0.28f),
                                    textColor = Color.White,
                                )
                            }
                        }
                    }
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (bottomRightSlots.isEmpty()) {
                            PreviewSlotBadge(
                                text = stringResource(AYMR.strings.player_layout_region_empty),
                                color = Color.White.copy(alpha = 0.14f),
                                textColor = Color.White.copy(alpha = 0.45f),
                            )
                        } else {
                            bottomRightSlots.forEach { slot ->
                                PreviewSlotBadge(
                                    text = slotLabels[slot] ?: slot.previewLabel,
                                    color = Color.White.copy(alpha = 0.18f),
                                    textColor = Color.White,
                                )
                            }
                        }
                    }
                }

                // Seekbar hairline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
            }
        }

        if (hiddenSlots.isNotEmpty()) {
            Text(
                text = "${stringResource(AYMR.strings.pref_player_layout_region_hidden)}: " +
                    hiddenSlots.joinToString { slotLabels[it] ?: it.previewLabel },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewSlotBadge(
    text: String,
    color: Color,
    textColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
