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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable glassmorphism card component for Aurora theme.
 *
 * Provides semi-transparent background with gradient border effect
 * to create depth against poster backgrounds.
 */
@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    innerPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.1f),
                    ),
                ),
                shape = RoundedCornerShape(cornerRadius),
            )
            .padding(innerPadding),
    ) {
        content()
    }
}
