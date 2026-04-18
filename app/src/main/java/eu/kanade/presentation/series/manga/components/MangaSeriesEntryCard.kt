package eu.kanade.presentation.series.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.components.aurora.AURORA_DIMMED_ITEM_ALPHA
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.series.isSeriesEntryCompleted
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaSeriesEntryCard(
    manga: LibraryManga,
    ordinalLabel: String?,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isCompleted = isSeriesEntryCompleted(manga.readCount, manga.totalChapters)
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        label = "series_entry_card_scale",
    )

    GlassmorphismCard(
        modifier = modifier
            .alpha(if (isCompleted) AURORA_DIMMED_ITEM_ALPHA else 1f)
            .graphicsLayer(
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

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                ItemCover.Book(
                    data = manga.manga.asMangaCover(),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.manga.title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        AYMR.strings.manga_series_chapters_progress,
                        manga.readCount.toInt(),
                        manga.totalChapters.toInt(),
                    ),
                    color = colors.textPrimary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Outlined.Done,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
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
}
