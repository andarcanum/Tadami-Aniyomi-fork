package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Info card containing description, stats, and genre tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaInfoCard(
    manga: Manga,
    chapterCount: Int,
    nextUpdate: Instant?,
    onTagSearch: (String) -> Unit,
    descriptionExpanded: Boolean,
    genresExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onToggleGenres: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    val nextUpdateDays = remember(nextUpdate) {
        if (nextUpdate != null) {
            val now = Instant.now()
            now.until(nextUpdate, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
        } else {
            null
        }
    }

    // Parse rating from description
    val parsedRating = remember(manga.description) {
        RatingParser.parseRating(manga.description)
    }

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 20.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Stats grid - Rating and Status, optionally Next Update
            // If manga is completed/finished, show only Rating (left) and Status (right)
            val isCompleted = manga.status.toInt() in listOf(
                SManga.COMPLETED,
                SManga.PUBLISHING_FINISHED,
                SManga.CANCELLED,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isCompleted) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
            ) {
                // Rating (parsed from description or N/A)
                StatItem(
                    value = if (parsedRating != null) {
                        RatingParser.formatRating(parsedRating.rating)
                    } else {
                        stringResource(MR.strings.not_applicable)
                    },
                    label = stringResource(AYMR.strings.aurora_rating),
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                )

                // Status
                StatItem(
                    value = MangaStatusFormatter.formatStatus(manga.status),
                    label = stringResource(AYMR.strings.aurora_status),
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                )

                // Next Update (Обновление) - only show if not completed
                if (!isCompleted) {
                    StatItem(
                        value = when (nextUpdateDays) {
                            null -> stringResource(MR.strings.not_applicable)
                            0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
                            else -> "$nextUpdateDays д"
                        },
                        label = "Обновление",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Description
            Column(
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        alignment = Alignment.TopStart,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = manga.description ?: stringResource(AYMR.strings.aurora_no_description),
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if ((manga.description?.length ?: 0) > 200) {
                        Icon(
                            imageVector = if (descriptionExpanded) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { onToggleDescription() },
                        )
                    }
                }
            }

            // Genre tags - collapsible
            if (!manga.genre.isNullOrEmpty()) {
                Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            alignment = Alignment.TopStart,
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            val genresToShow = if (genresExpanded) manga.genre!! else manga.genre!!.take(3)
                            genresToShow.forEach { genre ->
                                // Compact genre chip
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.accent.copy(alpha = 0.15f))
                                        .clickable { onTagSearch(genre) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 11.sp,
                                        color = colors.accent,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        if (manga.genre!!.size > 3) {
                            Icon(
                                imageVector = if (genresExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable { onToggleGenres() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
