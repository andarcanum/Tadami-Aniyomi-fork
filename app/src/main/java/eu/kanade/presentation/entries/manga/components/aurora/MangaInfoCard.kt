package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
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
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var genresExpanded by rememberSaveable { mutableStateOf(false) }

    val nextUpdateDays = remember(nextUpdate) {
        if (nextUpdate != null) {
            val now = Instant.now()
            now.until(nextUpdate, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
        } else {
            null
        }
    }

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 20.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats grid (2x2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Rating
                StatItem(
                    value = "4.9",
                    label = stringResource(AYMR.strings.aurora_rating),
                    modifier = Modifier.weight(1f)
                )

                // Status
                StatItem(
                    value = manga.status.toString(),
                    label = stringResource(AYMR.strings.aurora_status),
                    modifier = Modifier.weight(1f)
                )

                // Chapters
                StatItem(
                    value = chapterCount.toString(),
                    label = pluralStringResource(MR.plurals.manga_num_chapters, count = chapterCount, chapterCount),
                    modifier = Modifier.weight(1f)
                )

                // Next Update
                StatItem(
                    value = when (nextUpdateDays) {
                        null -> stringResource(MR.strings.not_applicable)
                        0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
                        else -> "$nextUpdateDays"
                    },
                    label = "Next Update",
                    modifier = Modifier.weight(1f)
                )
            }

            // Description
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Text(
                    text = manga.description ?: stringResource(AYMR.strings.aurora_no_description),
                    color = colors.textPrimary.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis
                )

                if ((manga.description?.length ?: 0) > 200) {
                    TextButton(
                        onClick = { descriptionExpanded = !descriptionExpanded }
                    ) {
                        Text(
                            text = if (descriptionExpanded)
                                stringResource(MR.strings.manga_info_collapse)
                                else stringResource(MR.strings.manga_info_expand),
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Genre tags - collapsible
            if (!manga.genre.isNullOrEmpty()) {
                Column(
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val genresToShow = if (genresExpanded) manga.genre!! else manga.genre!!.take(3)
                        genresToShow.forEach { genre ->
                            SuggestionChip(
                                onClick = { onTagSearch(genre) },
                                label = {
                                    Text(
                                        text = genre,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    }

                    if (manga.genre!!.size > 3) {
                        TextButton(
                            onClick = { genresExpanded = !genresExpanded }
                        ) {
                            Text(
                                text = if (genresExpanded) "Show less genres" else "Show all ${manga.genre!!.size} genres",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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
        modifier = modifier
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
