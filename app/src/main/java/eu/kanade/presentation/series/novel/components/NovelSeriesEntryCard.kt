package eu.kanade.presentation.series.novel.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.presentation.core.i18n.pluralStringResource

@Composable
fun NovelSeriesEntryCard(
    novel: LibraryNovel,
    ordinalLabel: String?,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        label = "series_entry_card_scale",
    )

    GlassmorphismCard(
        modifier = modifier.graphicsLayer(
            scaleX = cardScale,
            scaleY = cardScale,
        ),
        cornerRadius = 16.dp,
        verticalPadding = 4.dp,
        innerPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = null,
                tint = colors.textPrimary.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .then(dragHandleModifier),
            )

            ordinalLabel?.let {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .padding(start = 0.dp, end = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = it,
                        color = colors.textPrimary.copy(alpha = 0.85f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Cover
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                ItemCover.Book(
                    data = novel.novel.asNovelCover(),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = novel.novel.title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        AYMR.plurals.series_num_chapters,
                        count = novel.totalChapters.toInt(),
                        novel.totalChapters.toInt(),
                    ),
                    color = colors.textPrimary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }

            // Remove Button
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = colors.textPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}
