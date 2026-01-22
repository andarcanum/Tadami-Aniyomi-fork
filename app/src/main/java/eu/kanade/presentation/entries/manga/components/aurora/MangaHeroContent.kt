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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Hero content displayed at bottom of first screen over poster gradient.
 *
 * Contains: genre chips, manga title, compact stats, and Continue Reading button.
 */
@Composable
fun MangaHeroContent(
    manga: Manga,
    chapterCount: Int,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Genre chips (top 3)
        if (!manga.genre.isNullOrEmpty() && manga.genre!!.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                manga.genre!!.take(3).forEach { genre ->
                    if (genre.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accent.copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = genre,
                                color = colors.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // Manga title
        Text(
            text = manga.title,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            lineHeight = 40.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        // Compact statistics row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Parse rating from description
            val parsedRating = remember(manga.description) {
                RatingParser.parseRating(manga.description)
            }

            // Show rating if available
            if (parsedRating != null) {
                // Star icon
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFACC15),
                    modifier = Modifier.size(14.dp),
                )
                // Rating value
                Text(
                    text = RatingParser.formatRating(parsedRating.rating),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
            }

            // Status
            Text(
                text = MangaStatusFormatter.formatStatus(manga.status),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = "•",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )

            // Chapter count
            Text(
                text = pluralStringResource(
                    MR.plurals.manga_num_chapters,
                    count = chapterCount,
                    chapterCount,
                ),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Continue Reading button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.accent)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onContinueReading()
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = colors.textOnAccent,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(MR.strings.action_resume),
                    color = colors.textOnAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
