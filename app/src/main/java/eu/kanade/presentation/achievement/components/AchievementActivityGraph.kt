package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.LocalAuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.ActivityType

/**
 * GitHub-style activity contribution graph for achievements
 *
 * @param activityData List of daily activity data
 * @param modifier Modifier for the component
 */
@Composable
fun AchievementActivityGraph(
    activityData: List<DayActivity>,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val scrollState = rememberScrollState()

    // Calculate weeks for display
    val weeks = remember(activityData) { organizeIntoWeeks(activityData) }
    val monthLabels = remember(weeks) { calculateMonthLabels(weeks) }

    // Animation state
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing,
            ),
        )
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
            // Title
            Text(
                text = "Активность за год",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Month labels row
            MonthLabelsRow(
                monthLabels = monthLabels,
                colors = colors,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Activity grid with horizontal scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    weeks.forEachIndexed { weekIndex, weekDays ->
                        WeekColumn(
                            weekDays = weekDays,
                            weekIndex = weekIndex,
                            colors = colors,
                            animationProgress = animationProgress.value,
                            totalWeeks = weeks.size,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            ActivityLegend(colors = colors)
        }
    }
}

/**
 * Month labels displayed above the activity grid
 */
@Composable
private fun MonthLabelsRow(
    monthLabels: List<Pair<Int, String>>,
    colors: AuroraColors,
) {
    val density = LocalDensity.current
    val cellSize = 12.dp
    val cellSpacing = 2.dp
    val columnSpacing = 2.dp

    val weekColumnWidth = with(density) {
        (cellSize.toPx() * 7) + (cellSpacing.toPx() * 6)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        monthLabels.forEach { (weekIndex, monthName) ->
            val position = with(density) {
                (weekIndex * (weekColumnWidth + columnSpacing.toPx())).toDp()
            }

            Text(
                text = monthName,
                color = colors.textSecondary.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = position + 4.dp),
            )
        }
    }
}

/**
 * Single week column containing 7 day cells
 */
