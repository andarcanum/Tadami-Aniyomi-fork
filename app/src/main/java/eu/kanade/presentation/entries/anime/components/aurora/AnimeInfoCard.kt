package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.description.DescriptionEngine
import eu.kanade.presentation.entries.components.ExpandableDescriptionBlocks
import eu.kanade.presentation.entries.components.aurora.GlassmorphismCard
import eu.kanade.presentation.entries.components.auroraDescriptionBlockStyle
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

internal fun filterAnimeDescription(description: String?): String? {
    if (description.isNullOrBlank()) return null

    val patternsToFilter = listOf(
        "Original:", "Оригинал:",
        "Original Title:", "Оригинальное название:",
        "Rating:", "Рейтинг:",
        "Shikimori", "Сикимори",
        "Anilist", "Анилист",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeInfoCard(
    anime: Anime,
    translation: AuroraEntryTranslationState? = null,
    onTagSearch: (String) -> Unit,
    descriptionExpanded: Boolean,
    genresExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onToggleGenres: () -> Unit,
    selectedGenres: Set<String> = emptySet(),
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onSearchSelected: (() -> Unit)? = null,
    onClearSelected: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 20.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!anime.displayGenre.isNullOrEmpty()) {
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
                        val genresToShow = if (genresExpanded) anime.displayGenre!! else anime.displayGenre!!.take(8)
                        genresToShow.forEach { genre ->
                            val isSelected = genre in selectedGenres
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) {
                                            colors.accent.copy(
                                                alpha = 0.3f,
                                            )
                                        } else {
                                            colors.accent.copy(alpha = 0.15f)
                                        },
                                    )
                                    .pointerInput(genre, selectedGenres) {
                                        detectTapGestures(
                                            onTap = {
                                                if (selectedGenres.isNotEmpty()) {
                                                    onGenreLongClick?.invoke(genre)
                                                } else {
                                                    onTagSearch(genre)
                                                }
                                            },
                                            onLongPress = {
                                                onGenreLongClick?.invoke(genre)
                                            },
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = genre,
                                    fontSize = 11.sp,
                                    color = if (isSelected) colors.accent else colors.accent,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        if (selectedGenres.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent.copy(alpha = 0.8f))
                                    .clickable { onSearchSelected?.invoke() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "🔎 Search (${selectedGenres.size})",
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent.copy(alpha = 0.15f))
                                    .clickable { onClearSelected?.invoke() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✕", color = colors.accent, fontSize = 11.sp)
                            }
                        }
                    }

                    if (anime.displayGenre!!.size > 8) {
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

            val filteredDescription = remember(anime.displayDescription, translation?.description) {
                translation?.description?.takeUnless { it.isBlank() }
                    ?: filterAnimeDescription(anime.displayDescription)
            }

            Text(
                text = stringResource(AYMR.strings.aurora_description_header),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary.copy(alpha = 0.6f),
                letterSpacing = 0.8.sp,
            )

            var hasDescriptionOverflow by remember { mutableStateOf(false) }
            val descriptionBlocks = remember(filteredDescription) {
                filteredDescription?.let { DescriptionEngine.beautify(it) } ?: emptyList()
            }
            if (descriptionBlocks.isEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.aurora_no_description),
                    color = colors.textPrimary.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ExpandableDescriptionBlocks(
                    blocks = descriptionBlocks,
                    expanded = descriptionExpanded,
                    onToggle = { onToggleDescription() },
                    style = auroraDescriptionBlockStyle(colors),
                    collapsedLines = 5,
                    onOverflowChanged = { hasDescriptionOverflow = it },
                    modifier = Modifier.then(
                        if (hasDescriptionOverflow) {
                            Modifier.clickable { onToggleDescription() }
                        } else {
                            Modifier
                        },
                    ),
                )
            }
        }
    }
}
