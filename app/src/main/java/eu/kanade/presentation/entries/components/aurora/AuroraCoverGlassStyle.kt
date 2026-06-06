package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun Modifier.auroraCoverReadableCardStyle(
    colors: AuroraColors = AuroraTheme.colors,
    shape: Shape,
    cornerRadius: Dp,
    topAlpha: Float,
    centerAlpha: Float,
    bottomAlpha: Float,
    borderTopAlpha: Float = 0.90f,
    borderBottomAlpha: Float = 0.22f,
    neutralUnderlayAlpha: Float = 0.055f,
    accentUnderlayAlpha: Float = 0.030f,
): Modifier {
    return when {
        colors.isDark -> {
            this
        }

        colors.isEInk -> {
            this
                .background(
                    color = resolveAuroraHeroPanelContainerColor(colors),
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = resolveAuroraHeroPanelBorderColor(colors),
                    shape = shape,
                )
        }

        else -> {
            this
                .drawBehind {
                    val radiusPx = cornerRadius.toPx()
                    val cornerRadiusVal = CornerRadius(radiusPx, radiusPx)

                    drawRoundRect(
                        color = Color.Black.copy(alpha = neutralUnderlayAlpha),
                        topLeft = Offset(
                            x = 1.dp.toPx(),
                            y = 4.dp.toPx(),
                        ),
                        size = Size(
                            width = size.width - 2.dp.toPx(),
                            height = size.height,
                        ),
                        cornerRadius = cornerRadiusVal,
                    )

                    drawRoundRect(
                        color = colors.accent.copy(alpha = accentUnderlayAlpha),
                        topLeft = Offset(
                            x = 3.dp.toPx(),
                            y = 6.dp.toPx(),
                        ),
                        size = Size(
                            width = size.width - 6.dp.toPx(),
                            height = size.height,
                        ),
                        cornerRadius = cornerRadiusVal,
                    )
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = topAlpha),
                            Color.White.copy(alpha = centerAlpha),
                            Color.White.copy(alpha = bottomAlpha),
                        ),
                    ),
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = borderTopAlpha),
                            Color.White.copy(alpha = borderTopAlpha * 0.45f),
                            Color.White.copy(alpha = borderBottomAlpha),
                        ),
                    ),
                    shape = shape,
                )
        }
    }
}

@Composable
fun Modifier.auroraCoverHeroCardStyle(
    colors: AuroraColors = AuroraTheme.colors,
    shape: Shape,
    cornerRadius: Dp,
): Modifier {
    return auroraCoverReadableCardStyle(
        colors = colors,
        shape = shape,
        cornerRadius = cornerRadius,
        topAlpha = 0.92f,
        centerAlpha = 0.86f,
        bottomAlpha = 0.80f,
        neutralUnderlayAlpha = 0.060f,
        accentUnderlayAlpha = 0.032f,
    )
}

@Composable
fun Modifier.auroraCoverInfoCardStyle(
    colors: AuroraColors = AuroraTheme.colors,
    shape: Shape,
    cornerRadius: Dp,
): Modifier {
    return auroraCoverReadableCardStyle(
        colors = colors,
        shape = shape,
        cornerRadius = cornerRadius,
        topAlpha = 0.88f,
        centerAlpha = 0.82f,
        bottomAlpha = 0.76f,
        neutralUnderlayAlpha = 0.050f,
        accentUnderlayAlpha = 0.026f,
    )
}

@Composable
fun Modifier.auroraCoverChapterRowStyle(
    colors: AuroraColors = AuroraTheme.colors,
    shape: Shape,
    cornerRadius: Dp,
): Modifier {
    return auroraCoverReadableCardStyle(
        colors = colors,
        shape = shape,
        cornerRadius = cornerRadius,
        topAlpha = 0.90f,
        centerAlpha = 0.84f,
        bottomAlpha = 0.80f,
        neutralUnderlayAlpha = 0.045f,
        accentUnderlayAlpha = 0.022f,
    )
}

@Composable
fun Modifier.auroraCoverSectionVeilStyle(
    colors: AuroraColors = AuroraTheme.colors,
    shape: Shape,
): Modifier {
    return if (!colors.isDark && !colors.isEInk) {
        this
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.26f),
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.12f),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.20f),
                shape = shape,
            )
    } else {
        this
    }
}
