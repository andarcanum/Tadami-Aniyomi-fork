package eu.kanade.presentation.entries.novel.components.aurora

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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.entries.novel.model.normalizeNovelDescription
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelInfoCard(
    novel: Novel,
    translation: AuroraEntryTranslationState? = null,
    onTagSearch: (String) -> Unit,
    descriptionExpanded: Boolean,
    genresExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onToggleGenres: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val normalizedDescription = remember(novel.description, translation?.description) {
        translation?.description ?: normalizeNovelDescription(novel.description)
    }
    var hasDescriptionOverflow by remember(normalizedDescription) { mutableStateOf(false) }
    val normalizedGenres = remember(novel.genre) {
        val seen = LinkedHashSet<String>()
        novel.genre.orEmpty()
            .flatMap { value ->
                value
                    .split(Regex("[,;/|\\n\\r\\t•·]+"))
                    .map {
                        it.trim()
                            .trim('-', '–', '—', ',', ';', '/', '|', '•', '·')
                    }
            }
            .filter { it.isNotBlank() }
            .filter {
                seen.add(it.lowercase())
            }
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
            Column(
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(AYMR.strings.aurora_description_header),
                    color = colors.textSecondary.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    SelectionContainer(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = normalizedDescription ?: stringResource(AYMR.strings.aurora_no_description),
                            color = colors.textPrimary.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { textLayoutResult ->
                                if (textLayoutResult.hasVisualOverflow) {
                                    hasDescriptionOverflow = true
                                }
                            },
                        )
                    }

                    if (shouldShowNovelDescriptionToggle(hasDescriptionOverflow, descriptionExpanded)) {
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
                                .clickable(onClick = onToggleDescription),
                        )
                    }
                }
            }

            if (normalizedGenres.isNotEmpty()) {
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
                            val genresToShow = if (genresExpanded) normalizedGenres else normalizedGenres.take(3)
                            genresToShow.forEach { genre ->
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

                        if (normalizedGenres.size > 3) {
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
                                    .clickable(onClick = onToggleGenres),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldShowNovelDescriptionToggle(
    hasDescriptionOverflow: Boolean,
    descriptionExpanded: Boolean,
): Boolean {
    return hasDescriptionOverflow || descriptionExpanded
}
