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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.delay
import tachiyomi.domain.achievement.model.Achievement
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Aurora-themed Achievement Unlock Banner with slide-in animation, electric gradient,
 * Lottie fireworks for rare achievements, and particle burst effects
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

    // Enhanced bounce animation with overshoot
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scale",
    )

    // Slide from top with bounce
    val slideOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else -200f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "slide_offset",
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
            val isRare = achievement.points >= 50

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Lottie fireworks for rare achievements
                if (isRare) {
                    FireworksAnimation(
                        modifier = Modifier
                            .size(300.dp)
                            .offset(y = (-20).dp),
                    )
                }

                // Particle burst effect
                ParticleBurstEffect(
                    isActive = isVisible,
                    particleCount = if (isRare) 24 else 12,
                    modifier = Modifier.matchParentSize(),
                )

                // Main banner
                AchievementBannerItem(
                    achievement = achievement,
                    isRare = isRare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .offset(y = slideOffset.dp)
                        .scale(scale),
                )
            }
        }
    }
}

/**
 * Lottie fireworks animation for rare achievements
 */
@Composable
private fun FireworksAnimation(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.fireworks),
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        speed = 1f,
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
    )
}

/**
 * Data class for particle state
 */
private data class Particle(
    val id: Int,
    val angle: Float,
    val distance: Float,
    val size: Float,
    val color: Color,
    val delay: Int,
    val duration: Int,
)

/**
 * Particle burst effect using Compose canvas
 */
@Composable
private fun ParticleBurstEffect(
    isActive: Boolean,
    particleCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = listOf(
        Color(0xFFFFD700), // Gold
        Color(0xFFFF6B6B), // Coral
        Color(0xFF4ECDC4), // Turquoise
        Color(0xFFFF8C42), // Orange
        Color(0xFF9B59B6), // Purple
        Color(0xFF3498DB), // Blue
        Color(0xFF2ECC71), // Green
        Color(0xFFFF69B4), // Hot Pink
    )

    val particles = remember {
        List(particleCount) { index ->
            val angle = (index.toFloat() / particleCount) * 360f
            Particle(
                id = index,
                angle = angle,
                distance = Random.nextFloat() * 80f + 40f,
                size = Random.nextFloat() * 6f + 3f,
                color = colors.random(),
                delay = Random.nextInt(0, 100),
                duration = Random.nextInt(800, 1500),
            )
        }
    }

    var animationProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            animationProgress = 0f
            val startTime = System.currentTimeMillis()
            while (animationProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                animationProgress = (elapsed / 1500f).coerceIn(0f, 1f)
                delay(16)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .drawBehind {
                if (animationProgress <= 0f) return@drawBehind

                val centerX = size.width / 2
                val centerY = size.height / 2

                particles.forEach { particle ->
                    val particleProgress = ((animationProgress * 1500 - particle.delay) / particle.duration)
                        .coerceIn(0f, 1f)

                    if (particleProgress > 0f) {
                        val easeOut = 1f - (1f - particleProgress) * (1f - particleProgress)
                        val currentDistance = particle.distance * easeOut
                        val alpha = 1f - particleProgress

                        val radian = Math.toRadians(particle.angle.toDouble())
                        val x = centerX + (cos(radian) * currentDistance).toFloat()
                        val y = centerY + (sin(radian) * currentDistance).toFloat() - 20f

                        drawCircle(
                            color = particle.color.copy(alpha = alpha),
                            radius = particle.size * (1f - particleProgress * 0.5f),
                            center = Offset(x, y),
                        )
                    }
                }
            },
    )
}

/**
 * Individual banner item with Aurora styling
 */
