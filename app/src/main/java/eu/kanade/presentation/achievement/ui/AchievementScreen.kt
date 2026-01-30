package eu.kanade.presentation.achievement.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.achievement.components.AchievementActivityGraph
import eu.kanade.presentation.achievement.components.AchievementCard
import eu.kanade.presentation.achievement.components.AchievementCategoryTabs
import eu.kanade.presentation.achievement.components.AchievementContent
import eu.kanade.presentation.achievement.components.AchievementStatsComparison
import eu.kanade.presentation.achievement.components.ActivityStreakIndicator
import eu.kanade.presentation.achievement.screenmodel.AchievementScreenState
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * Aurora-themed Achievement Screen with custom top bar and floating stats
 */
@Composable
fun AchievementScreen(
    state: AchievementScreenState,
    onClickBack: () -> Unit,
    onCategoryChanged: (AchievementCategory) -> Unit = {},
    onAchievementClick: (achievement: Achievement) -> Unit = {},
    onDialogDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val title = stringResource(AYMR.strings.label_achievements)

    Scaffold(
        topBar = { scrollBehavior ->
            AuroraAchievementTopBar(
                title = title,
                onNavigateBack = onClickBack,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = colors.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(colors.background)
                .drawBehind {
                    // Subtle ambient gradient
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.03f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.3f),
                            radius = size.width * 0.8f,
                        ),
                    )
                },
        ) {
            when (state) {
                is AchievementScreenState.Loading -> {
                    AchievementContent(
                        state = state,
                        modifier = modifier.fillMaxSize(),
                        onAchievementClick = onAchievementClick,
                        onDialogDismiss = onDialogDismiss,
                    )
                }
                is AchievementScreenState.Success -> {
                    val totalPossiblePoints = state.achievements.sumOf { it.points }
                    val pointsFraction = if (totalPossiblePoints > 0) {
                        state.totalPoints.toFloat() / totalPossiblePoints
                    } else {
                        0f
                    }
                    val unlockedFraction = if (state.totalCount > 0) {
                        state.unlockedCount.toFloat() / state.totalCount
                    } else {
                        0f
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Статистика (объединить в один item для оптимизации)
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AuroraStatsHeader(
                                    totalPoints = state.totalPoints,
                                    totalPossiblePoints = totalPossiblePoints,
                                    pointsFraction = pointsFraction,
                                    unlockedCount = state.unlockedCount,
                                    totalCount = state.totalCount,
                                    unlockedFraction = unlockedFraction,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                AchievementStatsComparison(
                                    currentMonth = state.currentMonthStats,
                                    previousMonth = state.previousMonthStats,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // График активности
                        item {
                            AchievementActivityGraph(
                                yearlyStats = state.yearlyStats,
                            )
                        }

                        // Индикатор серии
                        item {
                            Spacer(modifier = Modifier.height(12.dp))

                            ActivityStreakIndicator(
                                currentStreak = state.currentStreak,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Табы категорий
                        item {
                            AchievementCategoryTabs(
                                selectedCategory = state.selectedCategory,
                                onCategoryChanged = onCategoryChanged,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Сетка достижений
                        items(
                            items = state.filteredAchievements,
                            key = { it.id },
                        ) { achievement ->
                            val progress = state.progress[achievement.id]
                            AchievementCard(
                                achievement = achievement,
                                progress = progress,
                                onClick = { onAchievementClick(achievement) },
                            )
                        }
                    }

                    // Show detail dialog if achievement is selected
                    if (state.selectedAchievement != null) {
                        eu.kanade.presentation.achievement.components.AchievementDetailDialog(
                            achievement = state.selectedAchievement!!,
                            progress = state.progress[state.selectedAchievement!!.id],
                            onDismiss = onDialogDismiss,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Aurora-styled top bar - unified color, no borders, clean design
 */
@Composable
private fun AuroraAchievementTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val backDescription = stringResource(AYMR.strings.achievement_back)

    // Animate alpha based on scroll
    val scrollProgress = scrollBehavior?.state?.collapsedFraction ?: 0f
    val backgroundAlpha by animateFloatAsState(
        targetValue = 0.95f + (scrollProgress * 0.05f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "background_alpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .background(colors.background.copy(alpha = backgroundAlpha))
            .drawBehind {
                // Subtle bottom divider
                drawLine(
                    color = Color.White.copy(alpha = 0.05f * scrollProgress),
                    start = Offset(0f, size.height - 1),
                    end = Offset(size.width, size.height - 1),
                    strokeWidth = 1f,
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button - no border, simple and clean
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backDescription,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Title - clean and neutral
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                letterSpacing = 0.5.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Spacer to balance the back button
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

/**
 * Floating stats header with glassmorphism cards
 */
@Composable
private fun AuroraStatsHeader(
    totalPoints: Int,
    totalPossiblePoints: Int,
    pointsFraction: Float,
    unlockedCount: Int,
    totalCount: Int,
    unlockedFraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val pointsPercent = (pointsFraction.coerceIn(0f, 1f) * 100).roundToInt()
    val unlockedPercent = (unlockedFraction.coerceIn(0f, 1f) * 100).roundToInt()

    val pointsLabel = stringResource(AYMR.strings.achievement_stats_points, pointsPercent)
    val unlockedLabel = stringResource(AYMR.strings.achievement_stats_unlocked, unlockedPercent)

    Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        // Points stat card
        AuroraStatCard(
            icon = Icons.Default.Star,
            value = totalPoints.toString(),
            label = pointsLabel,
            modifier = Modifier.weight(1f),
            iconTint = colors.accent,
            progressFraction = pointsFraction,
        )

        // Unlocked count stat card
        AuroraStatCard(
            icon = Icons.Default.EmojiEvents,
            value = "$unlockedCount/$totalCount",
            label = unlockedLabel,
            modifier = Modifier.weight(1f),
            iconTint = colors.progressCyan,
            progressFraction = unlockedFraction,
        )
    }
}

/**
 * Individual stat card with glassmorphism
 */
@Composable
private fun AuroraStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    iconTint: Color,
    progressFraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val clampedProgress = progressFraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.surface.copy(alpha = 0.6f),
                        colors.surface.copy(alpha = 0.3f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
            )
            .drawBehind {
                if (clampedProgress > 0f) {
                    drawRect(
                        color = iconTint.copy(alpha = 0.18f),
                        size = androidx.compose.ui.geometry.Size(size.width * clampedProgress, size.height),
                    )
                }
            }
            .padding(top = 12.dp, bottom = 4.dp, start = 16.dp, end = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Value and label
            Column {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                )
            }
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
