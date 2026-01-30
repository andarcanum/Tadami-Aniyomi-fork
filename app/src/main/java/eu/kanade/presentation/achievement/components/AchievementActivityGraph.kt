package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.achievement.model.MonthStats
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
fun AchievementActivityGraph(
    yearlyStats: List<Pair<YearMonth, MonthStats>>,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    // Calculate max value for scaling
    val maxActivity = remember(yearlyStats) {
        yearlyStats.maxOfOrNull { it.second.totalActivity } ?: 1
    }.coerceAtLeast(1)

    // Animation state using spring for smoothness
    var animationStarted by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "activity_animation",
    )

    LaunchedEffect(yearlyStats) {
        animationStarted = true
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Активность за год",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Bar Chart Row with Canvas for trend line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Trend line layer
                if (yearlyStats.size >= 3) {
                    TrendLine(
                        yearlyStats = yearlyStats,
                        maxActivity = maxActivity,
                        animationProgress = animationProgress,
                    )
                }

                // Bars layer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    yearlyStats.forEachIndexed { index, (month, stats) ->
                        val heightFraction = (stats.totalActivity.toFloat() / maxActivity).coerceIn(0.05f, 1f)

                        ActivityBar(
                            month = month,
                            stats = stats,
                            heightFraction = heightFraction,
                            animationProgress = animationProgress,
                            index = index,
                            totalItems = yearlyStats.size
                        )
                    }
                }
            }
        }
    }
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
) {
    val colors = AuroraTheme.colors

    // Staggered animation delay for each bar
    val staggerDelay = index.toFloat() / totalItems
    val barAnimationProgress = ((animationProgress - staggerDelay) / (1f - staggerDelay)).coerceIn(0f, 1f)
    val animatedHeight = (heightFraction * barAnimationProgress).coerceIn(0.05f, 1f)

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

    // Create tooltip state
    val tooltipState = remember { TooltipState() }

    // Month formatter for short month names in Russian
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM", Locale("ru")) }
    val monthLabel = month.format(monthFormatter).lowercase().take(3)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(24.dp)
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                ActivityTooltip(
                    month = month,
                    stats = stats,
                )
            },
            state = tooltipState,
        ) {
            // The Bar with long-press gesture
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight(animatedHeight)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                barColor,
                                barColor.copy(alpha = 0.3f)
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                isHighlighted = true
                                // Show tooltip on long press
                                kotlinx.coroutines.runBlocking {
                                    tooltipState.show()
                                }
                            },
                            onPress = {
                                // Wait for release
                                tryAwaitRelease()
                                isHighlighted = false
                                // Hide tooltip when released
                                kotlinx.coroutines.runBlocking {
                                    tooltipState.dismiss()
                                }
                            }
                        )
                    },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Month Label
        Text(
            text = monthLabel,
            color = if (isHighlighted) colors.accent else colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityTooltip(
    month: YearMonth,
    stats: MonthStats,
) {
    val colors = AuroraTheme.colors

    // Full month name formatter
    val fullMonthFormatter = remember { DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")) }
    val monthName = month.format(fullMonthFormatter)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }

    PlainTooltip {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(4.dp)
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
                    text = "Всего: ${stats.totalActivity}",
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }

            // Chapters
            if (stats.chaptersRead > 0) {
                Text(
                    text = "Глав: ${stats.chaptersRead}",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )
            }

            // Episodes
            if (stats.episodesWatched > 0) {
                Text(
                    text = "Эпизодов: ${stats.episodesWatched}",
                    color = colors.progressCyan,
                    fontSize = 11.sp,
                )
            }

            // Time in app
            if (stats.timeInAppMinutes > 0) {
                val hours = stats.timeInAppMinutes / 60
                val minutes = stats.timeInAppMinutes % 60
                val timeText = if (hours > 0) {
                    "${hours}ч ${minutes}мин"
                } else {
                    "${minutes}мин"
                }
                Text(
                    text = "Время: $timeText",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )
            }

            // Achievements
            if (stats.achievementsUnlocked > 0) {
                Text(
                    text = "Достижений: ${stats.achievementsUnlocked}",
                    color = colors.accent.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }

            // No activity message
            if (stats.totalActivity == 0) {
                Text(
                    text = "Нет активности",
                    color = colors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Calculates the total activity (chapters + episodes) for a month
 */
private val MonthStats.totalActivity: Int
    get() = chaptersRead + episodesWatched

/**
 * Calculates 3-month moving average trend line data
 */
private fun calculateTrendLine(data: List<Pair<YearMonth, MonthStats>>): List<Float> {
    return data.mapIndexed { index, _ ->
        val window = data.subList(
            maxOf(0, index - 1),
            minOf(data.size, index + 2)
        )
        window.map { it.second.totalActivity.toFloat() }.average().toFloat()
    }
}

@Composable
private fun TrendLine(
    yearlyStats: List<Pair<YearMonth, MonthStats>>,
    maxActivity: Int,
    animationProgress: Float,
) {
    val colors = AuroraTheme.colors
    val trendData = remember(yearlyStats) { calculateTrendLine(yearlyStats) }

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 12.dp)
    ) {
        if (trendData.size < 2) return@Canvas

        val barWidth = size.width / yearlyStats.size
        val centerOffset = barWidth / 2

        // Calculate points for trend line
        val points = trendData.mapIndexed { index, value ->
            val x = index * barWidth + centerOffset
            val heightFraction = (value / maxActivity).coerceIn(0.05f, 1f)
            val y = size.height - (size.height * heightFraction * animationProgress)
            androidx.compose.ui.geometry.Offset(x, y)
        }

        // Draw trend line with animation
        if (points.size >= 2) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(points.first().x, points.first().y)
                
                // Draw smooth curve through points
                for (i in 1 until points.size) {
                    val prevPoint = points[i - 1]
                    val currentPoint = points[i]
                    
                    // Use quadratic bezier for smooth curve
                    val controlX = (prevPoint.x + currentPoint.x) / 2
                    quadraticBezierTo(
                        controlX, prevPoint.y,
                        currentPoint.x, currentPoint.y
                    )
                }
            }

            // Draw the line
            drawPath(
                path = path,
                color = colors.progressCyan.copy(alpha = 0.8f * animationProgress),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // Draw glow effect
            drawPath(
                path = path,
                color = colors.progressCyan.copy(alpha = 0.3f * animationProgress),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 6.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}
