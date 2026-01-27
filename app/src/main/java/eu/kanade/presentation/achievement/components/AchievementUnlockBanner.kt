package eu.kanade.presentation.achievement.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay
import tachiyomi.domain.achievement.model.Achievement

/**
 * Aurora-themed Achievement Unlock Banner with slide-in animation and electric gradient
 */
@Composable
fun AchievementUnlockBanner(
    modifier: Modifier = Modifier,
) {
    var currentAchievement by remember { mutableStateOf<Achievement?>(null) }
    var isVisible by remember { mutableStateOf(false) }

    // Register callback with manager
    LaunchedEffect(Unit) {
        AchievementBannerManager.setOnShowCallback { achievement ->
            if (!isVisible) {
                currentAchievement = achievement
            }
        }
    }

    // Auto-dismiss after delay
    LaunchedEffect(currentAchievement) {
        if (currentAchievement != null) {
            isVisible = true
            delay(5000) // Show for 5 seconds
            isVisible = false
            delay(300) // Wait for exit animation
            currentAchievement = null
        }
    }

    // Slide-in animation from top
    val slideOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else -100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "slide_offset",
    )

    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "scale",
    )

    AnimatedVisibility(
        visible = currentAchievement != null && isVisible,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + fadeIn(
            animationSpec = tween(300),
        ),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(200),
        ) + fadeOut(
            animationSpec = tween(200),
        ),
        modifier = modifier,
    ) {
        val achievement = currentAchievement
        if (achievement != null) {
            AchievementBannerItem(
                achievement = achievement,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .offset(y = slideOffset.dp)
                    .scale(scale),
            )
        }
    }
}

/**
 * Individual banner item with Aurora styling
 */
@Composable
private fun AchievementBannerItem(
    achievement: Achievement,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = colors.accent.copy(alpha = 0.5f),
                spotColor = colors.progressCyan.copy(alpha = 0.3f),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0095FF), // Electric blue
                        Color(0xFF00E5FF), // Cyan
                        Color(0xFF7C4DFF), // Purple
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp),
            )
            .drawBehind {
                // Particle burst effect suggestion (subtle glow circles)
                val particlePositions = listOf(
                    Offset(size.width * 0.1f, size.height * 0.3f),
                    Offset(size.width * 0.9f, size.height * 0.7f),
                    Offset(size.width * 0.2f, size.height * 0.8f),
                )

                particlePositions.forEach { position ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = 20f,
                        center = position,
                    )
                }
            }
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Achievement Icon with glow
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.Transparent,
                                ),
                            ),
                            radius = size.minDimension * 0.6f,
                        )
                    },
            ) {
                AchievementIcon(
                    achievement = achievement,
                    isUnlocked = true,
                    modifier = Modifier.size(56.dp),
                    size = 56.dp,
                    useHexagonShape = true,
                )
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // "Achievement Unlocked!" label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "ДОСТИЖЕНИЕ РАЗБЛОКИРОВАНО!",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Achievement title with bold typography
                Text(
                    text = achievement.title,
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

                // Description
                achievement.description?.let { description ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                // Points
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${achievement.points} очков",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

/**
 * Global manager for showing achievement unlock banners
 */
object AchievementBannerManager {
    private var onShowCallback: ((Achievement) -> Unit)? = null

    fun setOnShowCallback(callback: (Achievement) -> Unit) {
        onShowCallback = callback
    }

    fun showAchievement(achievement: Achievement) {
        onShowCallback?.invoke(achievement)
    }

    fun clear() {
        onShowCallback = null
    }
}
