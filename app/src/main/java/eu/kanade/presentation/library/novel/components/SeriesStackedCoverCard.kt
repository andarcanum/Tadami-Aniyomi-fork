package eu.kanade.presentation.library.novel.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ItemCover
import tachiyomi.domain.entries.novel.model.NovelCover

@Composable
fun SeriesStackedCoverCard(
    covers: List<NovelCover>,
    customCoverData: Any? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    Box(
        modifier = modifier
            .aspectRatio(ItemCover.Book.ratio),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        ) {
            // Third cover (bottom)
            covers.getOrNull(2)?.let { cover ->
                ItemCover.Book(
                    data = cover,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = 8.dp, y = (-4).dp)
                        .rotate(4f)
                        .shadow(4.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp)),
                )
            }

            // Second cover (middle)
            covers.getOrNull(1)?.let { cover ->
                ItemCover.Book(
                    data = cover,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = 4.dp, y = (-2).dp)
                        .rotate(2f)
                        .shadow(4.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp)),
                )
            }

            // First cover (top)
            ItemCover.Book(
                data = customCoverData ?: covers.firstOrNull(),
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(4.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}
