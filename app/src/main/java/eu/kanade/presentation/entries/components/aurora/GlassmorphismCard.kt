package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    innerPadding: Dp = 16.dp,
    overlayColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(cornerRadius)

    val cardModifier = if (!colors.isDark && !colors.isEInk) {
        val backgroundColors = listOf(
            Color.White.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.68f),
            Color.White.copy(alpha = 0.60f),
        )
        val tintedBgColors =
            overlayColor?.let { tintAuroraCardBackgroundColors(backgroundColors, it) } ?: backgroundColors

        modifier
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .drawBehind {
                val radius = cornerRadius.toPx()
                val cornerRadiusPx = CornerRadius(radius, radius)

                val neutralOffsetY = 3.dp.toPx()
                val warmOffsetY = 5.dp.toPx()

                val neutralInset = 1.dp.toPx()
                val warmInset = 3.dp.toPx()

                // 1. Нейтральная тень
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.035f),
                    topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                    size = Size(width = size.width - neutralInset * 2, height = size.height),
                    cornerRadius = cornerRadiusPx,
                )

                // 2. Акцентное свечение (под цвет темы)
                drawRoundRect(
                    color = colors.accent.copy(alpha = 0.025f),
                    topLeft = Offset(x = warmInset, y = warmOffsetY),
                    size = Size(width = size.width - warmInset * 2, height = size.height),
                    cornerRadius = cornerRadiusPx,
                )
            }
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(colors = tintedBgColors),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.12f),
                    ),
                ),
                shape = shape,
            )
            .padding(innerPadding)
    } else {
        val bgColors = resolveAuroraDetailCardBackgroundColors(colors)
        val borderColors = resolveAuroraDetailCardBorderColors(colors)
        val tintedBgColors = overlayColor?.let { tintAuroraCardBackgroundColors(bgColors, it) } ?: bgColors
        val borderBrush = if (colors.isDark) {
            auroraMenuRimLightBrush(colors)
        } else {
            Brush.linearGradient(colors = borderColors)
        }
        modifier
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clip(shape)
            .background(brush = Brush.linearGradient(colors = tintedBgColors))
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape,
            )
            .padding(innerPadding)
    }

    Box(
        modifier = cardModifier,
    ) {
        content()
    }
}

internal fun tintAuroraCardBackgroundColors(
    backgroundColors: List<Color>,
    overlayColor: Color,
): List<Color> {
    return backgroundColors.map { backgroundColor ->
        overlayColor.compositeOver(backgroundColor)
    }
}
