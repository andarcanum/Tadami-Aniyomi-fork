package eu.kanade.presentation.achievement.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.presentation.core.components.material.padding

@Composable
fun AchievementTabsAndGrid(
    state: AchievementScreenState.Success,
    onCategoryChanged: (AchievementCategory) -> Unit,
    onAchievementClick: (achievement: Achievement) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        AchievementCategoryTabs(
            selectedCategory = state.selectedCategory,
            onCategoryChanged = onCategoryChanged,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        AchievementGrid(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onAchievementClick = onAchievementClick,
        )
    }
}

@Composable
private fun AchievementCategoryTabs(
    selectedCategory: AchievementCategory,
    onCategoryChanged: (AchievementCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val tabs = listOf(
        AchievementCategory.BOTH to "ВСЕ",
        AchievementCategory.ANIME to "АНИМЕ",
        AchievementCategory.MANGA to "МАНГА",
        AchievementCategory.SECRET to "СКРЫТЫЕ",
    )

    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(40.dp),
    ) {
        val segmentWidth = maxWidth / tabs.size
        val selectedIndex = tabs.indexOfFirst { it.first == selectedCategory }.coerceAtLeast(0)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "tab_indicator_offset",
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .fillMaxHeight()
                    .width(segmentWidth)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.9f),
                                colors.progressCyan.copy(alpha = 0.9f),
                            ),
                        ),
                    )
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.15f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                        )
                    },
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { (category, label) ->
                    val isSelected = category == selectedCategory
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) colors.textPrimary else colors.textSecondary,
                        label = "tab_text_color",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onCategoryChanged(category) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementGrid(
    state: AchievementScreenState.Success,
    modifier: Modifier = Modifier,
    onAchievementClick: (achievement: Achievement) -> Unit,
) {
    val filteredAchievements = state.filteredAchievements

    if (filteredAchievements.isEmpty()) {
        AuroraEmptyState(modifier = modifier.fillMaxSize())
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
