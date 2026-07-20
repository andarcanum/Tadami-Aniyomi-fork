package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.filled.Circle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadIndicator
import eu.kanade.presentation.entries.anime.components.isLikelyEpisodeDescription
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.components.aurora.AuroraCompactEntryRowCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.milliseconds

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
    showPreviews: Boolean = true,
    showSummaries: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val episode = item.episode
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
        AuroraCompactEntryRowCard(
            modifier = modifier,
            selected = selected,
            highlighted = isNew && !episode.seen,
            dimmed = episode.seen,
            cornerRadius = 20.dp,
            outerVerticalPadding = 6.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            onClick = onClick,
            onLongClick = onLongClick,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val showPreviewImage = showPreviews
                    if (showPreviewImage) {
                        val targetWidth = 112.dp
                        val imageData = if (!episode.previewUrl.isNullOrBlank()) {
                            episode.previewUrl
                        } else {
                            AnimeCover(
                                animeId = anime.id,
                                sourceId = anime.source,
                                isAnimeFavorite = anime.favorite,
                                url = anime.thumbnailUrl,
                                lastModified = anime.coverLastModified,
                            )
                        }
                        ItemCover.Thumb(
                            data = imageData,
                            modifier = Modifier
                                .width(targetWidth)
                                .clip(RoundedCornerShape(8.dp)),
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val summaryText = episode.summary.takeIf { !it.isNullOrBlank() && showSummaries }
                            ?: episode.scanlator.takeIf {
                                !it.isNullOrBlank() &&
                                    showSummaries &&
                                    it.isLikelyEpisodeDescription()
                            }
                        if (summaryText != null) {
                            var expandSummary by remember { mutableStateOf(false) }
                            Text(
                                text = summaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textSecondary.copy(alpha = 0.8f),
                                maxLines = if (expandSummary) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }

                        // Meta info row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (!episode.seen) {
                                Icon(
                                    imageVector = Icons.Filled.Circle,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(6.dp),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                            }
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
                    }

                    // Actions column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.align(Alignment.CenterVertically),
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

                // Progress bar for seen/in-progress episodes (starts under the poster!)
                if (episode.seen || episode.totalSeconds > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.divider),
                        ) {
                            val progress = if (episode.seen) {
                                1f
                            } else {
                                (
                                    episode.lastSecondSeen.toFloat() /
                                        maxOf(1L, episode.totalSeconds).toFloat()
                                    ).coerceIn(0f, 1f)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .background(colors.accent),
                            )
                        }

                        if (episode.totalSeconds > 0L) {
                            val totalDurationText = episode.totalSeconds.milliseconds.toDigitalString()
                            val durationText = if (!episode.seen && episode.lastSecondSeen > 0L) {
                                val lastSeenDurationText = episode.lastSecondSeen.milliseconds.toDigitalString()
                                "$lastSeenDurationText / $totalDurationText"
                            } else {
                                totalDurationText
                            }
                            Text(
                                text = durationText,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
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
            backgroundUntilSwipeThreshold = Color.Transparent,
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
    InProgress,
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

private fun kotlin.time.Duration.toDigitalString(): String {
    return toComponents { _, hours, minutes, seconds, _ ->
        val minutesStr = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
        val secondsStr = seconds.toString().padStart(2, '0')
        if (hours > 0) {
            "$hours:$minutesStr:$secondsStr"
        } else {
            "$minutes:$secondsStr"
        }
    }
}
