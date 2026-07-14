package eu.kanade.presentation.achievement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.easteregg.aurora.AuroraHeartManager
import eu.kanade.domain.easteregg.aurora.AuroraLocalization
import eu.kanade.presentation.achievement.components.AchievementActivityGraph
import eu.kanade.presentation.achievement.components.AchievementCard
import eu.kanade.presentation.achievement.components.AchievementCategoryTabs
import eu.kanade.presentation.achievement.components.AchievementContent
import eu.kanade.presentation.achievement.components.AchievementStatsComparison
import eu.kanade.presentation.achievement.screenmodel.AchievementScreenState
import eu.kanade.presentation.more.settings.AuroraTopBarLayout
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    onLocaleChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val title = stringResource(AYMR.strings.label_achievements)
    val localeTags = LocalConfiguration.current.locales.toLanguageTags()

    LaunchedEffect(localeTags) {
        onLocaleChanged()
    }

    Scaffold(
        topBar = {
            AuroraTopBarLayout(
                title = title,
                titleContent = null,
                onNavigateUp = onClickBack,
                actions = {},
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
                                AuroraBentoHeader(
                                    levelInfo = state.levelInfo,
                                    totalPoints = state.totalPoints,
                                    unlockedCount = state.unlockedCount,
                                    totalCount = state.totalCount,
                                    currentStreak = state.currentStreak,
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
                    state.selectedAchievement?.let { achievement ->
                        eu.kanade.presentation.achievement.components.AchievementDetailDialog(
                            achievement = achievement,
                            progress = state.progress[achievement.id],
                            onDismiss = onDialogDismiss,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bento Grid Header for Achievements Screen
 */
@Composable
private fun AuroraBentoHeader(
    levelInfo: eu.kanade.presentation.achievement.screenmodel.UserLevelInfo,
    totalPoints: Int,
    unlockedCount: Int,
    totalCount: Int,
    currentStreak: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Left Column: Giant Level Card (weight 1.1f)
        BentoLevelCard(
            levelInfo = levelInfo,
            modifier = Modifier
                .weight(1.1f)
                .height(156.dp),
        )

        // Right Column: Stats & Streak (weight 1f)
        Column(
            modifier = Modifier
                .weight(1f)
                .height(156.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BentoStatsCard(
                totalPoints = totalPoints,
                unlockedCount = unlockedCount,
                totalCount = totalCount,
                modifier = Modifier.weight(1f),
            )

            BentoStreakCard(
                currentStreak = currentStreak,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Bento Level progress card with giant watermark and neon indicators
 */
@Composable
private fun BentoLevelCard(
    levelInfo: eu.kanade.presentation.achievement.screenmodel.UserLevelInfo,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    // Outer Shell
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(4.dp),
    ) {
        // Inner Core
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                .padding(14.dp),
        ) {
            // Giant Monospace Watermark background number
            Text(
                text = String.format("%02d", levelInfo.level),
                fontSize = 90.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = colors.accent.copy(alpha = 0.06f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 20.dp),
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(AYMR.strings.achievement_bento_rank),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary.copy(alpha = 0.4f),
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val rankRes = when (levelInfo.level) {
                        in 1..5 -> AYMR.strings.achievement_rank_novice_reader
                        in 6..10 -> AYMR.strings.achievement_rank_avid_reader
                        in 11..15 -> AYMR.strings.achievement_rank_ranobe_master
                        in 16..20 -> AYMR.strings.achievement_rank_grandmaster
                        else -> AYMR.strings.achievement_rank_legendary_scholar
                    }
                    Text(
                        text = stringResource(rankRes).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.achievement_bento_level, levelInfo.level),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                            val manager =
                                remember { Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>() }
                            val holderTitle = manager.unlockedPayload()?.holderTitle
                            if (holderTitle != null) {
                                Text(
                                    text = "• ${AuroraLocalization.translate(holderTitle)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = "${levelInfo.currentXp}/${levelInfo.requiredXpForNext} XP",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary.copy(alpha = 0.6f),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Ultra thin progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.05f)),
                    ) {
                        val progressFraction = levelInfo.progressFraction.coerceIn(0f, 1f)
                        if (progressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .background(colors.accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bento Stats Card displaying total points and unlocked count
 */
@Composable
private fun BentoStatsCard(
    totalPoints: Int,
    unlockedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    // Outer Shell
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(4.dp),
    ) {
        // Inner Core
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
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
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.achievement_bento_xp_points),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary.copy(alpha = 0.4f),
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = totalPoints.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.achievementGold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.achievement_bento_unlocked),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary.copy(alpha = 0.4f),
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = "$unlockedCount/$totalCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                    )
                }
            }
        }
    }
}

/**
 * Bento Streak Card displaying current streak and horizontal micro timeline
 */
@Composable
private fun BentoStreakCard(
    currentStreak: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    // Outer Shell
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(4.dp),
    ) {
        // Inner Core
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
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
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(AYMR.strings.achievement_bento_streak),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary.copy(alpha = 0.4f),
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(AYMR.strings.achievement_bento_days, currentStreak),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                    )
                }

                // Micro timeline indicator: 5 micro-lines indicating streak status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(5) { index ->
                        val isActive = index < currentStreak.coerceAtMost(5)
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 14.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    if (isActive) colors.accent else Color.White.copy(alpha = 0.05f),
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = if (isActive) {
                                        colors.accent.copy(
                                            alpha = 0.5f,
                                        )
                                    } else {
                                        Color.White.copy(alpha = 0.1f)
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