@Composable
private fun WeekColumn(
    weekDays: List<DayActivity?>,
    weekIndex: Int,
    colors: AuroraColors,
    animationProgress: Float,
    totalWeeks: Int,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.drawBehind {
            if (weekIndex % 4 == 0 && weekIndex > 0) {
                drawLine(
                    color = colors.divider.copy(alpha = 0.2f),
                    start = Offset(-1.dp.toPx(), 0f),
                    end = Offset(-1.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    ) {
        weekDays.forEach { dayActivity ->
            if (dayActivity != null) {
                DayCell(
                    dayActivity = dayActivity,
                    colors = colors,
                    animationProgress = animationProgress,
                    weekIndex = weekIndex,
                    totalWeeks = totalWeeks,
                )
            } else {
                // Empty cell for padding
                Spacer(modifier = Modifier.size(12.dp))
            }
        }
    }
}

/**
 * Individual day cell with tooltip on long press
 */
@Composable
private fun DayCell(
    dayActivity: DayActivity,
    colors: AuroraColors,
    animationProgress: Float,
    weekIndex: Int,
    totalWeeks: Int,
) {
    var showTooltip by remember { mutableStateOf(false) }
    var tooltipOffset by remember { mutableStateOf(Offset.Zero) }

    // Calculate staggered animation delay based on position
    val staggerDelay = (weekIndex * 7 + dayActivity.date.dayOfWeek.value) * 15
    val cellAnimationProgress = ((animationProgress * 1000 - staggerDelay) / 500f)
        .coerceIn(0f, 1f)

    val cellColor = calculateCellColor(dayActivity.level, colors)
    val animatedAlpha = cellColor.alpha * cellAnimationProgress

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(cellColor.copy(alpha = animatedAlpha))
            .border(
                width = 0.5.dp,
                color = when (dayActivity.level) {
                    0 -> colors.divider.copy(alpha = 0.4f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(2.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        tooltipOffset = offset
                        showTooltip = true
                    },
                    onPress = {
                        // Hide tooltip on release
                        tryAwaitRelease()
                        showTooltip = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Tooltip
        if (showTooltip) {
            DayTooltip(
                dayActivity = dayActivity,
                colors = colors,
            )
        }
    }
}

/**
 * Tooltip showing date and activity details
 */
@Composable
private fun DayTooltip(
    dayActivity: DayActivity,
    colors: AuroraColors,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")) }
    val activityText = when (dayActivity.type) {
        ActivityType.READING -> "${dayActivity.level * 5}+ глав прочитано"
        ActivityType.WATCHING -> "${dayActivity.level * 3}+ эпизодов просмотрено"
        ActivityType.APP_OPEN -> "${dayActivity.level * 2}+ открытий приложения"
    }

    Box(
        modifier = Modifier
            .offset(y = (-40).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background.copy(alpha = 0.95f))
            .border(
                width = 1.dp,
                color = colors.accent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .drawBehind {
                // Glow effect
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.1f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width * 0.8f,
                    ),
                )
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = dayActivity.date.format(dateFormatter),
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = activityText,
                color = colors.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Legend explaining activity levels
 */
@Composable
private fun ActivityLegend(
    colors: AuroraColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Меньше",
            color = colors.textSecondary.copy(alpha = 0.6f),
            fontSize = 10.sp,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Level indicators
        (0..4).forEach { level ->
            val cellColor = calculateCellColor(level, colors)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cellColor)
                    .border(
                        width = 0.5.dp,
                        color = if (level == 0) colors.divider.copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Больше",
            color = colors.textSecondary.copy(alpha = 0.6f),
            fontSize = 10.sp,
        )
    }
}

/**
 * Calculate color based on activity level
 */
private fun calculateCellColor(level: Int, colors: AuroraColors): Color {
    return when (level.coerceIn(0, 4)) {
        0 -> Color.Transparent
        1 -> colors.accent.copy(alpha = 0.25f)
        2 -> colors.accent.copy(alpha = 0.45f)
        3 -> colors.accent.copy(alpha = 0.7f)
        4 -> colors.accent
        else -> Color.Transparent
    }
}

/**
 * Organize activity data into weeks for grid display
 */
private fun organizeIntoWeeks(activityData: List<DayActivity>): List<List<DayActivity?>> {
    if (activityData.isEmpty()) return emptyList()

    val sortedData = activityData.sortedBy { it.date }
    val startDate = sortedData.first().date
    val endDate = sortedData.last().date

    val weekFields = WeekFields.of(Locale.getDefault())
    val dataByDate = sortedData.associateBy { it.date }

    // Calculate the start of the first week (Monday)
    val firstWeekStart = startDate.with(weekFields.dayOfWeek(), 1L)

    // Generate all weeks
    val weeks = mutableListOf<List<DayActivity?>>()
    var currentWeekStart = firstWeekStart

    while (!currentWeekStart.isAfter(endDate)) {
        val weekDays = (0..6).map { dayOffset ->
            val currentDate = currentWeekStart.plusDays(dayOffset.toLong())
            dataByDate[currentDate]
        }
        weeks.add(weekDays)
        currentWeekStart = currentWeekStart.plusWeeks(1)
    }

    return weeks
}

/**
 * Calculate month labels with their week positions
 */
private fun calculateMonthLabels(weeks: List<List<DayActivity?>>): List<Pair<Int, String>> {
    val labels = mutableListOf<Pair<Int, String>>()
    var lastMonth: java.time.Month? = null

    weeks.forEachIndexed { weekIndex, weekDays ->
        // Find first non-null day in the week
        val firstDay = weekDays.firstOrNull { it != null }
        firstDay?.let { dayActivity ->
            val month = dayActivity.date.month
            if (month != lastMonth) {
                val monthName = month.getDisplayName(TextStyle.SHORT, Locale("ru")).uppercase()
                labels.add(weekIndex to monthName)
                lastMonth = month
            }
        }
    }

    return labels
}

// Preview
@Preview(showBackground = true, backgroundColor = 0xFF0f172a)
@Composable
private fun AchievementActivityGraphPreview() {
    val previewData = generatePreviewData()

    CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AuroraColors.Dark.background)
                .padding(16.dp),
        ) {
            AchievementActivityGraph(
                activityData = previewData,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFf8fafc)
@Composable
private fun AchievementActivityGraphLightPreview() {
    val previewData = generatePreviewData()

    CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Light) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AuroraColors.Light.background)
                .padding(16.dp),
        ) {
            AchievementActivityGraph(
                activityData = previewData,
            )
        }
    }
}

/**
 * Generate sample data for preview
 */
private fun generatePreviewData(): List<DayActivity> {
    val data = mutableListOf<DayActivity>()
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(365)

    var currentDate = startDate
    while (!currentDate.isAfter(endDate)) {
        // Random activity level with some patterns
        val level = when {
            // Weekend bias - more activity on weekends
            currentDate.dayOfWeek.value >= 6 -> (0..4).random()
            // Recent bias - more activity in recent months
            currentDate.isAfter(endDate.minusMonths(3)) -> (0..4).random()
            else -> (0..3).random()
        }

        if (level > 0) {
            val type = ActivityType.entries.toTypedArray().random()
            data.add(DayActivity(date = currentDate, level = level, type = type))
        }

        currentDate = currentDate.plusDays(1)
    }

    return data
}
