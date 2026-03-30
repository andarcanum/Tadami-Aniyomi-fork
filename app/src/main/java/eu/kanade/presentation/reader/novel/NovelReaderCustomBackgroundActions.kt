package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File

private val CustomBackgroundCardWidth = 160.dp
private val CustomBackgroundCardHeight = 214.dp
private val CustomBackgroundPreviewWidth = 148.dp
private val CustomBackgroundPreviewHeight = 92.dp

@Composable
internal fun NovelReaderCustomBackgroundCard(
    customItem: NovelReaderCustomBackgroundItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(CustomBackgroundCardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
        ) {
            Column(
                modifier = Modifier
                    .padding(6.dp)
                    .size(
                        width = CustomBackgroundCardWidth - 12.dp,
                        height = CustomBackgroundCardHeight,
                    ),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = File(customItem.absolutePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(
                            width = CustomBackgroundPreviewWidth,
                            height = CustomBackgroundPreviewHeight,
                        ),
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(AYMR.strings.editor_action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Text(
                    text = customItem.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = customItem.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NovelReaderCustomBackgroundActionChip(
                        label = stringResource(AYMR.strings.editor_action_rename),
                        onClick = onRename,
                    )
                    NovelReaderCustomBackgroundActionChip(
                        label = stringResource(AYMR.strings.novel_reader_background_action_replace),
                        onClick = onReplace,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelReaderCustomBackgroundActionChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
