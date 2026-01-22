package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.items.chapter.model.Chapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact chapter card with 40x40 thumbnail and minimal design.
 */
@Composable
fun MangaChapterCardCompact(
    manga: Manga,
    item: ChapterList.Item,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val chapter = item.chapter

    // Adjust opacity for read chapters
    val contentAlpha = if (chapter.read) 0.6f else 1f

    GlassmorphismCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        verticalPadding = 4.dp,
        innerPadding = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChapterClicked(chapter) },
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
                    model = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
                        ImageRequest.Builder(context)
                            .data(manga.asMangaCover())
                            .placeholderMemoryCacheKey(manga.thumbnailUrl)
                            .crossfade(true)
                            .size(40)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp),
                    alpha = contentAlpha,
                )

                // Dark overlay for read chapters
                if (chapter.read) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    )
                }
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
                    color = colors.textPrimary.copy(alpha = contentAlpha),
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
                        tint = colors.textSecondary.copy(alpha = contentAlpha),
                        modifier = Modifier.size(12.dp),
                    )

                    // Format upload date
                    val uploadDateText = remember(chapter.dateUpload) {
                        if (chapter.dateUpload > 0) {
                            val date = Date(chapter.dateUpload)
                            val now = System.currentTimeMillis()
                            val diff = now - chapter.dateUpload
                            val days = diff / (1000 * 60 * 60 * 24)

                            when {
                                days < 1 -> "Сегодня"
                                days < 2 -> "Вчера"
                                days < 7 -> "$days дней назад"
                                days < 30 -> "${days / 7} недель назад"
                                else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)
                            }
                        } else {
                            "Дата неизвестна"
                        }
                    }

                    Text(
                        text = uploadDateText,
                        fontSize = 12.sp,
                        color = colors.textSecondary.copy(alpha = contentAlpha),
                    )
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
                if (onDownloadChapter != null) {
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
