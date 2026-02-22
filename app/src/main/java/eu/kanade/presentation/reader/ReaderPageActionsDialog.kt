package eu.kanade.presentation.reader

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSetAsCover: () -> Unit,
    onShare: (Boolean) -> Unit,
    onSave: () -> Unit,
    buttonColorValue: Int,
    labelColorValue: Int,
    onButtonColorChange: (Int) -> Unit,
    onLabelColorChange: (Int) -> Unit,
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }
    var showColorSettings by remember { mutableStateOf(false) }
    val buttonColor = resolveReaderPageActionColor(buttonColorValue, MaterialTheme.colorScheme.surfaceVariant)
    val labelColor = resolveReaderPageActionColor(labelColorValue, MaterialTheme.colorScheme.onSurfaceVariant)

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ReaderPageActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.set_as_cover),
                    icon = Icons.Outlined.Photo,
                    buttonColor = buttonColor,
                    labelColor = labelColor,
                    onClick = { showSetCoverDialog = true },
                )
                ReaderPageActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_copy_to_clipboard),
                    icon = Icons.Outlined.ContentCopy,
                    buttonColor = buttonColor,
                    labelColor = labelColor,
                    onClick = {
                        onShare(true)
                        onDismissRequest()
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ReaderPageActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_share),
                    icon = Icons.Outlined.Share,
                    buttonColor = buttonColor,
                    labelColor = labelColor,
                    onClick = {
                        onShare(false)
                        onDismissRequest()
                    },
                )
                ReaderPageActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_save),
                    icon = Icons.Outlined.Save,
                    buttonColor = buttonColor,
                    labelColor = labelColor,
                    onClick = {
                        onSave()
                        onDismissRequest()
                    },
                )
            }

            TextButton(
                onClick = { showColorSettings = !showColorSettings },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(MR.strings.reader_page_actions_customize_colors),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (showColorSettings) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReaderPageActionColorRow(
                        label = stringResource(MR.strings.reader_page_actions_button_color),
                        selectedColor = buttonColorValue,
                        onColorSelected = onButtonColorChange,
                    )
                    ReaderPageActionColorRow(
                        label = stringResource(MR.strings.reader_page_actions_label_color),
                        selectedColor = labelColorValue,
                        onColorSelected = onLabelColorChange,
                    )
                }
            }
        }
    }

    if (showSetCoverDialog) {
        SetCoverDialog(
            onConfirm = {
                onSetAsCover()
                showSetCoverDialog = false
            },
            onDismiss = { showSetCoverDialog = false },
        )
    }
}

@Composable
private fun SetCoverDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(stringResource(MR.strings.confirm_set_image_as_cover))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun ReaderPageActionButton(
    title: String,
    icon: ImageVector,
    buttonColor: Color,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = buttonColor.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = buttonColor.copy(alpha = 0.45f),
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor,
            )
            Text(
                text = title,
                textAlign = TextAlign.Center,
                color = labelColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(
                    lineBreak = LineBreak.Paragraph,
                    hyphens = Hyphens.Auto,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
    }
}

@Composable
private fun ReaderPageActionColorRow(
    label: String,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val colors = listOf(
        0,
        0xFFE53935.toInt(),
        0xFFF4511E.toInt(),
        0xFFFFC107.toInt(),
        0xFF43A047.toInt(),
        0xFF1E88E5.toInt(),
        0xFF8E24AA.toInt(),
        0xFF00ACC1.toInt(),
        0xFF6D4C41.toInt(),
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { rawColor ->
            val swatchColor = if (rawColor == 0) themeColor else Color(rawColor)
            ReaderPageActionColorCircle(
                color = swatchColor,
                selected = selectedColor == rawColor,
                showThemeLabel = rawColor == 0,
                onClick = { onColorSelected(rawColor) },
            )
        }
    }
}

@Composable
private fun ReaderPageActionColorCircle(
    color: Color,
    selected: Boolean,
    showThemeLabel: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(color = color, shape = CircleShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showThemeLabel) {
            Text(
                text = stringResource(MR.strings.label_default).take(1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private fun resolveReaderPageActionColor(
    storedValue: Int,
    fallback: Color,
): Color {
    return if (storedValue == 0) fallback else Color(storedValue)
}
