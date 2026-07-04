package eu.kanade.presentation.achievement.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.launch
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchievementActivityGraph(
    yearlyStats: List<Pair<YearMonth, MonthStats>>,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()

    // Группировка месяцев по полугодиям с сортировкой
    val firstHalf = yearlyStats
        .filter { it.first.monthValue in 1..6 }
        .sortedBy { it.first.monthValue }
    val secondHalf = yearlyStats
        .filter { it.first.monthValue in 7..12 }
        .sortedBy { it.first.monthValue }

    // Рассчитываем maxActivity для всех 12 месяцев (для единой шкалы)
    val maxActivity = remember(yearlyStats) {
        yearlyStats.maxOfOrNull { it.second.totalActivity } ?: 1
    }.coerceAtLeast(1)

    // Pager для переключения между полугодиями
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Заголовок с индикатором периода
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.achievement_year_activity_title).uppercase(),
                color = colors.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )

            // Индикатор текущего периода
            Text(
                text = when (pagerState.currentPage) {
                    0 -> stringResource(MR.strings.achievement_period_jan_jun).uppercase()
                    1 -> stringResource(MR.strings.achievement_period_jul_dec).uppercase()
                    else -> ""
                },
                color = colors.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
        }

        // Bento Shell Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.surface.copy(alpha = 0.5f),
                                colors.surface.copy(alpha = 0.3f),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Горизонтальный Pager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        val monthsToShow = when (page) {
                            0 -> firstHalf
                            1 -> secondHalf
                            else -> emptyList()
                        }

                        MonthBarChart(
                            months = monthsToShow,
                            maxActivity = maxActivity,
                            locale = locale,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Индикаторы страниц (горизонтальные линии)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(2) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(width = 12.dp, height = 2.dp)
                                    .background(
                                        color = if (pagerState.currentPage == index) {
                                            colors.accent
                                        } else {
                                            colors.textSecondary.copy(alpha = 0.2f)
                                        },
                                        shape = RoundedCornerShape(1.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bar chart для отображения месяцев (6 штук на странице)
 */
@Composable
private fun MonthBarChart(
    months: List<Pair<YearMonth, MonthStats>>,
    maxActivity: Int,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    // Animation state
    var animationStarted by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "activity_animation",
    )

    LaunchedEffect(months) {
        animationStarted = true
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .drawBehind {
                // Draw thin tech grid lines behind the bars
                val strokeWidth = 1.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.03f)
                drawLine(
                    gridColor,
                    Offset(0f, size.height * 0.25f),
                    Offset(size.width, size.height * 0.25f),
                    strokeWidth,
                )
                drawLine(gridColor, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), strokeWidth)
                drawLine(
                    gridColor,
                    Offset(0f, size.height * 0.75f),
                    Offset(size.width, size.height * 0.75f),
                    strokeWidth,
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEachIndexed { index, (month, stats) ->
            val heightFraction = calculateHeightFraction(
                activity = stats.totalActivity,
                maxActivity = maxActivity,
            )

            ActivityBar(
                month = month,
                stats = stats,
                heightFraction = heightFraction,
                animationProgress = animationProgress,
                index = index,
                totalItems = months.size,
                locale = locale,
            )
        }
    }
}

/**
 * Калькулятор высоты графика с ограничением максимума
 *
 * @param activity Активность месяца (chapters + episodes)
 * @param maxActivity Максимальная активность среди всех месяцев
 * @return heightFraction в диапазоне [0.05f, 0.75f]
 */
private fun calculateHeightFraction(
    activity: Int,
    maxActivity: Int,
): Float {
    if (maxActivity == 0) return 0.05f

    // Базовая нормализация
    val normalized = activity.toFloat() / maxActivity

    // Ограничиваем максимум до 75% (чтобы графики не были слишком высокими)
    return normalized.coerceIn(0.05f, 0.75f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityBar(
    month: YearMonth,
    stats: MonthStats,
    heightFraction: Float,
    animationProgress: Float,
    index: Int,
    totalItems: Int,
    locale: Locale,
) {
    val colors = AuroraTheme.colors
    val coroutineScope = rememberCoroutineScope()

    // Staggered animation delay for each bar
    val staggerDelay = index.toFloat() / totalItems
    val barAnimationProgress = ((animationProgress - staggerDelay) / (1f - staggerDelay)).coerceIn(0f, 1f)
    val animatedHeight = (heightFraction * barAnimationProgress).coerceIn(0.05f, 1f)

    // Tooltip state
    val tooltipState = rememberTooltipState()

    // Highlight state for long-press
    var isHighlighted by remember { mutableStateOf(false) }
    val barColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            colors.accent.copy(alpha = 1f)
        } else {
            colors.accent.copy(alpha = 0.7f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bar_color",
    )

    val monthLabel = remember(month, locale) {
        formatMonthShortLabel(month, locale)
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = colors.divider,
                    shape = RoundedCornerShape(12.dp),
                ),
            ) {
                ActivityTooltipContent(month = month, stats = stats, locale = locale)
            }
        },
        state = tooltipState,
        modifier = Modifier.width(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics {
                contentDescription = "Activity bar for ${month.month.name}"
            },
        ) {
            // The Bar with long-press gesture (Sleek 8.dp narrow neon pillar)
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight(animatedHeight)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                barColor,
                                barColor.copy(alpha = 0.3f),
                            ),
                        ),
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                isHighlighted = true
                                coroutineScope.launch {
                                    tooltipState.show()
                                }
                            },
                            onPress = {
                                // Wait for release
                                tryAwaitRelease()
                                isHighlighted = false
                                coroutineScope.launch {
                                    tooltipState.dismiss()
                                }
                            },
                        )
                    },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Month Label (Uppercase Soft Sans-serif)
            Text(
                text = monthLabel.uppercase(),
                color = if (isHighlighted) colors.accent else colors.textSecondary.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActivityTooltipContent(
    month: YearMonth,
    stats: MonthStats,
    locale: Locale,
) {
    val colors = AuroraTheme.colors

    val monthName = remember(month, locale) {
        formatMonthYearLabel(month, locale)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp),
    ) {
        // Month name
        Text(
            text = monthName,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Total activity
        if (stats.totalActivity > 0) {
            Text(
                text = stringResource(MR.strings.achievement_stat_total, stats.totalActivity),
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }

        // Chapters
        if (stats.chaptersRead > 0) {
            Text(
                text = stringResource(MR.strings.achievement_stat_chapters, stats.chaptersRead),
                color = colors.textSecondary,
                fontSize = 11.sp,
            )
        }

        // Episodes
        if (stats.episodesWatched > 0) {
            Text(
                text = stringResource(MR.strings.achievement_stat_episodes, stats.episodesWatched),
                color = colors.progressCyan,
                fontSize = 11.sp,
            )
        }

        // Time in app
        if (stats.timeInAppMinutes > 0) {
            val hours = stats.timeInAppMinutes / 60
            val minutes = stats.timeInAppMinutes % 60
            val timeText = if (hours > 0) {
                stringResource(MR.strings.achievement_hours_minutes_alt, hours, minutes)
            } else {
                stringResource(MR.strings.achievement_minutes_alt, minutes)
            }
            Text(
                text = stringResource(MR.strings.achievement_stat_time, timeText),
                color = colors.textSecondary,
                fontSize = 11.sp,
            )
        }

        // Achievements
        if (stats.achievementsUnlocked > 0) {
            Text(
                text = stringResource(MR.strings.achievement_stat_achievements, stats.achievementsUnlocked),
                color = colors.accent.copy(alpha = 0.8f),
                fontSize = 11.sp,
            )
        }

        // No activity message
        if (stats.totalActivity == 0) {
            Text(
                text = stringResource(MR.strings.achievement_no_activity),
                color = colors.textSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Calculates the total activity (chapters + episodes) for a month
 */
private val MonthStats.totalActivity: Int
    get() = chaptersRead + episodesWatched

internal fun formatMonthShortLabel(month: YearMonth, locale: Locale): String {
    return month.month.getDisplayName(TextStyle.SHORT, locale)
        .replace(".", "")
        .lowercase(locale)
        .take(3)
}

internal fun formatMonthYearLabel(month: YearMonth, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    return month.format(formatter)
        .replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(locale)
            } else {
                char.toString()
            }
        }
}
