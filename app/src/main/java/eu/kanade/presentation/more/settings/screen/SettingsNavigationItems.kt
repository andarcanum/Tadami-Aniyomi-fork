package eu.kanade.presentation.more.settings.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.achievement.screen.AchievementScreenVoyager
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsAudioScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsDecoderScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsGesturesScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsPlayerScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsSubtitleScreen
import eu.kanade.presentation.more.settings.screen.player.custombutton.PlayerSettingsCustomButtonScreen
import eu.kanade.presentation.more.settings.screen.player.editor.PlayerSettingsEditorScreen
import eu.kanade.presentation.more.settings.screen.player.layout.PlayerSettingsLayoutMainScreen
import eu.kanade.tachiyomi.ui.setting.PlayerSettingsScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

internal data class SettingsNavigationItem(
    val key: String,
    val titleRes: StringResource,
    val subtitleRes: StringResource? = null,
    val subtitleType: SettingsSubtitleType = SettingsSubtitleType.Resource,
    val icon: ImageVector,
    val screen: VoyagerScreen,
)

internal enum class SettingsSubtitleType {
    Resource,
    AppVersion,
}

@Composable
internal fun SettingsNavigationItem.subtitleText(): String? {
    return when (subtitleType) {
        SettingsSubtitleType.Resource -> subtitleRes?.let { stringResource(it) }
        SettingsSubtitleType.AppVersion -> {
            "${stringResource(MR.strings.app_name)} ${AboutScreen.getVersionName(withBuildDate = false)}"
        }
    }
}

internal fun mainSettingsNavigationItems(): List<SettingsNavigationItem> = listOf(
    SettingsNavigationItem(
        key = "appearance",
        titleRes = MR.strings.pref_category_appearance,
        subtitleRes = MR.strings.pref_appearance_summary,
        icon = Icons.Outlined.Palette,
        screen = SettingsAppearanceScreen,
    ),
    SettingsNavigationItem(
        key = "library",
        titleRes = MR.strings.pref_category_library,
        subtitleRes = AYMR.strings.pref_library_summary,
        icon = Icons.Outlined.CollectionsBookmark,
        screen = SettingsLibraryScreen,
    ),
    SettingsNavigationItem(
        key = "reader",
        titleRes = MR.strings.pref_category_reader,
        subtitleRes = MR.strings.pref_reader_summary,
        icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
        screen = SettingsReaderScreen,
    ),
    SettingsNavigationItem(
        key = "novel_reader",
        titleRes = AYMR.strings.pref_category_novel_reader,
        subtitleRes = AYMR.strings.pref_novel_reader_summary,
        icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
        screen = SettingsNovelReaderScreen,
    ),
    SettingsNavigationItem(
        key = "player",
        titleRes = AYMR.strings.label_player,
        subtitleRes = AYMR.strings.pref_player_settings_summary,
        icon = Icons.Outlined.VideoSettings,
        screen = PlayerSettingsScreen(mainSettings = true),
    ),
    SettingsNavigationItem(
        key = "achievements",
        titleRes = AYMR.strings.label_achievements,
        subtitleRes = AYMR.strings.pref_achievements_summary,
        icon = Icons.Outlined.EmojiEvents,
        screen = AchievementScreenVoyager,
    ),
    SettingsNavigationItem(
        key = "downloads",
        titleRes = MR.strings.pref_category_downloads,
        subtitleRes = MR.strings.pref_downloads_summary,
        icon = Icons.Outlined.GetApp,
        screen = SettingsDownloadScreen,
    ),
    SettingsNavigationItem(
        key = "tracking",
        titleRes = MR.strings.pref_category_tracking,
        subtitleRes = MR.strings.pref_tracking_summary,
        icon = Icons.Outlined.Sync,
        screen = SettingsTrackingScreen,
    ),
    SettingsNavigationItem(
        key = "browse",
        titleRes = MR.strings.browse,
        subtitleRes = MR.strings.pref_browse_summary,
        icon = Icons.Outlined.Explore,
        screen = SettingsBrowseScreen,
    ),
    SettingsNavigationItem(
        key = "data_storage",
        titleRes = MR.strings.label_data_storage,
        subtitleRes = MR.strings.pref_backup_summary,
        icon = Icons.Outlined.Storage,
        screen = SettingsDataScreen,
    ),
    SettingsNavigationItem(
        key = "security",
        titleRes = MR.strings.pref_category_security,
        subtitleRes = MR.strings.pref_security_summary,
        icon = Icons.Outlined.Security,
        screen = SettingsSecurityScreen,
    ),
    SettingsNavigationItem(
        key = "advanced",
        titleRes = MR.strings.pref_category_advanced,
        subtitleRes = MR.strings.pref_advanced_summary,
        icon = Icons.Outlined.Code,
        screen = SettingsAdvancedScreen,
    ),
    SettingsNavigationItem(
        key = "about",
        titleRes = MR.strings.pref_category_about,
        subtitleType = SettingsSubtitleType.AppVersion,
        icon = Icons.Outlined.Info,
        screen = AboutScreen,
    ),
)

