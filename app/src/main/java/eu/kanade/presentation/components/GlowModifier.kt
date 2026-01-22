package eu.kanade.presentation.components

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
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
fun Modifier.gradientBorderGlow(
    colors: List<Color>,
    borderWidth: Dp = 2.dp,
    glowRadius: Dp = 12.dp,
    alpha: Float = 0.7f,
): Modifier = this.drawBehind {
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
        width = borderWidth.toPx(),
    )

    // Draw outer glow layers
    for (i in 1..3) {
        val glowAlpha = alpha / (i * 1.5f)
        colors.forEach { color ->
            drawRoundRect(
                color = color.copy(alpha = glowAlpha),
                style = stroke,
                size = size.copy(
                    width = size.width + (glowRadius.toPx() * i / 3),
                    height = size.height + (glowRadius.toPx() * i / 3),
                ),
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = -(glowRadius.toPx() * i / 6),
                    y = -(glowRadius.toPx() * i / 6),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
            )
        }
    }

    // Draw main gradient border
    val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(colors)
    drawRoundRect(
        brush = brush,
        style = stroke,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
    )
}
