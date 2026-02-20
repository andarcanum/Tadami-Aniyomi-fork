package eu.kanade.presentation.achievement.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.achievement.model.MonthStats

/**
 * Aurora-themed statistics comparison card with glassmorphism effect
 * Compares current month stats with previous month
 */
@Composable
fun AchievementStatsComparison(
    currentMonth: MonthStats,
    previousMonth: MonthStats,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.surface.copy(alpha = 0.4f),
                            colors.surface.copy(alpha = 0.2f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = colors.accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                )
                .drawBehind {
                    // Тонкая акцентная линия сверху
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.accent.copy(alpha = 0.3f),
                                Color.Transparent,
                            ),
                        ),
                        size = androidx.compose.ui.geometry.Size(width = size.width, height = 2f),
                    )

                    // Subtle glow effect at top
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, 0f),
                            radius = size.width * 0.8f,
                        ),
                    )
                }
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Header
                Text(
                    text = "Сравнение с прошлым месяцем",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Grid (2x2)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Row 1: Chapters and Episodes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatItem(
                            icon = Icons.Default.Book,
                            iconBackground = colors.accent.copy(alpha = 0.15f),
                            iconTint = colors.accent,
                            label = "Глав прочитано",
                            currentValue = currentMonth.chaptersRead,
                            previousValue = previousMonth.chaptersRead,
                            modifier = Modifier.weight(1f),
                        )
                        StatItem(
                            icon = Icons.Default.Movie,
                            iconBackground = colors.accent.copy(alpha = 0.15f),
                            iconTint = colors.accent,
                            label = "Эпизодов просмотрено",
                            currentValue = currentMonth.episodesWatched,
                            previousValue = previousMonth.episodesWatched,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Row 2: Time and Achievements
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatItem(
                            icon = Icons.Default.AccessTime,
                            iconBackground = colors.accent.copy(alpha = 0.15f),
                            iconTint = colors.accent,
                            label = "Время в приложении",
                            currentValue = currentMonth.timeInAppMinutes,
                            previousValue = previousMonth.timeInAppMinutes,
                            isTimeValue = true,
                            modifier = Modifier.weight(1f),
                        )
                        StatItem(
                            icon = Icons.Default.EmojiEvents,
                            iconBackground = colors.accent.copy(alpha = 0.15f),
                            iconTint = colors.accent,
                            label = "Достижений получено",
                            currentValue = currentMonth.achievementsUnlocked,
                            previousValue = previousMonth.achievementsUnlocked,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual stat item with icon, value and change indicator
 */
@Composable
private fun StatItem(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    label: String,
    currentValue: Int,
    previousValue: Int,
    isTimeValue: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    val percentageChange = if (previousValue > 0) {
        ((currentValue - previousValue).toFloat() / previousValue * 100).toInt()
    } else if (currentValue > 0) {
        100
    } else {
        0
    }

    val isIncrease = currentValue >= previousValue
    val changeColor = if (isIncrease) colors.success else colors.error
    val changeIcon = if (isIncrease) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown

    Column(
        modifier = modifier
            .height(120.dp)
            .background(
                color = colors.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = colors.accent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Value
        Text(
            text = if (isTimeValue) {
                formatTimeMinutes(currentValue)
            } else {
                currentValue.toString()
            },
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Label
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Change indicator
        if (previousValue > 0 || currentValue > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = changeIcon,
                    contentDescription = null,
                    tint = changeColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${kotlin.math.abs(percentageChange)}%",
                    color = changeColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "vs прошлый месяц",
                    color = colors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        } else {
            Text(
                text = "Нет данных",
                color = colors.textSecondary.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Format minutes to hours and minutes string
 */
private fun formatTimeMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}ч ${mins}м"
        hours > 0 -> "${hours}ч"
        else -> "${mins}м"
    }
}

@Preview(showBackground = true)
@Composable
private fun AchievementStatsComparisonPreview() {
    val colors = AuroraColors.Dark
    androidx.compose.runtime.CompositionLocalProvider(
        eu.kanade.presentation.theme.LocalAuroraColors provides colors,
    ) {
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(16.dp),
        ) {
            AchievementStatsComparison(
                currentMonth = MonthStats(
                    chaptersRead = 127,
                    episodesWatched = 45,
                    timeInAppMinutes = 2340,
                    achievementsUnlocked = 8,
                ),
                previousMonth = MonthStats(
                    chaptersRead = 98,
                    episodesWatched = 62,
                    timeInAppMinutes = 1890,
                    achievementsUnlocked = 5,
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AchievementStatsComparisonLightPreview() {
    val colors = AuroraColors.Light
    androidx.compose.runtime.CompositionLocalProvider(
        eu.kanade.presentation.theme.LocalAuroraColors provides colors,
    ) {
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(16.dp),
        ) {
            AchievementStatsComparison(
                currentMonth = MonthStats(
                    chaptersRead = 85,
                    episodesWatched = 32,
                    timeInAppMinutes = 1560,
                    achievementsUnlocked = 12,
                ),
                previousMonth = MonthStats(
                    chaptersRead = 120,
                    episodesWatched = 28,
                    timeInAppMinutes = 1800,
                    achievementsUnlocked = 8,
                ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AchievementStatsComparisonUniformPreview() {
    val colors = AuroraColors.Dark
    androidx.compose.runtime.CompositionLocalProvider(
        eu.kanade.presentation.theme.LocalAuroraColors provides colors,
    ) {
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(16.dp),
        ) {
            AchievementStatsComparison(
                currentMonth = MonthStats(
                    chaptersRead = 9999,
                    episodesWatched = 1,
                    timeInAppMinutes = 9999,
                    achievementsUnlocked = 999,
                ),
                previousMonth = MonthStats(
                    chaptersRead = 5000,
                    episodesWatched = 1,
                    timeInAppMinutes = 5000,
                    achievementsUnlocked = 500,
                ),
            )
        }
    }
}
