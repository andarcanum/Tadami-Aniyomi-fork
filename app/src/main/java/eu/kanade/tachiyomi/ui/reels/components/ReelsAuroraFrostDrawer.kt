package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.components.AuroraCapsuleTabs
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * Settings window for native landscape reels (Prototype v2): a centered Aurora frost dialog
 * (system blur + dim, rim-light edge, header close button) with capsule tabs — Player,
 * Network & Offline, Playlist.
 */
@Composable
fun ReelsAuroraFrostDrawer(
    isOpen: Boolean,
    initialTab: Int = 0,
    currentItem: ShortVideoItem? = null,
    playlistItems: List<ShortVideoItem> = emptyList(),
    currentPlaylistIndex: Int = 0,
    isHdQuality: Boolean = true,
    onToggleQuality: () -> Unit = {},
    qualityModeEnabled: Boolean = false,
    qualityLabel: String? = null,
    onOpenQualityVideo: () -> Unit = {},
    onOpenQualityDefault: () -> Unit = {},
    isCropMode: Boolean = false,
    onToggleCropMode: () -> Unit = {},
    playbackSpeed: Float = 1f,
    onSetPlaybackSpeed: (Float) -> Unit = {},
    isAutoAdvance: Boolean = false,
    onToggleAutoAdvance: () -> Unit = {},
    sleepTimerValue: String = "Выкл",
    onCycleSleepTimer: () -> Unit = {},
    isIncognito: Boolean = false,
    onToggleIncognito: () -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPartial: Boolean = false,
    downloadSizeText: String? = null,
    onDownloadCurrent: (() -> Unit)? = null,
    preloadWifiOnly: Boolean = false,
    onTogglePreloadWifiOnly: () -> Unit = {},
    dataSaverEnabled: Boolean = false,
    onToggleDataSaver: () -> Unit = {},
    offlineUsedText: String = "148.6 МБ",
    onClearVideoCache: () -> Unit = {},
    onSelectClip: (Int) -> Unit = {},
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val tabTitles = listOf("Плеер", "Офлайн & Сеть", "Плейлист")
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    // Centered frost window (AGENTS canon for settings modals) instead of the side
    // slide-over: the full-screen black scrim read as a dead black block over the video.
    // Tabs 0/1 scroll internally and the playlist is a LazyColumn, so the dialog content
    // must stay a bounded, non-scrolling area (innerScroll would hand it infinite height).
    AuroraFrostDialog(
        onDismiss = onDismiss,
        title = when (selectedTab) {
            0 -> "Параметры плеера"
            1 -> "Офлайн & Сеть Tadami"
            else -> "Плейлист источника"
        },
        innerScroll = false,
    ) {
        AuroraCapsuleTabs(
            titles = tabTitles,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // Tab Contents
        when (selectedTab) {
            0 -> PlayerTabContent(
                isHdQuality = isHdQuality,
                onToggleQuality = onToggleQuality,
                qualityModeEnabled = qualityModeEnabled,
                qualityLabel = qualityLabel,
                onOpenQualityVideo = onOpenQualityVideo,
                onOpenQualityDefault = onOpenQualityDefault,
                isCropMode = isCropMode,
                onToggleCropMode = onToggleCropMode,
                playbackSpeed = playbackSpeed,
                speeds = speeds,
                onSetPlaybackSpeed = onSetPlaybackSpeed,
                isAutoAdvance = isAutoAdvance,
                onToggleAutoAdvance = onToggleAutoAdvance,
                sleepTimerValue = sleepTimerValue,
                onCycleSleepTimer = onCycleSleepTimer,
                isIncognito = isIncognito,
                onToggleIncognito = onToggleIncognito,
            )
            1 -> NetworkOfflineTabContent(
                currentItem = currentItem,
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                isPartial = isPartial,
                downloadSizeText = downloadSizeText,
                onDownloadCurrent = onDownloadCurrent,
                preloadWifiOnly = preloadWifiOnly,
                onTogglePreloadWifiOnly = onTogglePreloadWifiOnly,
                dataSaverEnabled = dataSaverEnabled,
                onToggleDataSaver = onToggleDataSaver,
                offlineUsedText = offlineUsedText,
                onClearVideoCache = onClearVideoCache,
            )
            2 -> PlaylistTabContent(
                items = playlistItems,
                currentIndex = currentPlaylistIndex,
                onSelectClip = {
                    onSelectClip(it)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun PlayerTabContent(
    isHdQuality: Boolean,
    onToggleQuality: () -> Unit,
    qualityModeEnabled: Boolean,
    qualityLabel: String?,
    onOpenQualityVideo: () -> Unit,
    onOpenQualityDefault: () -> Unit,
    isCropMode: Boolean,
    onToggleCropMode: () -> Unit,
    playbackSpeed: Float,
    speeds: List<Float>,
    onSetPlaybackSpeed: (Float) -> Unit,
    isAutoAdvance: Boolean,
    onToggleAutoAdvance: () -> Unit,
    sleepTimerValue: String,
    onCycleSleepTimer: () -> Unit,
    isIncognito: Boolean,
    onToggleIncognito: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Card: Видео и Качество
        FrostCard(title = "ВИДЕО И КАЧЕСТВО") {
            if (qualityModeEnabled) {
                FrostNavRow(
                    icon = Icons.Filled.HighQuality,
                    title = stringResource(AYMR.strings.reels_quality_video),
                    subtitle = qualityLabel ?: stringResource(AYMR.strings.reels_quality_loading),
                    onClick = onOpenQualityVideo,
                )
                FrostNavRow(
                    icon = Icons.Outlined.AspectRatio,
                    title = stringResource(AYMR.strings.reels_quality_default),
                    subtitle = stringResource(AYMR.strings.reels_quality_global_description),
                    onClick = onOpenQualityDefault,
                )
            } else {
                FrostSwitchRow(
                    icon = Icons.Filled.HighQuality,
                    title = "Высокое качество (HD)",
                    subtitle = if (isHdQuality) {
                        "1080p 60fps • Максимальный битрейт"
                    } else {
                        "480p SD • Экономия трафика"
                    },
                    checked = isHdQuality,
                    onCheckedChange = onToggleQuality,
                )
            }
            FrostSwitchRow(
                icon = Icons.Outlined.AspectRatio,
                title = "Режим масштаба кадра",
                subtitle = if (isCropMode) "Заполнение экрана (Crop)" else "Полный кадр (Fit Letterbox)",
                checked = isCropMode,
                onCheckedChange = onToggleCropMode,
            )
        }

        // Card: Скорость воспроизведения
        FrostCard(title = "СКОРОСТЬ ВОСПРОИЗВЕДЕНИЯ") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                speeds.forEach { speed ->
                    val isSelected = (playbackSpeed == speed)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) {
                                    AuroraTheme.colors.accent.copy(
                                        alpha = 0.22f,
                                    )
                                } else {
                                    Color.White.copy(alpha = 0.05f)
                                },
                            )
                            .border(
                                1.dp,
                                if (isSelected) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onSetPlaybackSpeed(speed) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${speed}x",
                            color = if (isSelected) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Card: Автоматизация & Сон
        FrostCard(title = "АВТОМАТИЗАЦИЯ & СОН") {
            FrostSwitchRow(
                icon = Icons.Filled.PlayArrow,
                title = "Автопереход (Auto-Advance)",
                subtitle = "Переходить к следующему видео по завершении",
                checked = isAutoAdvance,
                onCheckedChange = onToggleAutoAdvance,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCycleSleepTimer)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = AuroraTheme.colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Column {
                        Text(
                            text = "Таймер сна",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Текущее значение: $sleepTimerValue",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                        )
                    }
                }
                Text(
                    text = sleepTimerValue,
                    color = AuroraTheme.colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Card: Приватность ленты
        FrostCard(title = "ПРИВАТНОСТЬ ЛЕНТЫ") {
            FrostSwitchRow(
                icon = Icons.Filled.VisibilityOff,
                title = "Reels Инкогнито",
                subtitle = "Не записывать просмотры в историю Tadami",
                checked = isIncognito,
                onCheckedChange = onToggleIncognito,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NetworkOfflineTabContent(
    currentItem: ShortVideoItem?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPartial: Boolean,
    downloadSizeText: String?,
    onDownloadCurrent: (() -> Unit)?,
    preloadWifiOnly: Boolean,
    onTogglePreloadWifiOnly: () -> Unit,
    dataSaverEnabled: Boolean,
    onToggleDataSaver: () -> Unit,
    offlineUsedText: String,
    onClearVideoCache: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Card: Текущий видеоролик
        FrostCard(title = stringResource(MR.strings.reels_download_current)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text =
                        currentItem?.title ?: currentItem?.author
                            ?: stringResource(MR.strings.reels_download_current),
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!downloadSizeText.isNullOrBlank()) {
                        Text(
                            text = downloadSizeText,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                        )
                    }
                }
                Button(
                    onClick = { onDownloadCurrent?.invoke() },
                    enabled = onDownloadCurrent != null,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDownloaded) {
                            Color(
                                0xFFFF3366,
                            ).copy(alpha = 0.22f)
                        } else {
                            AuroraTheme.colors.accent
                        },
                        contentColor = if (isDownloaded) Color(0xFFFF3366) else Color.Black,
                    ),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = stringResource(
                            when {
                                isDownloading -> MR.strings.reels_download_cancel
                                isPartial -> MR.strings.reels_download_resume
                                isDownloaded -> MR.strings.reels_download_open
                                else -> MR.strings.reels_download_video
                            },
                        ),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Card: Политика предзагрузки
        FrostCard(title = "ПОЛИТИКА ПРЕДЗАГРУЗКИ") {
            FrostSwitchRow(
                icon = Icons.Filled.Wifi,
                title = "Предзагрузка только по Wi-Fi",
                subtitle = "Экономия мобильного интернета в дороге",
                checked = preloadWifiOnly,
                onCheckedChange = onTogglePreloadWifiOnly,
            )
            FrostSwitchRow(
                icon = Icons.Filled.DataSaverOn,
                title = "Экономия трафика (Data Saver)",
                subtitle = "Принудительно загружать пониженное качество",
                checked = dataSaverEnabled,
                onCheckedChange = onToggleDataSaver,
            )
        }

        // Card: Офлайн-хранилище
        FrostCard(title = "ОФЛАЙН-ХРАНИЛИЩЕ") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Занято кэшем Reels:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Text(
                    text = offlineUsedText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Storage Bar Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.28f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(AuroraTheme.colors.accent),
                )
            }
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = onClearVideoCache,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(36.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Очистить кэш видео",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PlaylistTabContent(
    items: List<ShortVideoItem>,
    currentIndex: Int,
    onSelectClip: (Int) -> Unit,
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "В подборке нет роликов",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items) { index, clip ->
            val isCurrent = (index == currentIndex)
            val cardShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(
                        if (isCurrent) {
                            AuroraTheme.colors.accent.copy(
                                alpha = 0.14f,
                            )
                        } else {
                            Color.White.copy(alpha = 0.04f)
                        },
                        cardShape,
                    )
                    .border(
                        1.dp,
                        if (isCurrent) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.08f),
                        cardShape,
                    )
                    .clickable { onSelectClip(index) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Thumbnail Poster
                val context = LocalContext.current
                val posterUrl = clip.posterUrlVertical ?: clip.posterUrl
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = (clip.author ?: clip.title.orEmpty()).take(1).uppercase(),
                            color = AuroraTheme.colors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Info Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val durationSec = clip.durationSec?.roundToInt() ?: 0
                    Text(
                        text = "@${clip.author ?: "автор"} • ${durationSec}с",
                        color = AuroraTheme.colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = clip.title ?: "Без названия",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val views = clip.viewsCount?.let { formatCompactCount(it) }
                    if (views != null) {
                        Text(
                            text = "👁 $views просмотров",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color.White.copy(alpha = 0.04f), cardShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
        )
        content()
    }
}

@Composable
private fun FrostNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AuroraTheme.colors.accent,
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
            )
        }
        Text(
            text = "›",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun FrostSwitchRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AuroraTheme.colors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
            }
        }
        // Custom Glass Toggle Pill
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 22.dp)
                .clip(CircleShape)
                .background(
                    if (checked) AuroraTheme.colors.accent else Color.White.copy(alpha = 0.15f),
                    CircleShape,
                )
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.Black else Color.White, CircleShape),
            )
        }
    }
}
