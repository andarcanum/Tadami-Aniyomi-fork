package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.R
import tachiyomi.domain.achievement.model.Achievement
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aurora-themed Achievement Icon with hexagonal shape, pulsing glow, and scanline effects
 *
 * @param icon The icon name from achievement.badge_icon
 * @param isUnlocked Whether the achievement is unlocked
 * @param modifier The modifier to be applied to the icon
 * @param size The size of the icon
 * @param useHexagonShape Whether to use hexagon shape (true) or rounded rectangle (false)
 */
@Composable
fun AchievementIcon(
    icon: String?,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    useHexagonShape: Boolean = true,
) {
    val context = LocalContext.current
    val iconResId = getIconResourceId(icon, context.packageName)
    val colors = AuroraTheme.colors

    // Note: Pulsing animation removed for compatibility - can be added back with proper infinite transition setup
    val pulseScale = 1f

    // Glow alpha animation
    val glowAlpha by animateFloatAsState(
        targetValue = if (isUnlocked) 0.6f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "glow_alpha",
    )

    // Scale animation on unlock
    val unlockScale by animateFloatAsState(
        targetValue = if (isUnlocked) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "unlock_scale",
    )

    val shape = if (useHexagonShape) {
        HexagonShape
    } else {
        RoundedCornerShape(12.dp)
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(unlockScale * pulseScale),
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow effect for unlocked - centered behind the hexagon
        if (isUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Use hexagon's actual proportions for proper centering
                        val drawSize = this.size
                        val hexHeight = drawSize.width * 0.866f
                        val verticalOffset = (drawSize.height - hexHeight) / 2
                        drawPath(
                            path = createHexagonPath(drawSize.width, verticalOffset),
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.accent.copy(alpha = glowAlpha),
                                    colors.progressCyan.copy(alpha = glowAlpha * 0.5f),
                                    Color.Transparent,
                                ),
                                center = Offset(drawSize.width / 2, drawSize.height / 2),
                                radius = drawSize.width * 0.55f,
                            ),
                        )
                    },
            )
        }

        // Main icon container - centered without extra padding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    if (isUnlocked) {
                        Brush.radialGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.3f),
                                colors.surface.copy(alpha = 0.8f),
                            ),
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.White.copy(alpha = 0.02f),
                            ),
                        )
                    },
                )
                .border(
                    width = if (isUnlocked) 2.dp else 1.dp,
                    brush = if (isUnlocked) {
                        Brush.linearGradient(
                            colors = listOf(
                                colors.accent,
                                colors.progressCyan,
                            ),
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f),
                            ),
                        )
                    },
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Use Star icon centered in the hexagon
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier
                    .size(size * 0.5f)
                    .alpha(if (isUnlocked) 1f else 0.5f),
                tint = if (isUnlocked) colors.accent else colors.textSecondary.copy(alpha = 0.5f),
            )
        }

        // Scanline effect for locked achievements
        if (!isUnlocked) {
            ScanlineOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )
        }
    }
}

/**
 * Scanline overlay effect for locked achievements
 */
@Composable
private fun ScanlineOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .drawBehind {
                val drawSize = this.size
                val lineCount = 6
                val lineHeight = drawSize.height / (lineCount * 2)

                for (i in 0 until lineCount) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.2f),
                        topLeft = Offset(0f, i * 2 * lineHeight),
                        size = androidx.compose.ui.geometry.Size(drawSize.width, lineHeight),
                    )
                }

                // Diagonal scan line
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(0f, drawSize.height),
                    end = Offset(drawSize.width, 0f),
                    strokeWidth = 1f,
                )
            },
    )
}

/**
 * Hexagon shape for achievement icons - vertically centered in the bounds
 */
private val HexagonShape = GenericShape { drawSize, _ ->
    val hexHeight = drawSize.width * 0.866f
    val verticalOffset = (drawSize.height - hexHeight) / 2
    val path = createHexagonPath(drawSize.width, verticalOffset)
    addPath(path)
}

/**
 * Creates a hexagon path with optional vertical offset for centering
 */
private fun createHexagonPath(width: Float, verticalOffset: Float = 0f): Path {
    val height = width * 0.866f // sqrt(3)/2
    val radius = width / 2
    val centerX = width / 2
    val centerY = height / 2 + verticalOffset

    return Path().apply {
        for (i in 0 until 6) {
            val angle = Math.PI / 3 * i - Math.PI / 2 // Start from top
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()

            if (i == 0) {
                moveTo(x, y)
            } else {
                lineTo(x, y)
            }
        }
        close()
    }
}

/**
 * Resolves the icon resource ID from the icon name.
 *
 * @param iconName The name of the icon resource (e.g. "ic_badge_first_chapter")
 * @param packageName The package name of the app
 * @return The resource ID, defaults to ic_badge_default if not found
 */
private fun getIconResourceId(
    iconName: String?,
    packageName: String,
): Int {
    if (iconName.isNullOrEmpty()) {
        return R.drawable.ic_badge_default
    }

    return try {
        val resourceId = android.content.res.Resources.getSystem().getIdentifier(
            iconName,
            "drawable",
            packageName,
        )
        if (resourceId != 0) {
            resourceId
        } else {
            R.drawable.ic_badge_default
        }
    } catch (e: Exception) {
        R.drawable.ic_badge_default
    }
}

/**
 * Simplified version that takes an Achievement directly.
 */
@Composable
fun AchievementIcon(
    achievement: Achievement,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    useHexagonShape: Boolean = true,
) {
    AchievementIcon(
        icon = achievement.badgeIcon,
        isUnlocked = isUnlocked,
        modifier = modifier,
        size = size,
        useHexagonShape = useHexagonShape,
    )
}
