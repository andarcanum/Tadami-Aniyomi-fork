package eu.kanade.presentation.components

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aniview Premium Glow Effect
 * Adds an outer glow to the composable using shadow or blur effects
 */
fun Modifier.glowEffect(
    color: Color,
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.6f,
    shape: Shape? = null,
): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+: Use RenderEffect for better blur
        this.graphicsLayer {
            renderEffect = android.graphics.RenderEffect
                .createBlurEffect(
                    blurRadius.toPx(),
                    blurRadius.toPx(),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                .asComposeRenderEffect()
        }
    } else {
        // Fallback: Use shadow with elevated effect
        this.shadow(
            elevation = blurRadius,
            shape = shape ?: androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            ambientColor = color.copy(alpha = alpha),
            spotColor = color.copy(alpha = alpha),
        )
    }
}

/**
 * Cyan glow effect for library cards and progress indicators
 */
fun Modifier.cyanGlow(
    blurRadius: Dp = 12.dp,
    alpha: Float = 0.15f,
): Modifier = glowEffect(
    color = Color(0xFF00E5FF),
    blurRadius = blurRadius,
    alpha = alpha,
)

/**
 * Electric blue glow for active elements and buttons
 */
fun Modifier.electricBlueGlow(
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.6f,
): Modifier = glowEffect(
    color = Color(0xFF0095FF),
    blurRadius = blurRadius,
    alpha = alpha,
)

/**
 * Purple glow for premium features
 */
fun Modifier.purpleGlow(
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.5f,
): Modifier = glowEffect(
    color = Color(0xFF7C4DFF),
    blurRadius = blurRadius,
    alpha = alpha,
)

/**
 * Gradient border glow effect for hero cards
 * Creates a glowing border effect around the composable
 */
internal const val GradientBorderGlowLayerCount = 3

internal data class GradientBorderGlowPass(
    val alpha: Float,
    val expansionPx: Float,
    val offsetPx: Float,
)

internal fun gradientBorderGlowPasses(
    alpha: Float,
    glowRadiusPx: Float,
): List<GradientBorderGlowPass> = (1..GradientBorderGlowLayerCount).map { layer ->
    GradientBorderGlowPass(
        alpha = alpha / (layer * 1.5f),
        expansionPx = glowRadiusPx * layer / 3f,
        offsetPx = glowRadiusPx * layer / 6f,
    )
}

fun Modifier.gradientBorderGlow(
    colors: List<Color>,
    borderWidth: Dp = 2.dp,
    glowRadius: Dp = 12.dp,
    alpha: Float = 0.7f,
    cornerRadius: Dp = 24.dp,
): Modifier = this.drawWithCache {
    val stroke = Stroke(width = borderWidth.toPx())
    val cornerRadiusPx = cornerRadius.toPx()
    val glowRadiusPx = glowRadius.toPx()
    val glowPasses = gradientBorderGlowPasses(
        alpha = alpha,
        glowRadiusPx = glowRadiusPx,
    )
    val brush = Brush.horizontalGradient(colors)

    onDrawBehind {
        glowPasses.forEach { pass ->
            colors.forEach { color ->
                drawRoundRect(
                    color = color.copy(alpha = pass.alpha),
                    style = stroke,
                    size = size.copy(
                        width = size.width + pass.expansionPx,
                        height = size.height + pass.expansionPx,
                    ),
                    topLeft = Offset(
                        x = -pass.offsetPx,
                        y = -pass.offsetPx,
                    ),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                )
            }
        }

        drawRoundRect(
            brush = brush,
            style = stroke,
            cornerRadius = CornerRadius(cornerRadiusPx),
        )
    }
}
