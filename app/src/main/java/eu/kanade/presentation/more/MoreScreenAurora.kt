package eu.kanade.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

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
    onNovelReaderSettingsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onHelpClick: () -> Unit,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(80.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_more),
                    fontSize = 22.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }

            item {
                AuroraSettingItem(
                    title = navStyle.moreTab.options.title,
                    icon = navStyle.moreIcon,
                    onClick = onClickAlt,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_settings),
                    icon = Icons.Filled.Settings,
                    onClick = onSettingsClick,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_player_settings),
                    icon = Icons.Outlined.VideoSettings,
                    onClick = onPlayerSettingsClick,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.pref_category_novel_reader),
                    icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                    onClick = onNovelReaderSettingsClick,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_statistics),
                    icon = Icons.Filled.QueryStats,
                    onClick = onStatsClick,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_achievements),
                    icon = Icons.Filled.EmojiEvents,
                    onClick = onAchievementsClick,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_data_storage),
                    icon = Icons.Outlined.Storage,
                    onClick = onDataStorageClick,
                )

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
                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_downloads),
                    subtitle = downloadSubtitle,
                    icon = Icons.Filled.Download,
                    onClick = onDownloadClick,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_categories),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    onClick = onCategoriesClick,
                )

                AuroraToggleItem(
                    title = stringResource(AYMR.strings.aurora_downloaded_only),
                    icon = Icons.Filled.CloudOff,
                    checked = downloadedOnly,
                    onCheckedChange = onDownloadedOnlyChange,
                )

                AuroraToggleItem(
                    title = stringResource(AYMR.strings.aurora_incognito_mode),
                    icon = Icons.Outlined.VisibilityOff,
                    checked = incognitoMode,
                    onCheckedChange = onIncognitoModeChange,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_about),
                    icon = Icons.Filled.Info,
                    onClick = onAboutClick,
                )

                AuroraSettingItem(
                    title = stringResource(AYMR.strings.aurora_help),
                    icon = Icons.AutoMirrored.Filled.Help,
                    onClick = onHelpClick,
                )
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
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glass)
            .clickable(onClick = onClick)
            .padding(16.dp),
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
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

@Composable
fun AuroraToggleItem(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glass)
            .clickable { onCheckedChange(!checked) }
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = colors.accent,
                    checkedTrackColor = colors.accent.copy(alpha = 0.5f),
                ),
            )
        }
    }
}
