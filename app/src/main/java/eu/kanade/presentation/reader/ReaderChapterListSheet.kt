package eu.kanade.presentation.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

data class ReaderChapterListItem(
    val id: Long,
    val title: String,
    val dateText: String? = null,
    val scanlator: String? = null,
    val isCurrent: Boolean = false,
)

@Composable
fun ReaderChapterListSheet(
    items: List<ReaderChapterListItem>,
    onDismissRequest: () -> Unit,
    onChapterClick: (Long) -> Unit,
    onDownloadClick: (Long) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = resolveReaderChapterListStartIndex(items),
    )
    val startIndex = remember(items) {
        resolveReaderChapterListStartIndex(items)
    }

    LaunchedEffect(items, startIndex) {
        if (items.isNotEmpty()) {
            listState.scrollToItem(startIndex.coerceIn(0, items.lastIndex))
        }
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(MR.strings.chapters),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyChapterListState()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = items,
                        key = { item -> item.id },
                    ) { item ->
                        ReaderChapterListRow(
                            item = item,
                            onChapterClick = onChapterClick,
                            onDownloadClick = onDownloadClick,
                        )
                    }
                }
            }
        }
    }
}

internal fun resolveReaderChapterListStartIndex(items: List<ReaderChapterListItem>): Int {
    return items.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
}

@Composable
private fun ReaderChapterListRow(
    item: ReaderChapterListItem,
    onChapterClick: (Long) -> Unit,
    onDownloadClick: (Long) -> Unit,
) {
    val appHaptics = LocalAppHaptics.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val containerColor = if (item.isCurrent) {
        colors.primary.copy(alpha = if (isSystemInDarkTheme()) 0.14f else 0.10f)
    } else {
        Color.Transparent
    }
    val containerBorder = if (item.isCurrent) {
        BorderStroke(
            width = 1.dp,
            color = colors.primary.copy(alpha = 0.28f),
        )
    } else {
        null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        border = containerBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    appHaptics.tap()
                    onChapterClick(item.id)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isCurrent) {
                            AuroraTheme.colors.accent
                        } else {
                            colors.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val hasDate = item.dateText?.isNotBlank() == true
                val hasScanlator = item.scanlator?.isNotBlank() == true
                if (hasDate || hasScanlator) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (hasDate) {
                            Text(
                                text = item.dateText.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (hasDate && hasScanlator) {
                            DotSeparatorText()
                        }
                        if (hasScanlator) {
                            Text(
                                text = item.scanlator.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            ReaderChapterListDownloadButton(
                onClick = {
                    appHaptics.tap()
                    onDownloadClick(item.id)
                },
            )
        }
    }
}

@Composable
private fun ReaderChapterListDownloadButton(
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val tint = colors.textPrimary

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.28f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = stringResource(MR.strings.action_download),
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EmptyChapterListState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ViewList,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(MR.strings.no_chapters_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
