package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.setting.PlayerSettingsScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

@Composable
fun SettingsAuroraContent(
    navigator: Navigator,
    onBackClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    val settingsItems = listOf(
        SettingsItem(
            icon = Icons.Outlined.Palette,
            title = stringResource(MR.strings.pref_category_appearance),
            subtitle = stringResource(MR.strings.pref_appearance_summary),
            screen = SettingsAppearanceScreen,
        ),
        SettingsItem(
            icon = Icons.Outlined.CollectionsBookmark,
            title = stringResource(MR.strings.pref_category_library),
            subtitle = stringResource(AYMR.strings.pref_library_summary),
            screen = SettingsLibraryScreen,
        ),
        SettingsItem(
            icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
            title = stringResource(MR.strings.pref_category_reader),
            subtitle = stringResource(MR.strings.pref_reader_summary),
            screen = SettingsReaderScreen,
        ),
        SettingsItem(
            icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
            title = stringResource(AYMR.strings.pref_category_novel_reader),
            subtitle = stringResource(AYMR.strings.pref_novel_reader_summary),
            screen = SettingsNovelReaderScreen,
        ),
        SettingsItem(
            icon = Icons.Outlined.VideoSettings,
            title = stringResource(AYMR.strings.label_player),
            subtitle = stringResource(AYMR.strings.pref_player_settings_summary),
            screen = PlayerSettingsScreen(mainSettings = true),
        ),
        SettingsItem(
            icon = Icons.Outlined.GetApp,
            title = stringResource(MR.strings.pref_category_downloads),
            subtitle = stringResource(MR.strings.pref_downloads_summary),
            screen = SettingsDownloadScreen,
        ),
        SettingsItem(
            icon = Icons.Outlined.Sync,
            title = stringResource(MR.strings.pref_category_tracking),
            subtitle = stringResource(MR.strings.pref_tracking_summary),
            screen = SettingsTrackingScreen,
        ),
        SettingsItem(
            icon = Icons.Outlined.Explore,
            title = stringResource(MR.strings.browse),
            subtitle = stringResource(MR.strings.pref_browse_summary),
            screen = SettingsBrowseScreen,
        ),
        SettingsItem(
            icon = Icons.Outlined.Storage,
            title = stringResource(MR.strings.label_data_storage),
            subtitle = stringResource(MR.strings.pref_backup_summary),
            screen = SettingsDataScreen,
        ),
        SettingsItem(
            icon = Icons.Outlined.Security,
            title = stringResource(MR.strings.pref_category_security),
            subtitle = stringResource(MR.strings.pref_security_summary),
            screen = SettingsSecurityScreen,
        ),
        SettingsItem(
            icon = Icons.Outlined.Code,
            title = stringResource(MR.strings.pref_category_advanced),
            subtitle = stringResource(MR.strings.pref_advanced_summary),
            screen = SettingsAdvancedScreen,
        ),
        SettingsItem(
            icon = Icons.Outlined.Info,
            title = stringResource(MR.strings.pref_category_about),
            subtitle = "${stringResource(MR.strings.app_name)} ${AboutScreen.getVersionName(withBuildDate = false)}",
            screen = AboutScreen,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SettingsAuroraHeader(onBackClick = onBackClick)
            }

            items(settingsItems) { item ->
                SettingsAuroraItem(
                    item = item,
                    onClick = { navigator.push(item.screen) },
                )
            }
        }
    }
}

@Composable
private fun SettingsAuroraHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .background(AuroraTheme.colors.glass, CircleShape)
                .size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(AYMR.strings.aurora_back),
                tint = AuroraTheme.colors.textPrimary,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = stringResource(AYMR.strings.aurora_settings),
                style = MaterialTheme.typography.headlineSmall,
                color = AuroraTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(AYMR.strings.aurora_customize_experience),
                style = MaterialTheme.typography.bodySmall,
                color = AuroraTheme.colors.textSecondary,
            )
        }
    }
}

private data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val screen: VoyagerScreen,
)

@Composable
private fun SettingsAuroraItem(
    item: SettingsItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AuroraTheme.colors.glass,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        AuroraTheme.colors.accent.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = AuroraTheme.colors.accent,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = AuroraTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = AuroraTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AuroraTheme.colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
