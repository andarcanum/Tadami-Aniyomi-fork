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
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.components.aurora.AURORA_DIMMED_ITEM_ALPHA
import eu.kanade.presentation.entries.components.aurora.AURORA_NEW_ITEM_HIGHLIGHT_ALPHA
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.entries.novel.components.NovelChapterActionButton
import eu.kanade.presentation.entries.novel.novelChapterDateText
import eu.kanade.presentation.entries.novel.novelSwipeAction
import eu.kanade.presentation.entries.novel.novelSwipeActionThreshold
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterActionIconState
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterActionUiState
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

object NovelChapterCardCompactUi {

    @Composable
    fun Render(
        novel: Novel,
        chapter: NovelChapter,
        displayNumber: Int? = null,
        titleOverride: String? = null,
        selected: Boolean,
        chapterActionState: NovelChapterActionUiState? = null,
        isNew: Boolean,
        selectionMode: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        onTranslateClick: () -> Unit,
        onTranslatedDownloadClick: () -> Unit,
        onTranslatedDownloadLongClick: () -> Unit,
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
        val chapterDisplayNumber = displayNumber?.toDouble() ?: chapter.chapterNumber
        val cardAlpha = if (chapter.read) AURORA_DIMMED_ITEM_ALPHA else 1f
        val cardTint = if (isNew && !chapter.read) {
            colors.accent.copy(alpha = AURORA_NEW_ITEM_HIGHLIGHT_ALPHA)
        } else {
            null
        }
        val title = titleOverride ?: when (novel.displayMode) {
            Novel.CHAPTER_DISPLAY_NUMBER -> stringResource(
                MR.strings.display_mode_chapter,
                formatChapterNumber(chapterDisplayNumber),
            )
            else -> chapter.name.ifBlank {
                stringResource(MR.strings.display_mode_chapter, formatChapterNumber(chapterDisplayNumber))
            }
        }

        val chapterCard: @Composable () -> Unit = {
            GlassmorphismCard(
                modifier = modifier.alpha(cardAlpha),
                cornerRadius = 16.dp,
                verticalPadding = 2.dp,
                innerPadding = 8.dp,
                overlayColor = cardTint,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
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
                            .background(colors.accent.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatChapterNumber(chapterDisplayNumber),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        novelChapterDateText(
                            chapter = chapter,
                            parsedDateText = if (chapter.dateUpload > 0L) {
                                relativeDateTimeText(chapter.dateUpload)
                            } else {
                                null
                            },
                        )?.let { dateText ->
                            Text(
                                text = dateText,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }

                    if (!selectionMode) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (chapterActionState?.showGeminiRow == true) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val translateState = chapterActionState.translateState
                                    NovelChapterActionButton(
                                        icon = Icons.Outlined.Translate,
                                        iconTint = when (translateState) {
                                            NovelChapterActionIconState.Active -> colors.accent
                                            NovelChapterActionIconState.InProgress -> colors.accent
                                            else -> colors.textSecondary
                                        },
                                        onClick = onTranslateClick,
                                        backgroundColor = colors.surface.copy(alpha = 0.24f),
                                        showProgress = translateState == NovelChapterActionIconState.InProgress,
                                        progressColor = colors.accent,
                                        size = 32.dp,
                                        iconSize = 18.dp,
                                    )
                                    val translatedDownloadState = chapterActionState.downloadTranslatedState
                                    NovelChapterActionButton(
                                        icon = Icons.Outlined.Download,
                                        iconTint = when (translatedDownloadState) {
                                            NovelChapterActionIconState.Active -> colors.accent
                                            NovelChapterActionIconState.InProgress -> colors.accent
                                            else -> colors.textSecondary
                                        },
                                        onClick = onTranslatedDownloadClick,
                                        onLongClick = onTranslatedDownloadLongClick,
                                        backgroundColor = colors.surface.copy(alpha = 0.24f),
                                        showProgress = translatedDownloadState == NovelChapterActionIconState.InProgress,
                                        progressColor = colors.accent,
                                        size = 32.dp,
                                        iconSize = 18.dp,
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                NovelChapterActionButton(
                                    icon = when {
                                        downloading -> Icons.Outlined.Delete
                                        downloaded -> Icons.Outlined.Download
                                        else -> Icons.Outlined.Download
                                    },
                                    iconTint = when {
                                        downloading -> colors.accent
                                        downloaded -> colors.error
                                        else -> colors.textSecondary
                                    },
                                    onClick = onToggleDownload,
                                    backgroundColor = colors.surface.copy(alpha = 0.24f),
                                    showProgress = downloading,
                                    progressColor = colors.accent,
                                    size = 32.dp,
                                    iconSize = 18.dp,
                                )
                                NovelChapterActionButton(
                                    icon = Icons.Outlined.Bookmark,
                                    iconTint = if (chapter.bookmark) colors.accent else colors.textSecondary,
                                    onClick = onToggleBookmark,
                                    backgroundColor = colors.surface.copy(alpha = 0.24f),
                                    size = 32.dp,
                                    iconSize = 18.dp,
                                )
                                NovelChapterActionButton(
                                    icon = Icons.Outlined.CheckCircle,
                                    iconTint = if (chapter.read) colors.accent else colors.textSecondary,
                                    onClick = onToggleRead,
                                    backgroundColor = colors.surface.copy(alpha = 0.24f),
                                    size = 32.dp,
                                    iconSize = 18.dp,
                                )
                            }
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
}
