package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.ui.reels.ReelsAlbumsSort
import eu.kanade.tachiyomi.ui.reels.filterAndSortReelsAlbums
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

internal fun shouldShowAuroraReelsLibrarySection(
    hasReelsSources: Boolean,
    showReelsVideoFeed: Boolean,
    showReelsInLibrary: Boolean,
): Boolean = hasReelsSources && showReelsVideoFeed && showReelsInLibrary

internal fun resolveFallbackAuroraLibrarySection(
    availableSections: List<AnimeLibraryTab.Section>,
): AnimeLibraryTab.Section {
    return AnimeLibraryTab.Section.Anime.takeIf { it in availableSections }
        ?: availableSections.firstOrNull()
        ?: AnimeLibraryTab.Section.Anime
}

internal fun filterReelsLibraryAlbums(
    albums: List<ReelsAlbum>,
    selectedSourceId: Long?,
    searchQuery: String?,
    sort: ReelsAlbumsSort,
    sourceNames: Map<Long, String>,
): List<ReelsAlbum> = filterAndSortReelsAlbums(
    albums = albums,
    selectedSourceId = selectedSourceId,
    searchQuery = searchQuery.orEmpty(),
    sort = sort,
    sourceNames = sourceNames,
)

/**
 * Isolated Reels Albums section rendered inside Aurora Library alongside Anime / Manga / Novel.
 * Reads exclusively from [tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository] without
 * creating any rows in `animes`, categories, library update jobs, stats, or manga/anime backups.
 */
