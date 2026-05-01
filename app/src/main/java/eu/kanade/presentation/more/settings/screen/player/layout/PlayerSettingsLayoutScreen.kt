package eu.kanade.presentation.more.settings.screen.player.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutConfig
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutOrientation
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutRegion
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutSlot
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
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
    override fun getTitleRes() = titleRes()

    private fun titleRes() = when (orientation) {
        PlayerLayoutOrientation.Portrait -> AYMR.strings.pref_player_layout_portrait
        PlayerLayoutOrientation.Landscape -> AYMR.strings.pref_player_layout_landscape
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
        val layoutConfigPref = playerPreferences.playerLayoutConfig()
        val layoutConfigValue by layoutConfigPref.collectAsState()
        val layoutConfig = PlayerLayoutConfig.fromPreferenceValue(layoutConfigValue)

        val slotLabels = layoutSlotLabels()
        val regionLabels = layoutRegionLabels()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_player_layout_preview),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(titleRes()),
                        subtitle = layoutConfig.summaryFor(
                            orientation = orientation,
                            slotLabel = { slot -> slotLabels[slot] ?: slot.previewLabel },
                            regionLabel = { region -> regionLabels[region] ?: region.previewLabel },
                        ),
                        enabled = false,
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
