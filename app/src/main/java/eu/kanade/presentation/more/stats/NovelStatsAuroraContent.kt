package eu.kanade.presentation.more.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.more.stats.components.StatsAuroraProgressData
import eu.kanade.presentation.more.stats.components.StatsAuroraStatItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.toDurationString
import eu.kanade.tachiyomi.ui.stats.StatsCalculations
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
fun NovelStatsAuroraContent(
    state: StatsScreenState.SuccessNovel,
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

    val layoutDirection = LocalLayoutDirection.current
    val lazyColumnContentPadding = remember(paddingValues, layoutDirection) {
        PaddingValues(
            start = paddingValues.calculateStartPadding(layoutDirection),
            top = paddingValues.calculateTopPadding() + 16.dp,
            end = paddingValues.calculateEndPadding(layoutDirection),
            bottom = paddingValues.calculateBottomPadding(),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = lazyColumnContentPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item {
                OverviewCardsSection(
                    libraryCount = state.overview.libraryNovelCount,
                    readDuration = readDurationString,
                    completedCount = state.overview.completedNovelCount,
                    readDurationLabel = stringResource(MR.strings.label_read_duration),
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                StatsSectionCard(
                    title = stringResource(AYMR.strings.aurora_titles),
                    cardType = NovelStatsCardType.TITLES,
                    items = listOf(
                        StatsAuroraStatItem(
                            Icons.Outlined.Sync,
                            stringResource(AYMR.strings.aurora_in_global_update),
                            state.titles.globalUpdateItemCount.toString(),
                        ),
                        StatsAuroraStatItem(
                            Icons.Outlined.PlayCircle,
                            stringResource(AYMR.strings.aurora_started),
                            state.titles.startedNovelCount.toString(),
                        ),
                        StatsAuroraStatItem(
                            Icons.Outlined.LocalLibrary,
                            stringResource(AYMR.strings.aurora_local),
                            state.titles.localNovelCount.toString(),
                        ),
                    ),
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val accentColor = colors.accent
                StatsSectionCard(
                    title = stringResource(AYMR.strings.aurora_chapters_header),
                    cardType = NovelStatsCardType.EPISODES,
                    items = listOf(
                        StatsAuroraStatItem(
                            Icons.Outlined.PlayCircle,
                            stringResource(AYMR.strings.aurora_total),
                            state.chapters.totalChapterCount.toString(),
                        ),
                        StatsAuroraStatItem(
                            Icons.Outlined.PlayCircle,
                            stringResource(MR.strings.label_read_chapters),
                            state.chapters.readChapterCount.toString(),
                        ),
                        StatsAuroraStatItem(
                            Icons.Outlined.Download,
                            stringResource(AYMR.strings.aurora_downloaded),
                            state.chapters.downloadCount.toString(),
                        ),
                    ),
                    progressBars = listOf(
                        remember(state.chapters.readChapterCount, state.chapters.totalChapterCount, accentColor) {
                            StatsAuroraProgressData(
                                fraction = StatsCalculations.progressFraction(
                                    done = state.chapters.readChapterCount,
                                    total = state.chapters.totalChapterCount,
                                ),
                                color = accentColor,
                            )
                        },
                        remember(state.chapters.downloadCount, state.chapters.totalChapterCount) {
                            StatsAuroraProgressData(
                                fraction = StatsCalculations.progressFraction(
                                    done = state.chapters.downloadCount,
                                    total = state.chapters.totalChapterCount,
                                ),
                                color = colors.textSecondary,
                            )
                        },
                    ),
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val meanScoreStr = remember(state.trackers.trackedTitleCount, state.trackers.meanScore) {
                    if (state.trackers.trackedTitleCount > 0 && !state.trackers.meanScore.isNaN()) {
                        "%.2f \u2605".format(Locale.ENGLISH, state.trackers.meanScore)
                    } else {
                        none
                    }
                }
                StatsSectionCard(
                    title = stringResource(AYMR.strings.aurora_trackers),
                    cardType = NovelStatsCardType.TRACKERS,
                    items = listOf(
                        StatsAuroraStatItem(
                            Icons.Outlined.CollectionsBookmark,
                            stringResource(AYMR.strings.aurora_tracked_titles),
                            state.trackers.trackedTitleCount.toString(),
                        ),
                        StatsAuroraStatItem(
                            Icons.Outlined.Star,
                            stringResource(AYMR.strings.aurora_mean_score),
                            meanScoreStr,
                        ),
                        StatsAuroraStatItem(
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

private enum class NovelStatsCardType {
    TITLES,
    EPISODES,
    TRACKERS,
}

@Composable
private fun OverviewCardsSection(
    libraryCount: Int,
    readDuration: String,
    completedCount: Int,
    readDurationLabel: String,
) {
    val colors = AuroraTheme.colors
    val dividerColor = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    DoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(MR.strings.label_overview_section),
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = libraryCount.toString(),
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(AYMR.strings.aurora_in_library),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }

            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.6f)
                    .background(dividerColor),
            )

            val isNoData = readDuration == "N/A" || readDuration.isBlank()
            val readTimeText = if (isNoData) "0h" else readDuration
            val readTimeColor = if (isNoData) colors.textSecondary else colors.textPrimary

            Column(
                modifier = Modifier.weight(1.2f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = readTimeText,
                    color = readTimeColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = readDurationLabel,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }

            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.6f)
                    .background(dividerColor),
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = completedCount.toString(),
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(AYMR.strings.aurora_completed),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DoubleBezelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(24.dp)

    val modifierWithStyle = if (!colors.isDark && !colors.isEInk) {
        modifier
            .drawBehind {
                val radius = 24.dp.toPx()
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)

                val neutralOffsetY = 3.dp.toPx()
                val warmOffsetY = 5.dp.toPx()

                val neutralInset = 1.dp.toPx()
                val warmInset = 3.dp.toPx()

                // 1. Neutral shadow
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.035f),
                    topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width - neutralInset * 2,
                        height = size.height,
                    ),
                    cornerRadius = cornerRadius,
                )

                // 2. Accent glow (colors.accent)
                drawRoundRect(
                    color = colors.accent.copy(alpha = 0.025f),
                    topLeft = Offset(x = warmInset, y = warmOffsetY),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width - warmInset * 2,
                        height = size.height,
                    ),
                    cornerRadius = cornerRadius,
                )
            }
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.78f),
                        Color.White.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.60f),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.12f),
                    ),
                ),
                shape = shape,
            )
    } else if (colors.isDark && !colors.isEInk) {
        modifier
            .background(
                color = resolveAuroraMoreCardContainerColor(colors),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = auroraMenuRimLightBrush(colors),
                shape = shape,
            )
    } else {
        modifier
            .background(
                color = resolveAuroraMoreCardContainerColor(colors),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = resolveAuroraMoreCardBorderColor(colors),
                shape = shape,
            )
    }

    Column(
        modifier = modifierWithStyle.padding(18.dp),
    ) {
        content()
    }
}

