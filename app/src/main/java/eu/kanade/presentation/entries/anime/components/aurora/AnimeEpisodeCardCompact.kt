package eu.kanade.presentation.entries.anime.components.aurora

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
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraCoverModel
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadIndicator
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.items.episode.model.Episode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact episode card with 40x40 thumbnail and minimal design.
 */
@Composable
fun AnimeEpisodeCardCompact(
    anime: Anime,
    item: EpisodeList.Item,
    onEpisodeClicked: (Episode) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val episode = item.episode
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()

    // Adjust opacity for seen episodes
    val contentAlpha = if (episode.seen) 0.6f else 1f

    GlassmorphismCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        verticalPadding = 4.dp,
        innerPadding = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEpisodeClicked(episode) },
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
                    alpha = contentAlpha,
                )

                // Dark overlay for seen episodes
                if (episode.seen) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    )
                }
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
                    val uploadDateText = remember(episode.dateUpload) {
                        if (episode.dateUpload > 0) {
                            val date = Date(episode.dateUpload)
                            val now = System.currentTimeMillis()
                            val diff = now - episode.dateUpload
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
                if (onDownloadEpisode != null) {
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
