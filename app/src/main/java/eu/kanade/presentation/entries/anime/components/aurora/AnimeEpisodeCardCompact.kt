package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.NewLabel
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraCoverModel
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadIndicator
import eu.kanade.presentation.entries.components.aurora.AURORA_DIMMED_ITEM_ALPHA
import eu.kanade.presentation.entries.components.aurora.AURORA_NEW_ITEM_HIGHLIGHT_ALPHA
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Compact episode card with 40x40 thumbnail and minimal design.
 */
@Composable
fun AnimeEpisodeCardCompact(
    anime: Anime,
    item: EpisodeList.Item,
    selected: Boolean,
    isNew: Boolean,
    isAnyEpisodeSelected: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEpisodeSwipe: (LibraryPreferences.EpisodeSwipeAction) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val episode = item.episode
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val cardAlpha = if (episode.seen) AURORA_DIMMED_ITEM_ALPHA else 1f
    val cardTint = if (isNew && !episode.seen) {
        colors.accent.copy(alpha = AURORA_NEW_ITEM_HIGHLIGHT_ALPHA)
    } else {
        null
    }
    val startSwipeAction = auroraAnimeSwipeAction(
        action = episodeSwipeStartAction,
        seen = episode.seen,
        bookmark = episode.bookmark,
        fillermark = episode.fillermark,
        downloadState = item.downloadState,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onEpisodeSwipe(episodeSwipeStartAction) },
    )
    val endSwipeAction = auroraAnimeSwipeAction(
        action = episodeSwipeEndAction,
        seen = episode.seen,
        bookmark = episode.bookmark,
        fillermark = episode.fillermark,
        downloadState = item.downloadState,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onEpisodeSwipe(episodeSwipeEndAction) },
    )

    val episodeCard: @Composable () -> Unit = {
        GlassmorphismCard(
            modifier = modifier.alpha(cardAlpha),
            cornerRadius = 16.dp,
            verticalPadding = 4.dp,
            innerPadding = 12.dp,
            overlayColor = cardTint,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 40x40 thumbnail
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                ) {
                    AsyncImage(
                        model = remember(anime.id, anime.thumbnailUrl, anime.coverLastModified) {
                            ImageRequest.Builder(context)
                                .data(resolveAuroraCoverModel(anime.asAnimeCover()))
                                .placeholderMemoryCacheKey(anime.thumbnailUrl)
                                .crossfade(true)
                                .size(40)
                                .build()
                        },
                        error = placeholderPainter,
                        fallback = placeholderPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp),
                        alpha = 1f,
                    )
                }

                // Episode info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = episode.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Meta info row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(12.dp),
                        )

                        // Format upload date
                        val uploadDateText = relativeDateTimeText(episode.dateUpload)

                        Text(
                            text = uploadDateText,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (episode.bookmark) {
                            AuroraEpisodeStatusBadge(
                                status = AuroraEpisodeStatus.Bookmark,
                                icon = Icons.Outlined.BookmarkAdd,
                                label = null,
                            )
                        }
                        if (episode.fillermark) {
                            AuroraEpisodeStatusBadge(
                                status = AuroraEpisodeStatus.Fillermark,
                                icon = Icons.Outlined.NewLabel,
                                label = stringResource(AYMR.strings.aurora_episode_badge_filler),
                            )
                        }
                        if (episode.seen) {
                            AuroraEpisodeStatusBadge(
                                status = AuroraEpisodeStatus.Seen,
                                icon = Icons.Outlined.Done,
                                label = stringResource(AYMR.strings.aurora_episode_badge_seen),
                            )
                        }
                    }

                    // Progress bar for seen episodes
                    if (episode.seen) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.divider),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(colors.accent),
                            )
                        }
                    }
                }

                // Actions column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Download indicator
                    if (onDownloadEpisode != null && !isAnyEpisodeSelected) {
                        EpisodeDownloadIndicator(
                            enabled = true,
                            downloadStateProvider = { item.downloadState },
                            downloadProgressProvider = { item.downloadProgress },
                            onClick = { onDownloadEpisode(listOf(item), it) },
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Seen checkmark
                    if (episode.seen) {
                        Icon(
                            Icons.Outlined.Done,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    if (isAnyEpisodeSelected) {
        episodeCard()
    } else {
        SwipeableActionsBox(
            modifier = Modifier.clipToBounds(),
            startActions = listOfNotNull(startSwipeAction),
            endActions = listOfNotNull(endSwipeAction),
            swipeThreshold = auroraSwipeActionThreshold,
            backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            episodeCard()
        }
    }
}

@Composable
private fun AuroraEpisodeStatusBadge(
    status: AuroraEpisodeStatus,
    icon: ImageVector,
    label: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surface.copy(alpha = 0.32f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (label != null) 4.dp else 0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(12.dp),
        )
        if (shouldShowAuroraEpisodeStatusLabel(status) && label != null) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

internal enum class AuroraEpisodeStatus {
    Bookmark,
    Fillermark,
    Seen,
}

internal fun shouldShowAuroraEpisodeStatusLabel(status: AuroraEpisodeStatus): Boolean {
    return status != AuroraEpisodeStatus.Bookmark
}

private fun auroraAnimeSwipeAction(
    action: LibraryPreferences.EpisodeSwipeAction,
    seen: Boolean,
    bookmark: Boolean,
    fillermark: Boolean,
    downloadState: AnimeDownload.State,
    background: Color,
    onSwipe: () -> Unit,
): me.saket.swipe.SwipeAction? {
    return when (action) {
        LibraryPreferences.EpisodeSwipeAction.ToggleSeen -> auroraSwipeAction(
            icon = if (!seen) Icons.Outlined.Done else Icons.Outlined.RemoveDone,
            background = background,
            isUndo = seen,
            onSwipe = onSwipe,
        )
        LibraryPreferences.EpisodeSwipeAction.ToggleBookmark -> auroraSwipeAction(
            icon = if (!bookmark) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkRemove,
            background = background,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.EpisodeSwipeAction.ToggleFillermark -> auroraSwipeAction(
            icon = if (!fillermark) Icons.Outlined.NewLabel else Icons.AutoMirrored.Outlined.LabelOff,
            background = background,
            isUndo = fillermark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.EpisodeSwipeAction.Download -> auroraSwipeAction(
            icon = when (downloadState) {
                AnimeDownload.State.NOT_DOWNLOADED, AnimeDownload.State.ERROR -> Icons.Outlined.Download
                AnimeDownload.State.QUEUE, AnimeDownload.State.DOWNLOADING -> Icons.Outlined.FileDownloadOff
                AnimeDownload.State.DOWNLOADED -> Icons.Outlined.Delete
            },
            background = background,
            onSwipe = onSwipe,
        )
        LibraryPreferences.EpisodeSwipeAction.Disabled -> null
    }
}

private fun auroraSwipeAction(
    onSwipe: () -> Unit,
    icon: ImageVector,
    background: Color,
    isUndo: Boolean = false,
): me.saket.swipe.SwipeAction {
    return me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = icon,
                tint = contentColorFor(background),
                contentDescription = null,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}

private val auroraSwipeActionThreshold = 56.dp
