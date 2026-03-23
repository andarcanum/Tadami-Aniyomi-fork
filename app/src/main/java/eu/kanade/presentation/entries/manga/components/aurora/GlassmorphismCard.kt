package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.aurora.resolveAuroraDetailCardBackgroundColors
import eu.kanade.presentation.entries.components.aurora.resolveAuroraDetailCardBorderColors
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    innerPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors
    val bgColors = resolveAuroraDetailCardBackgroundColors(colors)
    val borderColors = resolveAuroraDetailCardBorderColors(colors)

    Box(
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush = Brush.linearGradient(colors = bgColors))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(colors = borderColors),
                shape = RoundedCornerShape(cornerRadius),
            )
            .padding(innerPadding),
    ) {
        content()
    }
}
