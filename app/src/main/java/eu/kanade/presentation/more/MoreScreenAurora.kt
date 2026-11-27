package eu.kanade.presentation.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tadami.aurora.BuildConfig
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.components.LocalHostScaffoldContentPadding
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.more.resolveAuroraMoreSwitchColors
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.AuroraTopBarTitleText
import eu.kanade.presentation.more.settings.auroraCardStyle
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsDefaultAppUiFont
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import eu.kanade.tachiyomi.ui.more.MoreEntryId
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MoreScreenAurora(
    navStyle: NavStyle,
    onClickAlt: () -> Unit,
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onDownloadClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onDataStorageClick: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
    onMangaReaderSettingsClick: () -> Unit,
    onNovelReaderSettingsClick: () -> Unit,
    onNovelQuotesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDebugAppUpdatePreviewClick: () -> Unit,
    onDebugUpdatedChangelogPreviewClick: () -> Unit,
    onDebugResetAuroraHeartClick: () -> Unit,
    onDebugResetLatticeResonanceClick: () -> Unit,
    onDebugForceLatticeBreachClick: () -> Unit,
    onStatsClick: () -> Unit,
    onLibraryUpdateErrorsClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onTreasuryClick: () -> Unit,
    onHelpClick: () -> Unit,
    latticeGridAvailable: Boolean,
    onOpenLatticeGridClick: () -> Unit,
    showReelsEntry: Boolean = false,
    onReelsClick: () -> Unit = {},
    visibleEntryIds: List<MoreEntryId> = MoreEntryId.entries.toList(),
    onCustomizeMenuClick: () -> Unit = {},
    onDebugCrystalClick: () -> Unit = {},
) {
    val colors = AuroraTheme.colors
    val hostScaffoldContentPadding = LocalHostScaffoldContentPadding.current
    val bottomContentPadding = (hostScaffoldContentPadding?.calculateBottomPadding() ?: 0.dp) + 24.dp
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val darkRimLightEnabled by uiPreferences.auroraDarkRimLightEnabled().collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    val movedTabTitle = navStyle.moreTab.options.title
    val movedTabIcon = navStyle.moreIcon

    val downloadQueueState = downloadQueueStateProvider()
    val downloadSubtitle = when (downloadQueueState) {
        DownloadQueueState.Stopped -> null
        is DownloadQueueState.Paused -> {
            val pending = downloadQueueState.pending
            if (pending == 0) {
                stringResource(AYMR.strings.aurora_download_paused)
            } else {
                "${stringResource(
                    AYMR.strings.aurora_download_paused,
                )} • ${stringResource(AYMR.strings.aurora_download_pending, pending)}"
            }
        }
        is DownloadQueueState.Downloading -> {
            stringResource(AYMR.strings.aurora_download_pending, downloadQueueState.pending)
        }
    }

    // Every entry has a stable id, so the user-defined order and hidden set can be applied by the
    // caller without this screen knowing anything about them.
    val entries = listOfNotNull(
        MoreEntry(MoreEntryId.MOVED_TAB, onClick = onClickAlt),
        if (showReelsEntry) MoreEntry(MoreEntryId.REELS, onClick = onReelsClick) else null,
        MoreEntry(MoreEntryId.SETTINGS, onClick = onSettingsClick),
        MoreEntry(MoreEntryId.PLAYER_SETTINGS, onClick = onPlayerSettingsClick),
        MoreEntry(MoreEntryId.READER_MANGA, onClick = onMangaReaderSettingsClick),
        MoreEntry(MoreEntryId.READER_NOVEL, onClick = onNovelReaderSettingsClick),
        MoreEntry(MoreEntryId.QUOTES, onClick = onNovelQuotesClick),
        MoreEntry(MoreEntryId.STATS, onClick = onStatsClick),
        MoreEntry(MoreEntryId.ACHIEVEMENTS, onClick = onAchievementsClick),
        MoreEntry(MoreEntryId.TREASURY, onClick = onTreasuryClick),
        MoreEntry(MoreEntryId.DATA_STORAGE, onClick = onDataStorageClick),
        MoreEntry(MoreEntryId.UPDATE_ERRORS, onClick = onLibraryUpdateErrorsClick),
        MoreEntry(MoreEntryId.DOWNLOADS, subtitle = downloadSubtitle, onClick = onDownloadClick),
        MoreEntry(MoreEntryId.CATEGORIES, onClick = onCategoriesClick),
        MoreEntry(
            id = MoreEntryId.DOWNLOADED_ONLY,
            checked = downloadedOnly,
            onCheckedChange = onDownloadedOnlyChange,
        ),
        MoreEntry(
            id = MoreEntryId.INCOGNITO,
            checked = incognitoMode,
            onCheckedChange = onIncognitoModeChange,
        ),
        MoreEntry(MoreEntryId.ABOUT, onClick = onAboutClick),
        // Safety net for the Frame resonance easter egg: once every carrier is
        // latched the Grid must always be reachable by hand.
        if (latticeGridAvailable) {
            MoreEntry(
                id = MoreEntryId.LATTICE_GRID,
                subtitle = stringResource(AYMR.strings.lattice_open_manual_summary),
                onClick = onOpenLatticeGridClick,
            )
        } else {
            null
        },
        if (BuildConfig.DEBUG) {
            MoreEntry(
                id = MoreEntryId.DEBUG_APP_UPDATE,
                subtitle = stringResource(AYMR.strings.debug_app_update_preview_summary),
                onClick = onDebugAppUpdatePreviewClick,
            )
        } else {
            null
        },
        if (BuildConfig.DEBUG) {
            MoreEntry(
                id = MoreEntryId.DEBUG_CHANGELOG,
                subtitle = stringResource(AYMR.strings.debug_updated_changelog_preview_summary),
                onClick = onDebugUpdatedChangelogPreviewClick,
            )
        } else {
            null
        },
        if (BuildConfig.DEBUG) {
            MoreEntry(
                id = MoreEntryId.DEBUG_RESET_HEART,
                subtitle = stringResource(AYMR.strings.debug_reset_aurora_heart_summary),
                onClick = onDebugResetAuroraHeartClick,
            )
        } else {
            null
        },
        if (BuildConfig.DEBUG) {
            MoreEntry(
                id = MoreEntryId.DEBUG_RESET_LATTICE,
                subtitle = stringResource(AYMR.strings.debug_reset_lattice_resonance_summary),
                onClick = onDebugResetLatticeResonanceClick,
            )
        } else {
            null
        },
        if (BuildConfig.DEBUG) {
            MoreEntry(
                id = MoreEntryId.DEBUG_FORCE_BREACH,
                subtitle = stringResource(AYMR.strings.debug_force_lattice_breach_summary),
                onClick = onDebugForceLatticeBreachClick,
            )
        } else {
            null
        },
        if (BuildConfig.DEBUG) {
            MoreEntry(
                id = MoreEntryId.DEBUG_CRYSTAL,
                subtitle = stringResource(AYMR.strings.crystal_debug_description),
                onClick = onDebugCrystalClick,
            )
        } else {
            null
        },
        MoreEntry(MoreEntryId.HELP, onClick = onHelpClick),
    )
    val entriesById = entries.associateBy { it.id }

    AuroraBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = bottomContentPadding),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        AuroraTopBarTitleText(title = stringResource(AYMR.strings.aurora_more))
                    }
                    Box {
                        AuroraTopBarIconButton(
                            onClick = { menuExpanded = true },
                            icon = Icons.Filled.MoreVert,
                            contentDescription = stringResource(MR.strings.action_menu),
                        )
                        AuroraEntryDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(AYMR.strings.aurora_more_menu_customize),
                                leadingIcon = Icons.Outlined.Tune,
                                onClick = {
                                    menuExpanded = false
                                    onCustomizeMenuClick()
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                visibleEntryIds.forEach { id ->
                    val entry = entriesById[id] ?: return@forEach
                    val onCheckedChange = entry.onCheckedChange
                    if (onCheckedChange != null) {
                        AuroraToggleItem(
                            title = MoreEntryTitle(id, movedTabTitle),
                            icon = moreEntryIcon(id, movedTabIcon),
                            checked = entry.checked,
                            onCheckedChange = onCheckedChange,
                            darkRimLightEnabled = darkRimLightEnabled,
                        )
                    } else {
                        AuroraSettingItem(
                            title = MoreEntryTitle(id, movedTabTitle),
                            subtitle = entry.subtitle,
                            icon = moreEntryIcon(id, movedTabIcon),
                            onClick = entry.onClick,
                            darkRimLightEnabled = darkRimLightEnabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuroraSettingItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    darkRimLightEnabled: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val useMediumWeight = LocalIsDefaultAppUiFont.current
    val appHaptics = LocalAppHaptics.current

    Card(
        onClick = {
            appHaptics.tap()
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AURORA_MORE_CARD_VERTICAL_INSET)
            .auroraCardStyle(colors, RoundedCornerShape(20.dp), applyDarkRimLight = darkRimLightEnabled),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!colors.isDark && !colors.isEInk) {
                Color.Transparent
            } else {
                resolveAuroraMoreCardContainerColor(colors)
            },
        ),
        border = if (colors.isEInk) {
            BorderStroke(
                width = 1.dp,
                color = resolveAuroraMoreCardBorderColor(colors),
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = auroraPrimaryMenuTitleTextStyle(
                        baseStyle = MaterialTheme.typography.bodyLarge,
                        useMediumWeight = useMediumWeight,
                    ),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun AuroraToggleItem(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    darkRimLightEnabled: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val useMediumWeight = LocalIsDefaultAppUiFont.current
    val appHaptics = LocalAppHaptics.current

    Card(
        onClick = {
            appHaptics.tap()
            onCheckedChange(!checked)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AURORA_MORE_CARD_VERTICAL_INSET)
            .auroraCardStyle(colors, RoundedCornerShape(20.dp), applyDarkRimLight = darkRimLightEnabled),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!colors.isDark && !colors.isEInk) {
                Color.Transparent
            } else {
                resolveAuroraMoreCardContainerColor(colors)
            },
        ),
        border = if (colors.isEInk) {
            BorderStroke(
                width = 1.dp,
                color = resolveAuroraMoreCardBorderColor(colors),
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = auroraPrimaryMenuTitleTextStyle(
                        baseStyle = MaterialTheme.typography.bodyLarge,
                        useMediumWeight = useMediumWeight,
                    ),
                )
            }

            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                androidx.compose.material3.Switch(
                    checked = checked,
                    onCheckedChange = null,
                    colors = resolveAuroraMoreSwitchColors(colors, colors.accent),
                )
            }
        }
    }
}

/**
 * One row of the «More» screen. Title and icon are resolved from [MoreEntryId] so the screen and the
 * menu customization screen cannot drift apart; a non-null [onCheckedChange] makes the row a toggle.
 */
private class MoreEntry(
    val id: MoreEntryId,
    val subtitle: String? = null,
    val checked: Boolean = false,
    val onClick: () -> Unit = {},
    val onCheckedChange: ((Boolean) -> Unit)? = null,
)
