package eu.kanade.presentation.achievement.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aurora-themed Achievement Detail Dialog with full-screen modal design
 */
@Composable
fun AchievementDetailDialog(
    achievement: Achievement,
    progress: AchievementProgress?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    unlockableManager: UnlockableManager = Injekt.get(),
) {
    val colors = AuroraTheme.colors
    val isUnlocked = progress?.isUnlocked == true
    val scrollState = rememberScrollState()

    // Animated glow intensity
    val glowIntensity by animateFloatAsState(
        targetValue = if (isUnlocked) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "glow_intensity",
    )

    AdaptiveSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.surface.copy(alpha = 0.98f),
                            colors.background.copy(alpha = 0.95f),
                        ),
                    ),
                )
                .drawBehind {
                    // Ambient glow at top
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.15f * glowIntensity),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2, 0f),
                            radius = size.width * 0.8f,
                        ),
                    )
                }
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary,
                        )
                    }
                }

                // Large Holographic Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Glow effect behind badge
                    if (isUnlocked) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                colors.accent.copy(alpha = 0.4f),
                                                colors.progressCyan.copy(alpha = 0.2f),
                                                Color.Transparent,
                                            ),
                                        ),
                                        radius = size.minDimension / 2,
                                    )
                                },
                        )
                    }

                    // Badge
                    if (achievement.isHidden && !isUnlocked) {
                        HiddenBadgeLarge()
                    } else {
                        AchievementIcon(
                            achievement = achievement,
                            isUnlocked = isUnlocked,
                            modifier = Modifier.size(120.dp),
                            size = 120.dp,
                            useHexagonShape = true,
                        )
                    }
                }

                // Title with neon glow
                Text(
                    text = if (achievement.isHidden && !isUnlocked) {
                        "???"
                    } else {
                        achievement.title
                    },
                    style = MaterialTheme.typography.headlineMedium.merge(
                        TextStyle(
                            shadow = if (isUnlocked) {
                                Shadow(
                                    color = colors.accent.copy(alpha = 0.6f),
                                    blurRadius = 16f,
                                )
                            } else {
                                null
                            },
                        ),
                    ),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = if (isUnlocked) colors.textPrimary else colors.textSecondary,
                )

                // Description
                if (!achievement.isHidden || isUnlocked) {
                    val description = achievement.description
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = colors.textSecondary,
                            lineHeight = 24.sp,
                        )
                    }
                }

                // Progress Section with animated visualization
                if (progress != null && !progress.isUnlocked) {
                    AnimatedProgressSection(progress, achievement.threshold, colors)
                }

                // Divider with gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.accent.copy(alpha = 0.5f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                // Rewards Section
                AuroraRewardSection(achievement, isUnlocked, unlockableManager, colors)

                // Unlock date
                val unlockedAt = progress?.unlockedAt
                if (unlockedAt != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Разблокировано: ${formatDate(unlockedAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary.copy(alpha = 0.8f),
                        )
                    }
                }

                // Glowing Close Button
                AuroraButton(
                    text = "ЗАКРЫТЬ",
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Hidden badge large variant with scanline effect
 */
@Composable
private fun HiddenBadgeLarge(
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = colors.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp),
        )

        // Scanline effect
        Column(
            modifier = Modifier.matchParentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(8) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .alpha(0.3f)
                        .background(
                            if (index % 2 == 0) {
                                Color.White.copy(alpha = 0.05f)
                            } else {
                                Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

/**
 * Animated progress section with circular visualization
 */
@Composable
private fun AnimatedProgressSection(
    progress: AchievementProgress,
    threshold: Int?,
    colors: eu.kanade.presentation.theme.AuroraColors,
) {
    val max = threshold ?: progress.maxProgress
    val progressFraction = (progress.progress.toFloat() / max).coerceIn(0f, 1f)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Circular progress indicator
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Background circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.1f),
                        shape = CircleShape,
                    ),
            )

            // Progress arc (simplified as horizontal bar for now)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    colors.accent,
                                    colors.progressCyan,
                                ),
                            ),
                        ),
                )
            }

            // Percentage text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${(progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                )
                Text(
                    text = "${progress.progress} / $max",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        // Linear progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                ),
        ) {
            // Progress fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                colors.accent,
                                colors.progressCyan,
                                colors.gradientPurple.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            )

            // Shimmer effect
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.4f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}

/**
 * Aurora-styled reward section
 */
@Composable
private fun AuroraRewardSection(
    achievement: Achievement,
    isUnlocked: Boolean,
    unlockableManager: UnlockableManager,
    colors: eu.kanade.presentation.theme.AuroraColors,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "НАГРАДЫ",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textSecondary.copy(alpha = 0.7f),
            letterSpacing = 2.sp,
        )

        // Points reward
        AuroraRewardItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            },
            title = "+${achievement.points} очков",
            subtitle = null,
            isUnlocked = isUnlocked,
        )

        // Unlockable reward
        val unlockableId = achievement.unlockableId
        if (unlockableId != null) {
            val isUnlockableUnlocked = unlockableManager.isUnlockableUnlocked(unlockableId)
            val unlockableName = unlockableManager.getUnlockableName(unlockableId)

            AuroraRewardItem(
                icon = {
                    Icon(
                        imageVector = if (isUnlockableUnlocked) Icons.Default.Check else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isUnlockableUnlocked) Color(0xFF4CAF50) else colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                title = unlockableName,
                subtitle = if (!isUnlockableUnlocked) "Разблокируется при выполнении достижения" else null,
                isUnlocked = isUnlockableUnlocked,
            )
        }
    }
}

/**
 * Individual reward item with Aurora styling
 */
@Composable
private fun AuroraRewardItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isUnlocked) {
                    colors.accent.copy(alpha = 0.1f)
                } else {
                    Color.White.copy(alpha = 0.03f)
                },
            )
            .border(
                width = 1.dp,
                color = if (isUnlocked) colors.accent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isUnlocked) {
                        colors.accent.copy(alpha = 0.2f)
                    } else {
                        Color.White.copy(alpha = 0.05f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isUnlocked) colors.textPrimary else colors.textSecondary,
                fontWeight = if (isUnlocked) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Aurora-styled button with glow effect
 */
@Composable
private fun AuroraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Button(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = colors.accent.copy(alpha = 0.3f),
                spotColor = colors.accent.copy(alpha = 0.2f),
            ),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.8f),
                            colors.progressCyan.copy(alpha = 0.6f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White,
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("ru"))
    return formatter.format(Date(timestamp))
}
