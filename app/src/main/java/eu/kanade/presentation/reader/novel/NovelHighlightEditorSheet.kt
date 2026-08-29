package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Bottom sheet for one saved highlight: snippet preview, note field, color swatches with an
 * arbitrary-color picker, and copy/share/delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelHighlightEditorSheet(
    highlight: NovelHighlight,
    onDismiss: () -> Unit,
    onSave: (note: String, colorArgb: Long) -> Unit,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    var note by remember(highlight.id) { mutableStateOf(highlight.note) }
    var colorArgb by remember(highlight.id) { mutableStateOf(highlight.colorArgb) }
    var showColorPicker by remember(highlight.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "«" + highlight.normalizedText + "»",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NOVEL_HIGHLIGHT_PRESET_COLORS.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color(preset), CircleShape)
                            .border(
                                width = if (colorArgb == preset) 2.dp else 1.dp,
                                color = if (colorArgb == preset) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape,
                            )
                            .clickable { colorArgb = preset },
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(colorArgb), CircleShape)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                        .clickable { showColorPicker = true },
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = false,
                label = { Text(text = stringResource(AYMR.strings.novel_highlight_editor_note_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    IconButton(onClick = { onCopy(highlight.normalizedText) }) {
                        Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null)
                    }
                    IconButton(onClick = { onShare(highlight.normalizedText) }) {
                        Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(AYMR.strings.novel_highlight_action_delete),
                        )
                    }
                }
                TextButton(
                    onClick = { onSave(note, colorArgb) },
                ) {
                    Text(text = stringResource(AYMR.strings.novel_highlight_action_save))
                }
            }
        }
    }
    if (showColorPicker) {
        NovelColorPickerDialog(
            initial = colorArgb,
            onPick = { picked ->
                colorArgb = picked
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
}
