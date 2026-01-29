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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
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
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import tachiyomi.domain.anilist.model.AnilistMetadata
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.shikimori.model.ShikimoriMetadata
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Filter out Shikimori metadata from description (Original, Rating, etc.).
 * Plugins like AnimeGO add this info, but we display it separately in UI.
 */
private fun filterDescription(description: String?): String? {
    if (description.isNullOrBlank()) return null

    val patternsToFilter = listOf(
        "Original:", "Оригинал:",
        "Original Title:", "Оригинальное название:",
        "Rating:", "Рейтинг:",
        "Shikimori", "сикимори",
        "Anilist", "анилист",
    )

    return description.lines()
        .filterNot { line ->
            patternsToFilter.any { pattern ->
                line.contains(pattern, ignoreCase = true)
            }
        }
        .joinToString("\n")
        .trim()
        .takeIf { it.isNotEmpty() }
}

/**
 * Info card containing description, stats, and genre tags for anime.
 * Displays: Rating (from Anilist/Shikimori), Type, Status, Next Update
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
    statsRequester: BringIntoViewRequester? = null,
    // Metadata integration (supports both Anilist and Shikimori)
    animeMetadata: AnimeScreenModel.AnimeMetadataData? = null,
    isMetadataLoading: Boolean = false,
    metadataError: AnimeScreenModel.MetadataError? = null,
    onRetryMetadata: () -> Unit = {},
    onLoginClick: () -> Unit = {},
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

    // Determine if anime is completed (prefer metadata status)
    val isCompleted = animeMetadata?.isCompleted() ?: when (anime.status.toInt()) {
        SAnime.COMPLETED, SAnime.PUBLISHING_FINISHED, SAnime.CANCELLED -> true
        else -> false
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
            // Stats grid - Rating, Type, Status, optionally Next Update
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (statsRequester != null) {
                            base.bringIntoViewRequester(statsRequester)
                        } else {
                            base
                        }
                    },
                horizontalArrangement = if (isCompleted) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
            ) {
                // Rating (from metadata source)
                StatItem(
                    value = when {
                        isMetadataLoading -> "..."
                        metadataError == AnimeScreenModel.MetadataError.NotAuthenticated -> stringResource(MR.strings.not_applicable)
                        else -> animeMetadata?.score?.let { String.format("%.1f", it) } ?: stringResource(MR.strings.not_applicable)
                    },
                    label = "РЕЙТИНГ",
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                    isLoading = isMetadataLoading,
                    error = metadataError,
                    onRetry = onRetryMetadata,
                    onLoginClick = onLoginClick,
                )

                // Type (from metadata source)
                StatItem(
                    value = when {
                        isMetadataLoading -> "..."
                        metadataError == AnimeScreenModel.MetadataError.NotAuthenticated -> stringResource(MR.strings.not_applicable)
                        else -> animeMetadata?.format?.uppercase() ?: stringResource(MR.strings.not_applicable)
                    },
                    label = "ТИП",
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                    isLoading = isMetadataLoading,
                    error = metadataError,
                    onRetry = onRetryMetadata,
                    onLoginClick = onLoginClick,
                )

                // Status (from metadata source if available, otherwise from source)
                StatItem(
                    value = when {
                        isMetadataLoading -> "..."
                        metadataError == AnimeScreenModel.MetadataError.NotAuthenticated -> AnimeStatusFormatter.formatStatus(anime.status)
                        else -> animeMetadata?.formattedStatus ?: AnimeStatusFormatter.formatStatus(anime.status)
                    },
                    label = "СТАТУС",
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                    isLoading = isMetadataLoading,
                    error = metadataError,
                    onRetry = onRetryMetadata,
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                val filteredDescription = remember(anime.description) {
                    filterDescription(anime.description)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = filteredDescription ?: "Описание отсутствует",
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if ((filteredDescription?.length ?: 0) > 200) {
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
                    modifier = Modifier.fillMaxWidth(),
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
    isLoading: Boolean = false,
    error: AnimeScreenModel.MetadataError? = null,
    onRetry: () -> Unit = {},
    onLoginClick: () -> Unit = {},
) {
    val colors = AuroraTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        // Value with optional icon
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )

            // Show retry icon for NetworkError
            if (error == AnimeScreenModel.MetadataError.NetworkError) {
                Spacer(modifier = Modifier.padding(start = 4.dp))
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    tint = colors.accent.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onRetry() },
                )
            }

            // Show info icon for Disabled (user not logged in) - clickable
            if (error == AnimeScreenModel.MetadataError.NotAuthenticated && !isLoading) {
                Spacer(modifier = Modifier.padding(start = 4.dp))
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Авторизуйтесь в сервисе",
                    tint = colors.textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onLoginClick() },
                )
            }
        }

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
