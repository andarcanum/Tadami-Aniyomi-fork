package eu.kanade.presentation.library.novel.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ItemCover
import tachiyomi.domain.entries.novel.model.NovelCover

@Composable
fun SeriesStackedCoverCard(
    covers: List<NovelCover>,
    customCoverData: Any? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    widthFactor: Float = 0.65f,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFactor)
                .aspectRatio(ItemCover.Book.ratio),
            contentAlignment = Alignment.Center,
        ) {
            // Z-order: furthest -> nearest -> center

            // 1. Far side items (indices 3 and 4)
            covers.getOrNull(3)?.let { SideCover(it, xOffset = (-22).dp, rotation = -16f, scale = 0.85f, alpha = 0.7f) }
            covers.getOrNull(4)?.let { SideCover(it, xOffset = 22.dp, rotation = 16f, scale = 0.85f, alpha = 0.7f) }

            // 2. Near side items (indices 1 and 2)
            covers.getOrNull(1)?.let { SideCover(it, xOffset = (-11).dp, rotation = -8f, scale = 0.92f, alpha = 0.85f) }
            covers.getOrNull(2)?.let { SideCover(it, xOffset = 11.dp, rotation = 8f, scale = 0.92f, alpha = 0.85f) }

            // 3. Center front (index 0 or custom)
            ItemCover.Book(
                data = customCoverData ?: covers.firstOrNull(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .shadow(8.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun BoxScope.SideCover(
    data: Any,
    xOffset: Dp,
    rotation: Float,
    scale: Float,
    alpha: Float,
) {
    ItemCover.Book(
        data = data,
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxSize(scale)
            .offset(x = xOffset)
            .rotate(rotation)
            .graphicsLayer { this.alpha = alpha }
            .shadow(2.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp)),
    )
}
