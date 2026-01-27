package eu.kanade.presentation.achievement.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress

/**
 * Aurora-themed Achievement Card with glassmorphism and neon effects
 */
@Composable
fun AchievementCard(
    achievement: Achievement,
    progress: AchievementProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isUnlocked = progress?.isUnlocked == true

    // Animated glow alpha for unlocked state
    val glowAlpha by animateFloatAsState(
        targetValue = if (isUnlocked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "glow_alpha",
    )

    // Animated border color
    val borderColor by animateColorAsState(
        targetValue = if (isUnlocked) {
            colors.accent.copy(alpha = 0.6f)
        } else {
            Color.White.copy(alpha = 0.08f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "border_color",
    )

    // Card background with glassmorphism effect
    val cardBackground = if (isUnlocked) {
        Brush.verticalGradient(
            colors = listOf(
                colors.surface.copy(alpha = 0.9f),
                colors.surface.copy(alpha = 0.7f),
                colors.background.copy(alpha = 0.8f),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                colors.surface.copy(alpha = 0.4f),
                colors.background.copy(alpha = 0.3f),
            ),
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isUnlocked) 8.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (isUnlocked) colors.accent.copy(alpha = 0.3f) else Color.Transparent,
                spotColor = if (isUnlocked) colors.accent.copy(alpha = 0.2f) else Color.Transparent,
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Box(
            modifier = Modifier
                .background(cardBackground)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(16.dp),
                )
                .drawBehind {
                    // Neon glow effect for unlocked achievements
                    if (isUnlocked) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.accent.copy(alpha = 0.08f * glowAlpha),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * 0.8f, size.height * 0.2f),
                                radius = size.width * 0.6f,
                            ),
                        )
                    }
                }
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Header row: Icon, Title, Points with checkmark in corner
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Checkmark in top-right corner for unlocked
                if (isUnlocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent.copy(alpha = 0.2f))
                            .border(
                                width = 1.dp,
                                color = colors.accent.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Achievement Icon with hexagonal shape option
                    if (achievement.isHidden && !isUnlocked) {
                        HiddenAchievementIcon(
                            modifier = Modifier.size(52.dp),
                        )
                    } else {
                        AchievementIcon(
                            achievement = achievement,
                            isUnlocked = isUnlocked,
                            modifier = Modifier.size(52.dp),
                            size = 52.dp,
                            useHexagonShape = true,
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Title and Points
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (achievement.isHidden && !isUnlocked) {
                                "???"
                            } else {
                                achievement.title
                            },
                            color = if (isUnlocked) colors.textPrimary else colors.textSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                letterSpacing = 0.3.sp,
                                shadow = if (isUnlocked) {
                                    Shadow(
                                        color = colors.accent.copy(alpha = 0.5f),
                                        blurRadius = 8f,
                                    )
                                } else {
                                    null
                                },
                            ),
                        )

                        if (achievement.points > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (isUnlocked) colors.accent else colors.textSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${achievement.points} очков",
                                    color = if (isUnlocked) colors.accent else colors.textSecondary.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isUnlocked) FontWeight.SemiBold else FontWeight.Normal,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                        }
                    }
                }
            }

                // Description
                if (!achievement.isHidden || isUnlocked) {
                    Spacer(modifier = Modifier.height(10.dp))
                    achievement.description?.let { description ->
                        Text(
                            text = description,
                            color = colors.textSecondary.copy(alpha = if (isUnlocked) 0.9f else 0.6f),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                            letterSpacing = 0.2.sp,
                        )
                    }
                }

                // Holographic Progress Bar (if not unlocked and has threshold)
                if (!isUnlocked && achievement.threshold != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HolographicProgressBar(
                        progress = progress?.progress ?: 0,
                        threshold = achievement.threshold ?: 1,
                    )
                }
            }
        }
    }
}

/**
 * Hidden achievement icon with lock and scanline effect
 */
@Composable
private fun HiddenAchievementIcon(
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = colors.textSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp),
        )

        // Scanline effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.02f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}


/**
 * Holographic progress bar with animated shimmer effect
 */
@Composable
private fun HolographicProgressBar(
    progress: Int,
    threshold: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val progressFraction = (progress.toFloat() / threshold).coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                text = "ПРОГРЕСС",
                color = colors.textSecondary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = "$progress/$threshold",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(3.dp),
                ),
        ) {
            // Progress fill with holographic gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                colors.accent,
                                colors.progressCyan,
                                colors.accent.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            )

            // Shimmer overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.3f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}
