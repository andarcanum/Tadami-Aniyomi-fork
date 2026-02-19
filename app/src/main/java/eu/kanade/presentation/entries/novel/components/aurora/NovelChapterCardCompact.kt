package eu.kanade.presentation.entries.novel.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.entries.novel.novelSwipeAction
import eu.kanade.presentation.entries.novel.novelSwipeActionThreshold
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.formatChapterNumber
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelChapterCardCompact(
    novel: Novel,
    chapter: NovelChapter,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleDownload: () -> Unit,
    chapterSwipeStartAction: LibraryPreferences.NovelSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.NovelSwipeAction,
    onChapterSwipe: (LibraryPreferences.NovelSwipeAction) -> Unit,
    downloaded: Boolean,
    downloading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val title = when (novel.displayMode) {
        Novel.CHAPTER_DISPLAY_NUMBER -> stringResource(
            MR.strings.display_mode_chapter,
            formatChapterNumber(chapter.chapterNumber),
        )
        else -> chapter.name.ifBlank {
            stringResource(MR.strings.display_mode_chapter, formatChapterNumber(chapter.chapterNumber))
        }
    }

    val chapterCard: @Composable () -> Unit = {
        GlassmorphismCard(
            modifier = modifier,
            cornerRadius = 16.dp,
            verticalPadding = 2.dp,
            innerPadding = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) colors.accent.copy(alpha = 0.16f) else Color.Transparent,
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = if (chapter.read) 0.26f else 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatChapterNumber(chapter.chapterNumber),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (chapter.read) colors.textSecondary else colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (chapter.dateUpload > 0) {
                            relativeDateTimeText(chapter.dateUpload)
                        } else {
                            stringResource(MR.strings.unknown)
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }

                if (!selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.24f))
                            .clickable(onClick = onToggleDownload),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.accent,
                            )
                        } else {
                            Icon(
                                imageVector = if (downloaded) Icons.Outlined.Delete else Icons.Outlined.Download,
                                contentDescription = null,
                                tint = if (downloaded) colors.error else colors.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.24f))
                            .clickable(onClick = onToggleBookmark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bookmark,
                            contentDescription = null,
                            tint = if (chapter.bookmark) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.24f))
                            .clickable(onClick = onToggleRead),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (chapter.read) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (!selectionMode) {
        val startSwipeAction = novelSwipeAction(
            action = chapterSwipeStartAction,
            read = chapter.read,
            bookmark = chapter.bookmark,
            downloaded = downloaded,
            downloading = downloading,
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = { onChapterSwipe(chapterSwipeStartAction) },
        )
        val endSwipeAction = novelSwipeAction(
            action = chapterSwipeEndAction,
            read = chapter.read,
            bookmark = chapter.bookmark,
            downloaded = downloaded,
            downloading = downloading,
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = { onChapterSwipe(chapterSwipeEndAction) },
        )

        SwipeableActionsBox(
            modifier = Modifier.clipToBounds(),
            startActions = listOfNotNull(startSwipeAction),
            endActions = listOfNotNull(endSwipeAction),
            swipeThreshold = novelSwipeActionThreshold,
            backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            chapterCard()
        }
    } else {
        chapterCard()
    }
}
