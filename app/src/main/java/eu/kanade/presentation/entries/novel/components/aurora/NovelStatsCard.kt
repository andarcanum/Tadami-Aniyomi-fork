package eu.kanade.presentation.entries.novel.components.aurora

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
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun NovelStatsCard(
    novel: Novel,
    rating: Float?,
    chapterCount: Int,
    readChapterCount: Int,
    nextUpdate: Instant?,
    sourceName: String,
    modifier: Modifier = Modifier,
) {
    val nextUpdateDays = rememberNovelNextUpdateDays(nextUpdate)
    val clampedReadCount = readChapterCount.coerceIn(0, chapterCount.coerceAtLeast(0))
    val progressFraction = if (chapterCount > 0) clampedReadCount / chapterCount.toFloat() else null

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
                        value = rating?.let { String.format(Locale.ROOT, "%.1f", it) }
                            ?: stringResource(MR.strings.not_applicable),
                        leadingIcon = Icons.Filled.Star,
                        leadingIconTint = Color(0xFFFACC15),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_progress),
                        value = if (chapterCount > 0) {
                            "$clampedReadCount/$chapterCount"
                        } else {
                            stringResource(MR.strings.not_applicable)
                        },
                        progressFraction = progressFraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuietMetricTile(
                        label = stringResource(MR.strings.chapters),
                        value = chapterCount.toString(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietMetricTile(
                        label = stringResource(AYMR.strings.aurora_update),
                        value = when (nextUpdateDays) {
                            null -> stringResource(MR.strings.not_applicable)
                            0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
                            else -> "${nextUpdateDays}d"
                        },
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
                    value = sourceName,
                    modifier = Modifier.weight(1f),
                )
                QuietMetricTile(
                    label = stringResource(AYMR.strings.aurora_status),
                    value = novelStatusText(novel.status),
                    badge = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

internal fun rememberNovelNextUpdateDays(nextUpdate: Instant?, now: Instant = Instant.now()): Int? {
    return nextUpdate?.let { now.until(it, ChronoUnit.DAYS).toInt().coerceAtLeast(0) }
}

@Composable
private fun novelStatusText(status: Long): String {
    return when (status) {
        SManga.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
        SManga.COMPLETED.toLong() -> stringResource(MR.strings.completed)
        SManga.LICENSED.toLong() -> stringResource(MR.strings.licensed)
        SManga.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
        SManga.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
        SManga.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
        else -> stringResource(MR.strings.unknown)
    }
}
