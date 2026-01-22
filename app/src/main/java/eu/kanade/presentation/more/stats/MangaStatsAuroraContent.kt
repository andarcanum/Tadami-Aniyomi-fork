package eu.kanade.presentation.more.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.toDurationString
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
fun MangaStatsAuroraContent(
    state: StatsScreenState.SuccessManga,
    paddingValues: PaddingValues,
) {
    val colors = AuroraTheme.colors

    val context = LocalContext.current
    val none = "N/A"
    val readDurationString = remember(state.overview.totalReadDuration) {
        state.overview.totalReadDuration
            .toDuration(DurationUnit.MILLISECONDS)
            .toDurationString(context, fallback = none)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(AYMR.strings.aurora_statistics),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }

            item {
                OverviewCardsSection(
                    libraryCount = state.overview.libraryMangaCount,
                    readDuration = readDurationString,
                    completedCount = state.overview.completedMangaCount,
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                StatsSectionCard(
                    title = stringResource(AYMR.strings.aurora_titles),
                    items = listOf(
                        MangaStatItem(
                            Icons.Outlined.Sync,
                            stringResource(AYMR.strings.aurora_in_global_update),
                            state.titles.globalUpdateItemCount.toString(),
                        ),
                        MangaStatItem(
                            Icons.Outlined.PlayCircle,
                            stringResource(AYMR.strings.aurora_started),
                            state.titles.startedMangaCount.toString(),
                        ),
                        MangaStatItem(
                            Icons.Outlined.Tv,
                            stringResource(AYMR.strings.aurora_local),
                            state.titles.localMangaCount.toString(),
                        ),
                    ),
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                StatsSectionCard(
                    title = stringResource(AYMR.strings.aurora_chapters_header),
                    items = listOf(
                        MangaStatItem(
                            Icons.Outlined.PlayCircle,
                            stringResource(AYMR.strings.aurora_total),
                            state.chapters.totalChapterCount.toString(),
                        ),
                        MangaStatItem(
                            Icons.Outlined.Schedule,
                            stringResource(MR.strings.label_read_chapters),
                            state.chapters.readChapterCount.toString(),
                        ),
                        MangaStatItem(
                            Icons.Outlined.Download,
                            stringResource(AYMR.strings.aurora_downloaded),
                            state.chapters.downloadCount.toString(),
                        ),
                    ),
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val meanScoreStr = remember(state.trackers.trackedTitleCount, state.trackers.meanScore) {
                    if (state.trackers.trackedTitleCount > 0 && !state.trackers.meanScore.isNaN()) {
                        "%.2f ★".format(Locale.ENGLISH, state.trackers.meanScore)
                    } else {
                        none
                    }
                }
                StatsSectionCard(
                    title = stringResource(AYMR.strings.aurora_trackers),
                    items = listOf(
                        MangaStatItem(
                            Icons.Outlined.CollectionsBookmark,
                            stringResource(AYMR.strings.aurora_tracked_titles),
                            state.trackers.trackedTitleCount.toString(),
                        ),
                        MangaStatItem(
                            Icons.Outlined.Star,
                            stringResource(AYMR.strings.aurora_mean_score),
                            meanScoreStr,
                        ),
                        MangaStatItem(
                            Icons.Outlined.Sync,
                            stringResource(AYMR.strings.aurora_trackers_used),
                            state.trackers.trackerCount.toString(),
                        ),
                    ),
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun OverviewCardsSection(
    libraryCount: Int,
    readDuration: String,
    completedCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OverviewCard(
            icon = Icons.Outlined.CollectionsBookmark,
            value = libraryCount.toString(),
            label = stringResource(AYMR.strings.aurora_in_library),
            modifier = Modifier.weight(1f),
        )
        OverviewCard(
            icon = Icons.Outlined.Schedule,
            value = readDuration,
            label = stringResource(MR.strings.label_read_duration),
            modifier = Modifier.weight(1f),
        )
        OverviewCard(
            icon = Icons.Outlined.LocalLibrary,
            value = completedCount.toString(),
            label = stringResource(AYMR.strings.aurora_completed),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OverviewCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.accent.copy(alpha = 0.15f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colors.accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class MangaStatItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

@Composable
private fun StatsSectionCard(
    title: String,
    items: List<MangaStatItem>,
) {
    val colors = AuroraTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.glass,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .background(colors.accent, RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                items.forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = colors.accent.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.value,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.label,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