internal fun playerSettingsNavigationItems(): List<SettingsNavigationItem> = listOf(
    SettingsNavigationItem(
        key = "player_internal",
        titleRes = AYMR.strings.pref_player_internal,
        subtitleRes = AYMR.strings.pref_player_internal_summary,
        icon = Icons.Outlined.PlayCircleOutline,
        screen = PlayerSettingsPlayerScreen,
    ),
    SettingsNavigationItem(
        key = "player_layout",
        titleRes = AYMR.strings.pref_player_layout,
        subtitleRes = AYMR.strings.pref_player_layout_summary,
        icon = Icons.Filled.ViewModule,
        screen = PlayerSettingsLayoutMainScreen,
    ),
    SettingsNavigationItem(
        key = "player_gestures",
        titleRes = AYMR.strings.pref_player_gestures,
        subtitleRes = AYMR.strings.pref_player_gestures_summary,
        icon = Icons.Outlined.Gesture,
        screen = PlayerSettingsGesturesScreen,
    ),
    SettingsNavigationItem(
        key = "player_decoder",
        titleRes = AYMR.strings.pref_player_decoder,
        subtitleRes = AYMR.strings.pref_player_decoder_summary,
        icon = Icons.Outlined.Memory,
        screen = PlayerSettingsDecoderScreen,
    ),
    SettingsNavigationItem(
        key = "player_subtitle",
        titleRes = AYMR.strings.pref_player_subtitle,
        subtitleRes = AYMR.strings.pref_player_subtitle_summary,
        icon = Icons.Outlined.Subtitles,
        screen = PlayerSettingsSubtitleScreen,
    ),
    SettingsNavigationItem(
        key = "player_audio",
        titleRes = AYMR.strings.pref_player_audio,
        subtitleRes = AYMR.strings.pref_player_audio_summary,
        icon = Icons.Outlined.Audiotrack,
        screen = PlayerSettingsAudioScreen,
    ),
    SettingsNavigationItem(
        key = "player_custom_button",
        titleRes = AYMR.strings.pref_player_custom_button,
        subtitleRes = AYMR.strings.pref_player_custom_button_summary,
        icon = Icons.Outlined.Terminal,
        screen = PlayerSettingsCustomButtonScreen,
    ),
    SettingsNavigationItem(
        key = "player_editor",
        titleRes = AYMR.strings.pref_player_editor,
        subtitleRes = AYMR.strings.pref_player_editor_summary,
        icon = Icons.Outlined.EditNote,
        screen = PlayerSettingsEditorScreen,
    ),
    SettingsNavigationItem(
        key = "player_advanced",
        titleRes = AYMR.strings.pref_player_advanced,
        subtitleRes = AYMR.strings.pref_player_advanced_summary,
        icon = Icons.Outlined.Security,
        screen = PlayerSettingsAdvancedScreen,
    ),
)