@Composable
internal fun ReelsLibraryAlbumsSection(
    albums: List<ReelsAlbum>,
    hasLoaded: Boolean,
    searchQuery: String?,
    selectedSourceId: Long?,
    onSelectSource: (Long?) -> Unit,
    sort: ReelsAlbumsSort,
    onSelectSort: (ReelsAlbumsSort) -> Unit,
    sourceNames: Map<Long, String>,
    sourceIcons: Map<Long, ImageBitmap>,
    missingSourceIds: Set<Long>,
    showFilterDialog: Boolean,
    onDismissFilterDialog: () -> Unit,
    onOpenAlbum: (ReelsAlbum) -> Unit,
    onPlayAlbum: (ReelsAlbum) -> Unit,
    onRemoveAlbum: (ReelsAlbum) -> Unit,
    onCleanupMissingSources: () -> Unit,
    onDiscoverAlbums: () -> Unit,
    contentPadding: PaddingValues,
    gridState: LazyGridState,
) {
    val colors = AuroraTheme.colors
    val adaptiveSpec = rememberAuroraAdaptiveSpec()
    var actionTargetAlbum by remember { mutableStateOf<ReelsAlbum?>(null) }
    var confirmCleanup by remember { mutableStateOf(false) }

    val availableSourceChips = remember(albums, sourceNames) {
        albums.map { it.sourceId }.distinct().sortedBy { sourceNames[it].orEmpty().lowercase() }
    }
    val visibleAlbums = remember(albums, selectedSourceId, searchQuery, sort, sourceNames) {
        filterReelsLibraryAlbums(
            albums = albums,
            selectedSourceId = selectedSourceId,
            searchQuery = searchQuery,
            sort = sort,
            sourceNames = sourceNames,
        )
    }

    if (showFilterDialog) {
        ReelsLibraryFilterDialog(
            sort = sort,
            hasMissingSources = missingSourceIds.isNotEmpty(),
            onSelectSort = onSelectSort,
            onRequestCleanup = {
                onDismissFilterDialog()
                confirmCleanup = true
            },
            onDiscoverAlbums = {
                onDismissFilterDialog()
                onDiscoverAlbums()
            },
            onDismiss = onDismissFilterDialog,
        )
    }

    if (confirmCleanup) {
        AuroraFrostDialog(
            onDismiss = { confirmCleanup = false },
            title = stringResource(MR.strings.reels_cleanup_missing),
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = { confirmCleanup = false },
                )
                AuroraFrostConfirm(
                    label = stringResource(MR.strings.action_remove),
                    onClick = {
                        confirmCleanup = false
                        onCleanupMissingSources()
                    },
                )
            },
        ) {
            Text(
                text = stringResource(MR.strings.reels_albums_cleanup_confirm),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
        }
    }

    actionTargetAlbum?.let { album ->
        val sourceName = sourceNames[album.sourceId] ?: stringResource(MR.strings.reels_unknown_source)
        AuroraFrostDialog(
            onDismiss = { actionTargetAlbum = null },
            title = album.name,
            contentSpacing = 8.dp,
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = { actionTargetAlbum = null },
                )
            },
        ) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ReelsLibraryDialogActionRow(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                label = stringResource(MR.strings.reels_album_open),
                onClick = {
                    actionTargetAlbum = null
                    onOpenAlbum(album)
                },
            )
            ReelsLibraryDialogActionRow(
                icon = Icons.Filled.PlayArrow,
                label = stringResource(MR.strings.reels_album_play_as_feed),
                onClick = {
                    actionTargetAlbum = null
                    onPlayAlbum(album)
                },
            )
            ReelsLibraryDialogActionRow(
                icon = Icons.Outlined.DeleteOutline,
                label = stringResource(MR.strings.reels_album_remove),
                isDestructive = true,
                onClick = {
                    actionTargetAlbum = null
                    onRemoveAlbum(album)
                },
            )
            ReelsLibraryDialogActionRow(
                icon = Icons.Filled.Explore,
                label = stringResource(MR.strings.reels_albums_manage_action),
                onClick = {
                    actionTargetAlbum = null
                    onDiscoverAlbums()
                },
            )
        }
    }

    when {
        !hasLoaded -> LoadingScreen(Modifier.padding(contentPadding))
        albums.isEmpty() -> {
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(MR.strings.reels_albums_empty),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(MR.strings.reels_albums_library_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDiscoverAlbums,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colors.isEInk) colors.textPrimary else colors.accent,
                        contentColor = colors.textOnAccent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(MR.strings.reels_albums_discover_action))
                }
            }
        }
        visibleAlbums.isEmpty() -> {
            EmptyScreen(
                stringRes = MR.strings.no_results_found,
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
                actions = persistentListOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.reels_albums_discover_action,
                        icon = Icons.Filled.Explore,
                        onClick = onDiscoverAlbums,
                    ),
                ),
            )
        }
        else -> {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(adaptiveSpec.compactGridAdaptiveMinCellDp.dp),
                contentPadding = contentPadding + PaddingValues(
                    horizontal = adaptiveSpec.contentHorizontalPaddingDp.dp,
                    vertical = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(
                    key = "reels-library-controls",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReelsSourceFilterPill(
                            label = stringResource(MR.strings.reels_search_tab_all),
                            icon = null,
                            selected = selectedSourceId == null,
                            onClick = { onSelectSource(null) },
                        )
                        if (availableSourceChips.size > 1 || selectedSourceId != null) {
                            availableSourceChips.forEach { sid ->
                                val label = sourceNames[sid] ?: stringResource(MR.strings.reels_unknown_source)
                                ReelsSourceFilterPill(
                                    label = label,
                                    icon = sourceIcons[sid],
                                    selected = selectedSourceId == sid,
                                    onClick = {
                                        onSelectSource(if (selectedSourceId == sid) null else sid)
                                    },
                                )
                            }
                        }
                        ReelsSourceFilterPill(
                            label = stringResource(MR.strings.reels_albums_manage_action),
                            icon = null,
                            leadingVector = Icons.Filled.Explore,
                            selected = false,
                            onClick = onDiscoverAlbums,
                        )
                    }
                }

                items(
                    items = visibleAlbums,
                    key = { "${it.sourceId}:${it.albumId}" },
                ) { album ->
                    val sourceName = sourceNames[album.sourceId] ?: stringResource(MR.strings.reels_unknown_source)
                    val isMissing = album.sourceId in missingSourceIds
                    ReelsLibraryAlbumCard(
                        album = album,
                        sourceName = sourceName,
                        sourceIcon = sourceIcons[album.sourceId],
                        isMissingSource = isMissing,
                        onClick = { onOpenAlbum(album) },
                        onPlay = { onPlayAlbum(album) },
                        onLongClick = { actionTargetAlbum = album },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReelsLibraryAlbumCard(
    album: ReelsAlbum,
    sourceName: String,
    sourceIcon: ImageBitmap?,
    isMissingSource: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val cardShape = RoundedCornerShape(14.dp)
    val borderColor = if (colors.isEInk) {
        colors.divider
    } else if (isMissingSource) {
        colors.error.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = if (colors.isDark) 0.10f else 0.35f)
    }
    val containerColor = if (colors.isEInk) {
        colors.surface
    } else {
        colors.cardBackground
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(cardShape)
            .background(containerColor)
            .border(1.dp, borderColor, cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (!album.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = colors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.Center),
            )
        }

        // Source badge (top-start) + 1-tap Play action (top-end)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val badgeBg = when {
                colors.isEInk -> colors.surface
                isMissingSource -> colors.error.copy(alpha = 0.85f)
                else -> Color.Black.copy(alpha = 0.62f)
            }
            val badgeText = when {
                colors.isEInk -> colors.textPrimary
                else -> Color.White
            }
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(CircleShape)
                    .background(badgeBg)
                    .then(
                        if (colors.isEInk) {
                            Modifier.border(1.dp, colors.divider, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (sourceIcon != null) {
                    Image(
                        bitmap = sourceIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(
                    text = sourceName,
                    color = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val playBg = if (colors.isEInk) colors.surface else Color.Black.copy(alpha = 0.62f)
            val playTint = if (colors.isEInk) colors.textPrimary else Color.White
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(playBg)
                    .then(
                        if (colors.isEInk) {
                            Modifier.border(1.dp, colors.divider, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(MR.strings.reels_album_play_as_feed),
                    tint = playTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Album title footer
        if (colors.isEInk) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(colors.surface)
                    .border(1.dp, colors.divider)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = album.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 8.dp),
            ) {
                Text(
                    text = album.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReelsSourceFilterPill(
    label: String,
    icon: ImageBitmap?,
    leadingVector: ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val bg = when {
        colors.isEInk && selected -> colors.textPrimary
        colors.isEInk -> colors.surface
        selected -> colors.accent.copy(alpha = 0.22f)
        else -> colors.cardBackground
    }
    val border = when {
        colors.isEInk -> colors.divider
        selected -> colors.accent.copy(alpha = 0.55f)
        else -> colors.divider
    }
    val textColor = when {
        colors.isEInk && selected -> colors.textOnAccent
        colors.isEInk -> colors.textPrimary
        selected -> colors.textPrimary
        else -> colors.textSecondary
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else if (leadingVector != null) {
            Icon(
                imageVector = leadingVector,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReelsLibraryFilterDialog(
    sort: ReelsAlbumsSort,
    hasMissingSources: Boolean,
    onSelectSort: (ReelsAlbumsSort) -> Unit,
    onRequestCleanup: () -> Unit,
    onDiscoverAlbums: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AuroraTheme.colors
    AuroraFrostDialog(
        onDismiss = onDismiss,
        title = stringResource(MR.strings.action_sort),
        contentSpacing = 6.dp,
        footer = {
            AuroraFrostCancel(
                label = stringResource(MR.strings.action_cancel),
                onClick = onDismiss,
            )
        },
    ) {
        ReelsAlbumsSort.entries.forEach { option ->
            val label = when (option) {
                ReelsAlbumsSort.DateDesc -> stringResource(MR.strings.reels_sort_newest)
                ReelsAlbumsSort.DateAsc -> stringResource(MR.strings.reels_sort_oldest)
                ReelsAlbumsSort.Name -> stringResource(MR.strings.action_sort_alpha)
                ReelsAlbumsSort.Source -> stringResource(MR.strings.reels_sort_source)
            }
            val selected = sort == option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            if (colors.isEInk) colors.accentVariant else colors.accent.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable {
                        onSelectSort(option)
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (colors.isEInk) colors.textPrimary else colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (hasMissingSources) {
            ReelsLibraryDialogActionRow(
                icon = Icons.Outlined.DeleteSweep,
                label = stringResource(MR.strings.reels_cleanup_missing),
                isDestructive = true,
                onClick = onRequestCleanup,
            )
        }
        ReelsLibraryDialogActionRow(
            icon = Icons.Filled.Explore,
            label = stringResource(MR.strings.reels_albums_manage_action),
            onClick = onDiscoverAlbums,
        )
    }
}

@Composable
private fun ReelsLibraryDialogActionRow(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val tint = if (isDestructive) colors.error else colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}
