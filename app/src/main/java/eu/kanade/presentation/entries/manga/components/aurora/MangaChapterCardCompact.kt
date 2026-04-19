package eu.kanade.presentation.entries.manga.components.aurora

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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
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
import coil3.request.crossfade
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.aurora.AURORA_DIMMED_ITEM_ALPHA
import eu.kanade.presentation.entries.components.aurora.AURORA_NEW_ITEM_HIGHLIGHT_ALPHA
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * Compact chapter card with 40x40 thumbnail and minimal design.
 */
@Composable
fun MangaChapterCardCompact(
    manga: Manga,
    item: ChapterList.Item,
    selected: Boolean,
    isNew: Boolean,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onLongClick: () -> Unit,
    onChapterSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val chapter = item.chapter
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val cardAlpha = if (chapter.read) AURORA_DIMMED_ITEM_ALPHA else 1f
    val cardTint = if (isNew && !chapter.read) {
        colors.accent.copy(alpha = AURORA_NEW_ITEM_HIGHLIGHT_ALPHA)
    } else {
        null
    }
    val scanlator = chapter.scanlator?.trim()?.takeIf(String::isNotEmpty)
    val startSwipeAction = auroraMangaSwipeAction(
        action = chapterSwipeStartAction,
        read = chapter.read,
        bookmark = chapter.bookmark,
        downloadState = item.downloadState,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onChapterSwipe(chapterSwipeStartAction) },
    )
    val endSwipeAction = auroraMangaSwipeAction(
        action = chapterSwipeEndAction,
        read = chapter.read,
        bookmark = chapter.bookmark,
        downloadState = item.downloadState,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onChapterSwipe(chapterSwipeEndAction) },
    )

    val chapterCard: @Composable () -> Unit = {
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
                        onClick = { onChapterClicked(chapter) },
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
                    val coverRequest = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
                        buildAuroraCoverImageRequest(context, manga.asMangaCover()) {
                            crossfade(true)
                            size(40)
                        }
                    }
                    AsyncImage(
                        model = coverRequest,
                        error = placeholderPainter,
                        fallback = placeholderPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp),
                        alpha = 1f,
                    )
                }

                // Chapter info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = chapter.name,
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
                        val uploadDateText = relativeDateTimeText(chapter.dateUpload)

                        Text(
                            text = uploadDateText,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )

                        if (scanlator != null) {
                            DotSeparatorText()
                            Text(
                                text = scanlator,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (chapter.bookmark) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AuroraChapterStatusBadge(
                                status = AuroraChapterStatus.Bookmark,
                                icon = Icons.Outlined.BookmarkAdd,
                                label = null,
                            )
                        }
                    }

                    // Progress bar for read chapters
                    if (chapter.read) {
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
                    if (onDownloadChapter != null && !isAnyChapterSelected) {
                        ChapterDownloadIndicator(
                            enabled = true,
                            downloadStateProvider = { item.downloadState },
                            downloadProgressProvider = { item.downloadProgress },
                            onClick = { onDownloadChapter(listOf(item), it) },
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Read checkmark
                    if (chapter.read) {
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

    if (isAnyChapterSelected) {
        chapterCard()
    } else {
        SwipeableActionsBox(
            modifier = Modifier.clipToBounds(),
            startActions = listOfNotNull(startSwipeAction),
            endActions = listOfNotNull(endSwipeAction),
            swipeThreshold = auroraSwipeActionThreshold,
            backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            chapterCard()
        }
    }
}

@Composable
private fun AuroraChapterStatusBadge(
    status: AuroraChapterStatus,
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
        if (shouldShowAuroraChapterStatusLabel(status) && label != null) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

internal enum class AuroraChapterStatus {
    Bookmark,
}

internal fun shouldShowAuroraChapterStatusLabel(status: AuroraChapterStatus): Boolean {
    return status != AuroraChapterStatus.Bookmark
}

private fun auroraMangaSwipeAction(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: MangaDownload.State,
    background: Color,
    onSwipe: () -> Unit,
): me.saket.swipe.SwipeAction? {
    return when (action) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> auroraSwipeAction(
            icon = if (!read) Icons.Outlined.Done else Icons.Outlined.RemoveDone,
            background = background,
            isUndo = read,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> auroraSwipeAction(
            icon = if (!bookmark) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkRemove,
            background = background,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Download -> auroraSwipeAction(
            icon = when (downloadState) {
                MangaDownload.State.NOT_DOWNLOADED, MangaDownload.State.ERROR -> Icons.Outlined.Download
                MangaDownload.State.QUEUE, MangaDownload.State.DOWNLOADING -> Icons.Outlined.FileDownloadOff
                MangaDownload.State.DOWNLOADED -> Icons.Outlined.Delete
            },
            background = background,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Disabled -> null
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
