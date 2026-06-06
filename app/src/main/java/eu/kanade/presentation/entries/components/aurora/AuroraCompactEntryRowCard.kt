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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun AuroraCompactEntryRowCard(
    modifier: Modifier = Modifier,
    colors: AuroraColors = AuroraTheme.colors,
    selected: Boolean = false,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    cornerRadius: Dp = 20.dp,
    outerVerticalPadding: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val contentAlpha = if (dimmed) 0.68f else 1f
    val overlayColor = when {
        selected -> colors.accent.copy(alpha = 0.14f)
        highlighted -> colors.accent.copy(alpha = 0.055f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = outerVerticalPadding),
    ) {
        val cardModifier = if (!colors.isDark && !colors.isEInk) {
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
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.68f),
                            Color.White.copy(alpha = 0.60f),
                        ),
                    ),
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                    shape = shape,
                )
        } else {
            val bgColors = resolveAuroraDetailCardBackgroundColors(colors)
            val borderColors = resolveAuroraDetailCardBorderColors(colors)
            Modifier
                .clip(shape)
                .background(brush = Brush.linearGradient(colors = bgColors))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(colors = borderColors),
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
            Box(
                modifier = Modifier.alpha(contentAlpha),
                content = content,
            )
        }
    }
}
