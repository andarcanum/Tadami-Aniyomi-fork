package eu.kanade.presentation.achievement.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.achievement.screenmodel.AchievementScreenState
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.components.material.padding

/**
 * Aurora-themed Achievement Content with loading and empty states
 */
@Composable
fun AchievementContent(
    state: AchievementScreenState,
    modifier: Modifier = Modifier,
    onAchievementClick: (achievement: tachiyomi.domain.achievement.model.Achievement) -> Unit = {},
    onDialogDismiss: () -> Unit = {},
) {
    val colors = AuroraTheme.colors

    when (state) {
        is AchievementScreenState.Loading -> {
            AuroraLoadingState(modifier = modifier)
        }
        is AchievementScreenState.Success -> {
            AchievementGrid(
                state = state,
                modifier = modifier,
                onAchievementClick = onAchievementClick,
            )

            // Show detail dialog if achievement is selected
            if (state.selectedAchievement != null) {
                val achievement = state.selectedAchievement!!
                val progress = state.progress[achievement.id]
                AchievementDetailDialog(
                    achievement = achievement,
                    progress = progress,
                    onDismiss = onDialogDismiss,
                )
            }
        }
    }
}

/**
 * Aurora-themed loading state with pulsing indicator
 */
@Composable
private fun AuroraLoadingState(
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Pulsing glow behind indicator
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.accent.copy(alpha = 0.2f),
                                    Color.Transparent,
                                ),
                            ),
                            radius = size.minDimension / 2,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp),
                )
            }

            Text(
                text = "Загрузка достижений...",
                color = colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/**
 * Achievement grid with Aurora styling
 */
@Composable
private fun AchievementGrid(
    state: AchievementScreenState.Success,
    modifier: Modifier = Modifier,
    onAchievementClick: (achievement: tachiyomi.domain.achievement.model.Achievement) -> Unit,
) {
    val colors = AuroraTheme.colors
    val filteredAchievements = state.filteredAchievements

    if (filteredAchievements.isEmpty()) {
        AuroraEmptyState(
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.medium,
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        items(
            items = filteredAchievements,
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
}

/**
 * Aurora-themed empty state with icon and message
 */
@Composable
private fun AuroraEmptyState(
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon with glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.accent.copy(alpha = 0.15f),
                                    Color.Transparent,
                                ),
                            ),
                            radius = size.minDimension / 2,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Нет достижений",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = "В этой категории пока нет достижений",
                    color = colors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}
