package eu.kanade.presentation.entries.anime.components.aurora

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
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.SAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Info card containing description, stats, and genre tags for anime.
 * Displays: Rating (placeholder), Type (placeholder), Status, Next Update
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeInfoCard(
    anime: Anime,
    episodeCount: Int,
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

    // Determine if anime is completed
    val isCompleted = anime.status.toInt() in listOf(
        SAnime.COMPLETED,
        SAnime.PUBLISHING_FINISHED,
        SAnime.CANCELLED,
    )

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 20.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Stats grid - Rating, Type, Status, optionally Next Update
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isCompleted) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
            ) {
                // Rating (placeholder for Shikimori integration)
                StatItem(
                    value = stringResource(MR.strings.not_applicable),
                    label = "РЕЙТИНГ",
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                )

                // Type (placeholder for Shikimori integration)
                StatItem(
                    value = stringResource(MR.strings.not_applicable),
                    label = "ТИП",
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                )

                // Status
                StatItem(
                    value = AnimeStatusFormatter.formatStatus(anime.status),
                    label = "СТАТУС",
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                )

                // Next Update - only show if not completed
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
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = anime.description ?: "Описание отсутствует",
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if ((anime.description?.length ?: 0) > 200) {
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
            if (!anime.genre.isNullOrEmpty()) {
                Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
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
                            val genresToShow = if (genresExpanded) anime.genre!! else anime.genre!!.take(3)
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

                        if (anime.genre!!.size > 3) {
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

/**
 * Formatter for anime status values.
 */
object AnimeStatusFormatter {
    fun formatStatus(status: Long): String {
        return when (status.toInt()) {
            SAnime.ONGOING -> "Онгоинг"
            SAnime.COMPLETED -> "Завершён"
            SAnime.LICENSED -> "Лицензирован"
            SAnime.PUBLISHING_FINISHED -> "Выпуск завершён"
            SAnime.CANCELLED -> "Отменён"
            SAnime.ON_HIATUS -> "На паузе"
            else -> "Неизвестно"
        }
    }
}
