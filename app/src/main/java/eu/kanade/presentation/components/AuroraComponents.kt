package eu.kanade.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val animatedAuroraBackground by uiPreferences.animatedAuroraBackground().collectAsState()

    AuroraAmbientBackground(
        enabled = animatedAuroraBackground,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun AuroraAmbientBackground(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AuroraTheme.colors
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val shouldAnimate = shouldAnimateAuroraBackground(
        userEnabled = enabled,
        isLifecycleResumed = isLifecycleResumed,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        if (enabled) {
            if (shouldAnimate) {
                val transition = rememberInfiniteTransition(label = "auroraAmbient")
                val blobOneDriftX by transition.animateFloat(
                    initialValue = -0.08f,
                    targetValue = 0.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 26000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobOneDriftX",
                )
                val blobOneDriftY by transition.animateFloat(
                    initialValue = -0.05f,
                    targetValue = 0.04f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 30000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobOneDriftY",
                )
                val blobTwoDriftX by transition.animateFloat(
                    initialValue = 0.08f,
                    targetValue = -0.06f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 22000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobTwoDriftX",
                )
                val blobTwoDriftY by transition.animateFloat(
                    initialValue = 0.02f,
                    targetValue = -0.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 28000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobTwoDriftY",
                )
                val blobThreeDriftX by transition.animateFloat(
                    initialValue = -0.03f,
                    targetValue = 0.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 20000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobThreeDriftX",
                )
                val blobThreeDriftY by transition.animateFloat(
                    initialValue = 0.06f,
                    targetValue = -0.04f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 24000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobThreeDriftY",
                )
                val blobOneAlpha by transition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 18000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobOneAlpha",
                )
                val blobTwoAlpha by transition.animateFloat(
                    initialValue = 0.12f,
                    targetValue = 0.22f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 20000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobTwoAlpha",
                )
                val blobThreeAlpha by transition.animateFloat(
                    initialValue = 0.1f,
                    targetValue = 0.18f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 24000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blobThreeAlpha",
                )

                AuroraAmbientCanvas(
                    colors = colors,
                    blobOneDriftX = blobOneDriftX,
                    blobOneDriftY = blobOneDriftY,
                    blobTwoDriftX = blobTwoDriftX,
                    blobTwoDriftY = blobTwoDriftY,
                    blobThreeDriftX = blobThreeDriftX,
                    blobThreeDriftY = blobThreeDriftY,
                    blobOneAlpha = blobOneAlpha,
                    blobTwoAlpha = blobTwoAlpha,
                    blobThreeAlpha = blobThreeAlpha,
                )
            } else {
                AuroraAmbientCanvas(
                    colors = colors,
                    blobOneDriftX = -0.08f,
                    blobOneDriftY = -0.05f,
                    blobTwoDriftX = 0.08f,
                    blobTwoDriftY = 0.02f,
                    blobThreeDriftX = -0.03f,
                    blobThreeDriftY = 0.06f,
                    blobOneAlpha = 0.2f,
                    blobTwoAlpha = 0.12f,
                    blobThreeAlpha = 0.1f,
                )
            }
        }

        content()
    }
}

@Composable
private fun AuroraAmbientCanvas(
    colors: eu.kanade.presentation.theme.AuroraColors,
    blobOneDriftX: Float,
    blobOneDriftY: Float,
    blobTwoDriftX: Float,
    blobTwoDriftY: Float,
    blobThreeDriftX: Float,
    blobThreeDriftY: Float,
    blobOneAlpha: Float,
    blobTwoAlpha: Float,
    blobThreeAlpha: Float,
) {
    Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        fun drawAmbientBlob(
            centerFractionX: Float,
            centerFractionY: Float,
            driftX: Float,
            driftY: Float,
            radiusFraction: Float,
            aspectX: Float,
            aspectY: Float,
            color: Color,
            alpha: Float,
        ) {
            val center = Offset(
                x = size.width * (centerFractionX + driftX),
                y = size.height * (centerFractionY + driftY),
            )
            val radius = size.minDimension * radiusFraction
            withTransform({
                translate(
                    left = center.x - radius * aspectX,
                    top = center.y - radius * aspectY,
                )
                scale(scaleX = aspectX, scaleY = aspectY, pivot = Offset.Zero)
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = alpha),
                            Color.Transparent,
                        ),
                        center = Offset(radius, radius),
                        radius = radius,
                    ),
                    radius = radius,
                )
            }
        }

        val blobBaseAlpha = if (colors.isDark) 1f else 0.78f
        drawAmbientBlob(
            centerFractionX = 0.22f,
            centerFractionY = 0.14f,
            driftX = blobOneDriftX,
            driftY = blobOneDriftY,
            radiusFraction = 0.5f,
            aspectX = 1.35f,
            aspectY = 0.8f,
            color = colors.glowEffect,
            alpha = blobOneAlpha * blobBaseAlpha,
        )
        drawAmbientBlob(
            centerFractionX = 0.84f,
            centerFractionY = 0.22f,
            driftX = blobTwoDriftX,
            driftY = blobTwoDriftY,
            radiusFraction = 0.44f,
            aspectX = 1.15f,
            aspectY = 0.72f,
            color = colors.gradientPurple,
            alpha = blobTwoAlpha * blobBaseAlpha,
        )
        drawAmbientBlob(
            centerFractionX = 0.48f,
            centerFractionY = 0.72f,
            driftX = blobThreeDriftX,
            driftY = blobThreeDriftY,
            radiusFraction = 0.52f,
            aspectX = 1.45f,
            aspectY = 0.9f,
            color = colors.accent,
            alpha = blobThreeAlpha * 0.72f,
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    colors.background.copy(alpha = if (colors.isDark) 0.2f else 0.12f),
                    colors.background.copy(alpha = if (colors.isDark) 0.38f else 0.2f),
                ),
                center = Offset(size.width * 0.5f, size.height * 0.4f),
                radius = size.maxDimension * 0.92f,
            ),
        )
    }
}

internal fun shouldAnimateAuroraBackground(
    userEnabled: Boolean,
    isLifecycleResumed: Boolean,
): Boolean {
    return userEnabled && isLifecycleResumed
}

@Composable
fun AuroraHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    // Reusable header component if needed, currently TopBars are custom per screen
}
