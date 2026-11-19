package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.rememberLiveBackdropBlurEnabled

@Composable
fun AuroraCompactEntryRowCard(
    modifier: Modifier = Modifier,
    colors: AuroraColors = AuroraTheme.colors,
    selected: Boolean = false,
    highlighted: Boolean = false,
    cornerRadius: Dp = 20.dp,
    outerVerticalPadding: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    hazeState: HazeState? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val overlayColor = when {
        selected -> colors.accent.copy(alpha = 0.14f)
        highlighted -> colors.accent.copy(alpha = 0.055f)
        else -> Color.Transparent
    }

    // Frost family of the title-screen hero panel: live blur under a translucent surface
    // on capable devices; lightweight gradient fallback on API < 31 / low-RAM / battery saver.
    val liveBlurEnabled = rememberLiveBackdropBlurEnabled(isEInkMode = colors.isEInk)
    val haze = hazeState?.takeIf { liveBlurEnabled }
    val hazeStyle = if (haze != null) {
        remember(colors.isDark) {
            HazeBlurStyle {
                backgroundColor(Color.Transparent)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            Color.White.copy(alpha = if (colors.isDark) 0.10f else 0.18f),
                        ),
                    ),
                )
                blurRadius(20.dp)
                noiseFactor(0f)
            }
        }
    } else {
        null
    }
    val hazeModifier = if (haze != null && hazeStyle != null) {
        Modifier.hazeBlur(
            input = HazeInput.Sources(haze),
            style = hazeStyle,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = outerVerticalPadding),
    ) {
        val cardModifier = if (haze != null) {
            // Frost family of the title-screen hero panel: live blur under a translucent surface.
            if (!colors.isDark) {
                Modifier
                    .clip(shape)
                    .then(hazeModifier)
                    .auroraCoverHeroCardStyle(
                        colors = colors,
                        shape = shape,
                        cornerRadius = cornerRadius,
                    )
            } else {
                val darkFrostBrush = remember {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    )
                }
                val darkRimBrush = remember(colors) { auroraMenuRimLightBrush(colors) }
                Modifier
                    .clip(shape)
                    .then(hazeModifier)
                    .background(brush = darkFrostBrush)
                    .border(
                        width = 1.dp,
                        brush = darkRimBrush,
                        shape = shape,
                    )
            }
        } else if (!colors.isDark && !colors.isEInk) {
            val lightBackgroundBrush = remember {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.78f),
                        Color.White.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.60f),
                    ),
                )
            }
            val lightBorderBrush = remember {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.12f),
                    ),
                )
            }
            Modifier
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
                    brush = lightBackgroundBrush,
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    brush = lightBorderBrush,
                    shape = shape,
                )
        } else {
            val bgBrush = remember(colors) {
                Brush.linearGradient(colors = resolveAuroraDetailCardBackgroundColors(colors))
            }
            val borderBrush = remember(colors) {
                if (colors.isDark) {
                    auroraMenuRimLightBrush(colors)
                } else {
                    Brush.linearGradient(colors = resolveAuroraDetailCardBorderColors(colors))
                }
            }
            Modifier
                .clip(shape)
                .background(brush = bgBrush)
                .border(
                    width = 1.dp,
                    brush = borderBrush,
                    shape = shape,
                )
        }

        Box(
            modifier = cardModifier
                .background(overlayColor, shape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(contentPadding),
        ) {
            content()
        }
    }
}
