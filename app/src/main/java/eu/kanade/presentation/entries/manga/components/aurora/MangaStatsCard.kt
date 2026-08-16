package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.aurora.GlassmorphismCard
import eu.kanade.presentation.entries.components.aurora.QuietMetadataRow
import eu.kanade.presentation.entries.components.aurora.QuietMetricTile
import eu.kanade.presentation.entries.components.aurora.QuietSectionDivider
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaStatsCard(
    manga: Manga,
    detailsSnapshot: MangaDetailsSnapshot,
    modifier: Modifier = Modifier,
    showAuthor: Boolean = true,
) {
    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_rating),
                        value = detailsSnapshot.ratingText ?: stringResource(MR.strings.not_applicable),
                        leadingIcon = Icons.Filled.Star,
                        leadingIconTint = Color(0xFFFACC15),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_progress),
                        value = detailsSnapshot.progress?.progressText ?: stringResource(MR.strings.not_applicable),
                        modifier = Modifier.fillMaxWidth(),
                        progressFraction = detailsSnapshot.progress?.percent,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_chapters),
                        value = detailsSnapshot.progress?.chaptersText ?: stringResource(MR.strings.not_applicable),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_state),
                        value = detailsSnapshot.statusText,
                        badge = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (showAuthor) {
                QuietSectionDivider()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuietMetricTile(
                        label = stringResource(MR.strings.author),
                        value = manga.displayAuthor?.takeIf { it.isNotBlank() }
                            ?: stringResource(MR.strings.unknown_author),
                        leadingIcon = Icons.Filled.PersonOutline,
                        valueMaxLines = Int.MAX_VALUE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val artist = manga.displayArtist?.takeIf { it.isNotBlank() }
                    if (artist != null && artist != manga.displayAuthor) {
                        QuietMetricTile(
                            label = stringResource(MR.strings.artist),
                            value = artist,
                            leadingIcon = Icons.Filled.Brush,
                            valueMaxLines = Int.MAX_VALUE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            QuietSectionDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuietMetadataRow(
                    icon = Icons.Outlined.Public,
                    label = stringResource(AYMR.strings.aurora_source),
                    value = detailsSnapshot.sourceTitle ?: stringResource(MR.strings.not_applicable),
                    modifier = Modifier.weight(1f),
                )
                QuietMetadataRow(
                    icon = Icons.Rounded.Translate,
                    label = stringResource(AYMR.strings.aurora_translation),
                    value = detailsSnapshot.translationText ?: stringResource(MR.strings.not_applicable),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