@Composable
private fun ConcentricProgressRings(
    watchedFraction: Float,
    downloadedFraction: Float,
    watchedColor: Color,
    downloadedColor: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    var animationTriggered by remember { mutableStateOf(false) }

    val animatedWatched by animateFloatAsState(
        targetValue = if (animationTriggered) watchedFraction else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 100f,
        ),
        label = "watched_progress",
    )

    val animatedDownloaded by animateFloatAsState(
        targetValue = if (animationTriggered) downloadedFraction else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 100f,
        ),
        label = "download_progress",
    )

    LaunchedEffect(watchedFraction, downloadedFraction) {
        animationTriggered = true
    }

    Box(
        modifier = modifier.size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()

            drawCircle(
                color = watchedColor.copy(alpha = 0.12f),
                radius = size.minDimension / 2 - strokeWidth / 2,
                style = Stroke(width = strokeWidth),
            )
            drawArc(
                color = watchedColor,
                startAngle = -90f,
                sweepAngle = animatedWatched * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            val innerRingRadius = size.minDimension / 2 - strokeWidth * 2.2f
            drawCircle(
                color = downloadedColor.copy(alpha = 0.12f),
                radius = innerRingRadius,
                style = Stroke(width = strokeWidth),
            )
            drawArc(
                color = downloadedColor,
                startAngle = -90f,
                sweepAngle = animatedDownloaded * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${(animatedWatched * 100).toInt()}%",
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label.lowercase(),
                color = colors.textSecondary,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatsSectionCard(
    title: String,
    cardType: NovelStatsCardType,
    items: List<StatsAuroraStatItem>,
    progressBars: List<StatsAuroraProgressData> = emptyList(),
) {
    val colors = AuroraTheme.colors
    DoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
        )

        Spacer(modifier = Modifier.height(14.dp))

        when (cardType) {
            NovelStatsCardType.TITLES -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    items.forEachIndexed { index, item ->
                        val dotColor = when (index) {
                            0 -> colors.accent.copy(alpha = 0.4f)
                            1 -> colors.accent
                            else -> colors.textSecondary
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = item.value,
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(dotColor, CircleShape),
                                )
                                Text(
                                    text = item.label,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            NovelStatsCardType.EPISODES -> {
                val total = items.getOrNull(0)?.value?.toIntOrNull() ?: 0
                val watched = items.getOrNull(1)?.value?.toIntOrNull() ?: 0
                val downloaded = items.getOrNull(2)?.value?.toIntOrNull() ?: 0

                val watchedFraction = progressBars.getOrNull(0)?.fraction ?: 0f
                val downloadedFraction = progressBars.getOrNull(1)?.fraction ?: 0f
                val progressColor = progressBars.getOrNull(0)?.color ?: colors.accent
                val downloadColor = progressBars.getOrNull(1)?.color ?: colors.textSecondary

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "$watched",
                                    color = colors.textPrimary,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "/ $total",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                            Text(
                                text = items.getOrNull(1)?.label ?: "Read",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    tint = downloadColor,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "$downloaded",
                                    color = colors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = items.getOrNull(2)?.label ?: "Downloaded",
                                color = colors.textSecondary,
                                fontSize = 10.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    ConcentricProgressRings(
                        watchedFraction = watchedFraction,
                        downloadedFraction = downloadedFraction,
                        watchedColor = progressColor,
                        downloadedColor = downloadColor,
                        label = items.getOrNull(1)?.label ?: "Read",
                    )
                }
            }

            NovelStatsCardType.TRACKERS -> {
                val trackedTitles = items.getOrNull(0)?.value ?: "0"
                val meanScore = items.getOrNull(1)?.value ?: "N/A"
                val trackersUsed = items.getOrNull(2)?.value ?: "0"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column {
                            Text(
                                text = trackedTitles,
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = items.getOrNull(0)?.label ?: "Tracked Titles",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Column {
                            Text(
                                text = trackersUsed,
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = items.getOrNull(2)?.label ?: "Trackers Used",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    val isNoScore = meanScore == "N/A" || meanScore.isBlank()
                    val locale = Locale.getDefault().language
                    val scoreText = if (isNoScore) {
                        if (locale == "ru") "Без оценки" else "No score"
                    } else {
                        meanScore
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isNoScore) Color.Transparent else colors.accent.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = if (isNoScore) {
                                    colors.textSecondary.copy(
                                        alpha = 0.20f,
                                    )
                                } else {
                                    colors.accent.copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (!isNoScore) {
                                Icon(
                                    imageVector = Icons.Outlined.Star,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Text(
                                text = scoreText,
                                color = if (isNoScore) colors.textSecondary else colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isNoScore) FontWeight.Normal else FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