@Composable
private fun AchievementBannerItem(
    achievement: Achievement,
    isRare: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    // Glow animation for rare achievements
    var glowScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(isRare) {
        if (isRare) {
            while (true) {
                glowScale = 1.2f
                delay(500)
                glowScale = 1f
                delay(500)
            }
        }
    }

    val animatedGlow by animateFloatAsState(
        targetValue = glowScale,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "glow",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                if (isRare) {
                    shadowElevation = 20f
                    spotShadowColor = colors.accent.copy(alpha = 0.6f)
                    ambientShadowColor = colors.progressCyan.copy(alpha = 0.4f)
                }
            }
            .shadow(
                elevation = if (isRare) 20.dp else 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (isRare) colors.accent.copy(alpha = 0.6f) else colors.accent.copy(alpha = 0.5f),
                spotColor = if (isRare) {
                    colors.progressCyan.copy(
                        alpha = 0.5f,
                    )
                } else {
                    colors.progressCyan.copy(alpha = 0.3f)
                },
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = if (isRare) {
                        // Rare achievements keep gold/orange theme (universal for rarity)
                        listOf(
                            Color(0xFFFF6B00), // Orange
                            Color(0xFFFFD700), // Gold
                            Color(0xFFFF8C42), // Light Orange
                            Color(0xFFFFD700), // Gold
                        )
                    } else {
                        // Normal achievements use adaptive accent color
                        listOf(
                            colors.accent.copy(alpha = 0.7f).compositeOver(Color.White.copy(alpha = 0.3f)),
                            colors.accent,
                            colors.accent.copy(alpha = 0.8f).compositeOver(Color.Black.copy(alpha = 0.2f)),
                        )
                    },
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
            .border(
                width = if (isRare) 2.dp else 1.dp,
                color = if (isRare) {
                    Color.White.copy(alpha = 0.5f)
                } else {
                    Color.White.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(20.dp),
            )
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
                        if (isRare) {
                            // Pulsing glow for rare achievements
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700).copy(alpha = 0.4f * animatedGlow),
                                        Color.Transparent,
                                    ),
                                ),
                                radius = size.minDimension * 0.7f * animatedGlow,
                            )
                        }
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

                // Rare badge indicator
                if (isRare) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .background(
                                color = Color(0xFFFFD700),
                                shape = CircleShape,
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
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
                        text = if (isRare) "РЕДКОЕ ДОСТИЖЕНИЕ!" else "ДОСТИЖЕНИЕ РАЗБЛОКИРОВАНО!",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = if (isRare) 12.sp else 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = if (isRare) 2.sp else 1.5.sp,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.4f),
                                blurRadius = 4f,
                            ),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Achievement title with bold typography
                Text(
                    text = achievement.title,
                    color = Color.White,
                    fontSize = if (isRare) 20.sp else 18.sp,
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
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                // Points with special styling for rare
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isRare) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = "+${achievement.points} очков",
                        color = if (isRare) Color(0xFFFFD700) else Color.White.copy(alpha = 0.95f),
                        fontSize = if (isRare) 14.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        style = TextStyle(
                            shadow = if (isRare) {
                                Shadow(
                                    color = Color(0xFFFF6B00).copy(alpha = 0.5f),
                                    blurRadius = 8f,
                                )
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Global manager for showing achievement unlock banners
 * Supports deferred notifications when user is in reader/player
 */
object AchievementBannerManager {
    private var onShowCallback: ((Achievement) -> Unit)? = null
    private var onShowGroupCallback: ((List<Achievement>) -> Unit)? = null
    private val pendingAchievements = mutableListOf<Achievement>()
    private var isInReaderOrPlayer = false

    fun setOnShowCallback(callback: (Achievement) -> Unit) {
        onShowCallback = callback
    }

    fun setOnShowGroupCallback(callback: (List<Achievement>) -> Unit) {
        onShowGroupCallback = callback
    }

    /**
     * Call when entering reader or player to defer notifications
     */
    fun setInReaderOrPlayer(value: Boolean) {
        isInReaderOrPlayer = value
        if (!value && pendingAchievements.isNotEmpty()) {
            // User exited reader/player, show grouped notification
            showPendingAchievements()
        }
    }

    fun showAchievement(achievement: Achievement) {
        if (isInReaderOrPlayer) {
            // Defer notification until user exits reader/player
            pendingAchievements.add(achievement)
        } else {
            // Show immediately
            onShowCallback?.invoke(achievement)
        }
    }

    /**
     * Show all pending achievements as a group
     */
    private fun showPendingAchievements() {
        if (pendingAchievements.isEmpty()) return

        if (pendingAchievements.size == 1) {
            // Single achievement - show normal banner
            onShowCallback?.invoke(pendingAchievements.first())
        } else {
            // Multiple achievements - show group notification
            onShowGroupCallback?.invoke(pendingAchievements.toList())
        }
        pendingAchievements.clear()
    }

    /**
     * Get current pending count for UI display
     */
    fun getPendingCount(): Int = pendingAchievements.size

    /**
     * Get list of pending achievements and clear the queue
     */
    fun getPendingAchievements(): List<Achievement> {
        val list = pendingAchievements.toList()
        pendingAchievements.clear()
        return list
    }

    fun clear() {
        onShowCallback = null
        onShowGroupCallback = null
        pendingAchievements.clear()
    }
}
