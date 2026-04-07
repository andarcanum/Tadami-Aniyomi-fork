package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.entries.manga.components.aurora.QuietMetadataRow
import eu.kanade.presentation.entries.manga.components.aurora.QuietMetricTile
import eu.kanade.presentation.entries.manga.components.aurora.QuietSectionDivider
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AnimeStatsCard(
    snapshot: AnimeDetailsSnapshot,
    modifier: Modifier = Modifier,
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
                        value = snapshot.ratingText,
                        leadingIcon = Icons.Filled.Star,
                        leadingIconTint = Color(0xFFFACC15),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_progress),
                        value = snapshot.progress?.progressText ?: stringResource(MR.strings.not_applicable),
                        progressFraction = snapshot.progress?.percent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_episodes),
                        value = snapshot.episodesText,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_update),
                        value = snapshot.updateText,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                    value = snapshot.sourceText,
                    modifier = Modifier.weight(1f),
                )
                QuietMetricTile(
                    label = stringResource(AYMR.strings.aurora_status),
                    value = snapshot.statusBadgeText,
                    badge = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

