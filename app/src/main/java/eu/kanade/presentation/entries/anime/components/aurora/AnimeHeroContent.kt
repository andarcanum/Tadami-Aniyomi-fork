package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Hero content displayed at the bottom of the first screen.
 * Shows anime title, basic info, Continue/Start button, and Dubbing selector.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeHeroContent(
    anime: Anime,
    episodeCount: Int,
    hasUnseenEpisodes: Boolean,
    onContinueWatching: () -> Unit,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f),
                    ),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Anime Title
        Text(
            text = anime.title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Genres preview (first 3)
        if (!anime.genre.isNullOrEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                anime.genre!!.take(3).forEach { genre ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = genre,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Status and episode count
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = AnimeStatusFormatter.formatStatus(anime.status),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "•",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = "$episodeCount эп.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Continue/Start button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent)
                    .clickable { onContinueWatching() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.textOnAccent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (hasUnseenEpisodes) {
                            stringResource(MR.strings.action_resume)
                        } else {
                            stringResource(MR.strings.action_start)
                        },
                        color = colors.textOnAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Dubbing selector button (if available)
            if (onDubbingClicked != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selectedDubbing?.isNotBlank() == true) {
                                colors.accent.copy(alpha = 0.3f)
                            } else {
                                Color.White.copy(alpha = 0.15f)
                            },
                        )
                        .clickable { onDubbingClicked() }
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (selectedDubbing?.isNotBlank() == true) {
                                colors.accent
                            } else {
                                Color.White.copy(alpha = 0.8f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = selectedDubbing?.takeIf { it.isNotBlank() } ?: stringResource(MR.strings.label_dubbing),
                            color = if (selectedDubbing?.isNotBlank() == true) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.8f)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
