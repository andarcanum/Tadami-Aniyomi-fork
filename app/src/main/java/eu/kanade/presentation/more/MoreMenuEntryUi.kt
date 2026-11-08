package eu.kanade.presentation.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import eu.kanade.tachiyomi.ui.more.MoreEntryId
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Single source of truth for the label of a «More» screen entry, shared by the screen itself and by
 * the menu customization screen so a renamed entry cannot drift apart.
 *
 * [movedTabTitle] is resolved by the caller: it comes from `navStyle.moreTab.options`, which may only
 * be read inside the bottom-nav tab navigator.
 */
@Composable
internal fun MoreEntryTitle(
    id: MoreEntryId,
    movedTabTitle: String,
): String {
    return when (id) {
        MoreEntryId.MOVED_TAB -> movedTabTitle
        MoreEntryId.REELS -> stringResource(MR.strings.reels_sources_section_header)
        MoreEntryId.SETTINGS -> stringResource(AYMR.strings.aurora_settings)
        MoreEntryId.PLAYER_SETTINGS -> stringResource(AYMR.strings.aurora_player_settings)
        MoreEntryId.READER_MANGA -> stringResource(MR.strings.pref_category_reader)
        MoreEntryId.READER_NOVEL -> stringResource(AYMR.strings.pref_category_novel_reader)
        MoreEntryId.QUOTES -> stringResource(AYMR.strings.novel_quotes_library_title)
        MoreEntryId.STATS -> stringResource(AYMR.strings.aurora_statistics)
        MoreEntryId.ACHIEVEMENTS -> stringResource(AYMR.strings.aurora_achievements)
        MoreEntryId.TREASURY -> stringResource(AYMR.strings.label_treasury)
        MoreEntryId.DATA_STORAGE -> stringResource(AYMR.strings.aurora_data_storage)
        MoreEntryId.UPDATE_ERRORS -> stringResource(AYMR.strings.option_label_library_update_errors)
        MoreEntryId.DOWNLOADS -> stringResource(AYMR.strings.aurora_downloads)
        MoreEntryId.CATEGORIES -> stringResource(AYMR.strings.aurora_categories)
        MoreEntryId.DOWNLOADED_ONLY -> stringResource(AYMR.strings.aurora_downloaded_only)
        MoreEntryId.INCOGNITO -> stringResource(AYMR.strings.aurora_incognito_mode)
        MoreEntryId.ABOUT -> stringResource(AYMR.strings.aurora_about)
        MoreEntryId.LATTICE_GRID -> stringResource(AYMR.strings.lattice_open_manual)
        MoreEntryId.DEBUG_APP_UPDATE -> stringResource(AYMR.strings.debug_app_update_preview)
        MoreEntryId.DEBUG_CHANGELOG -> stringResource(AYMR.strings.debug_updated_changelog_preview)
        MoreEntryId.DEBUG_RESET_HEART -> stringResource(AYMR.strings.debug_reset_aurora_heart)
        MoreEntryId.DEBUG_RESET_LATTICE -> stringResource(AYMR.strings.debug_reset_lattice_resonance)
        MoreEntryId.DEBUG_FORCE_BREACH -> stringResource(AYMR.strings.debug_force_lattice_breach)
        MoreEntryId.DEBUG_CRYSTAL -> stringResource(AYMR.strings.crystal_debug_title)
        MoreEntryId.HELP -> stringResource(AYMR.strings.aurora_help)
    }
}

/** Icon of a «More» screen entry; [movedTabIcon] is `navStyle.moreIcon` resolved by the caller. */
internal fun moreEntryIcon(
    id: MoreEntryId,
    movedTabIcon: ImageVector,
): ImageVector {
    return when (id) {
        MoreEntryId.MOVED_TAB -> movedTabIcon
        MoreEntryId.REELS -> Icons.Outlined.SlowMotionVideo
        MoreEntryId.SETTINGS -> Icons.Filled.Settings
        MoreEntryId.PLAYER_SETTINGS -> Icons.Outlined.VideoSettings
        MoreEntryId.READER_MANGA -> Icons.AutoMirrored.Outlined.ChromeReaderMode
        MoreEntryId.READER_NOVEL -> Icons.Outlined.Book
        MoreEntryId.QUOTES -> Icons.Outlined.FormatQuote
        MoreEntryId.STATS -> Icons.Filled.QueryStats
        MoreEntryId.ACHIEVEMENTS -> Icons.Filled.EmojiEvents
        MoreEntryId.TREASURY -> Icons.Outlined.Inventory2
        MoreEntryId.DATA_STORAGE -> Icons.Outlined.Storage
        MoreEntryId.UPDATE_ERRORS -> Icons.Outlined.ReportProblem
        MoreEntryId.DOWNLOADS -> Icons.Filled.Download
        MoreEntryId.CATEGORIES -> Icons.AutoMirrored.Outlined.Label
        MoreEntryId.DOWNLOADED_ONLY -> Icons.Filled.CloudOff
        MoreEntryId.INCOGNITO -> Icons.Outlined.VisibilityOff
        MoreEntryId.ABOUT -> Icons.Filled.Info
        MoreEntryId.LATTICE_GRID -> Icons.Outlined.Hub
        MoreEntryId.DEBUG_APP_UPDATE -> Icons.Outlined.NewReleases
        MoreEntryId.DEBUG_CHANGELOG -> Icons.Outlined.NewReleases
        MoreEntryId.DEBUG_RESET_HEART -> Icons.Outlined.ReportProblem
        MoreEntryId.DEBUG_RESET_LATTICE -> Icons.Outlined.ReportProblem
        MoreEntryId.DEBUG_FORCE_BREACH -> Icons.Outlined.NewReleases
        MoreEntryId.DEBUG_CRYSTAL -> Icons.Outlined.NewReleases
        MoreEntryId.HELP -> Icons.AutoMirrored.Filled.Help
    }
}
