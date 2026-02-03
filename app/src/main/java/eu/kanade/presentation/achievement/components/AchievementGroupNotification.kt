package eu.kanade.presentation.achievement.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay
import tachiyomi.domain.achievement.model.Achievement

/**
 * Group notification for multiple achievements shown when user exits reader/player
 */
@Composable
fun AchievementGroupNotification(
    modifier: Modifier = Modifier,
    onViewAll: (List<Achievement>) -> Unit,
) {
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var isVisible by remember { mutableStateOf(false) }
    var clicked by remember { mutableStateOf(false) }

    // Register callback with manager
    LaunchedEffect(Unit) {
        AchievementBannerManager.setOnShowGroupCallback { list ->
            achievements = list
            clicked = false
        }
    }

    // Auto-dismiss after delay (only if not clicked)
    LaunchedEffect(achievements, clicked) {
        if (achievements.isNotEmpty() && !clicked) {
            isVisible = true
            delay(8000) // Show for 8 seconds (longer for group)
            if (!clicked) {
                isVisible = false
                delay(300)
                achievements = emptyList()
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scale",
    )

    val slideOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else -200f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "slide_offset",
    )

    AnimatedVisibility(
        visible = achievements.isNotEmpty() && isVisible,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = androidx.compose.animation.core.tween(200),
        ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
        modifier = modifier,
    ) {
        val count = achievements.size
        val totalPoints = achievements.sumOf { it.points }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AchievementGroupBannerItem(
                achievements = achievements,
                count = count,
                totalPoints = totalPoints,
                onViewAll = onViewAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .offset(y = slideOffset.dp)
                    .scale(scale),
            )
        }
    }
}

@Composable
private fun AchievementGroupBannerItem(
    achievements: List<Achievement>,
    count: Int,
    totalPoints: Int,
    onViewAll: (List<Achievement>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = modifier
            .graphicsLayer {
                shadowElevation = 20f
                spotShadowColor = colors.accent.copy(alpha = 0.6f)
                ambientShadowColor = colors.progressCyan.copy(alpha = 0.4f)
            }
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = colors.accent.copy(alpha = 0.6f),
                spotColor = colors.progressCyan.copy(alpha = 0.5f),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = 0.9f),
                        colors.accent,
                        colors.progressCyan.copy(alpha = 0.8f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable { onViewAll(achievements) }
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon with stacked badges effect
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Background circles for stacked effect
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = (-4).dp, y = 4.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.3f),
                            shape = CircleShape,
                        ),
                )
                // Main icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.25f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Count badge
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .background(
                            color = Color(0xFFFFD700),
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color.White,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = count.toString(),
                        color = Color(0xFFFF6B00),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "НОВЫЕ ДОСТИЖЕНИЯ!",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            blurRadius = 4f,
                        ),
                    ),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Получено $count достижений",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.3f),
                            blurRadius = 8f,
                        ),
                    ),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "+$totalPoints очков",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color(0xFFFF6B00).copy(alpha = 0.5f),
                                blurRadius = 8f,
                            ),
                        ),
                    )
                }
            }

            // Arrow indicator
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Смотреть",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
