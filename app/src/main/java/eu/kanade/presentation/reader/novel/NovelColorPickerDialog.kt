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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Preset highlight colors shown as one-tap swatches. */
val NOVEL_HIGHLIGHT_PRESET_COLORS = listOf(
    0xFFFBC02D,
    0xFFA5D6A7,
    0xFF90CAF9,
    0xFFF48FB1,
    0xFFCE93D8,
)

/** Parses `#RRGGBB` / `#AARRGGBB` into an ARGB long, or null when malformed. */
fun parseNovelHighlightHexColor(value: String): Long? {
    val trimmed = value.trim().removePrefix("#")
    val argb = when (trimmed.length) {
        6 -> 0xFF000000L or (trimmed.toLongOrNull(16) ?: return null)
        8 -> trimmed.toLongOrNull(16) ?: return null
        else -> return null
    }
    return argb
}

/** Formats an ARGB long as `#AARRGGBB` (the same order [parseNovelHighlightHexColor] reads). */
fun formatNovelHighlightHexColor(argb: Long): String {
    return String.format(
        java.util.Locale.US,
        "#%02X%02X%02X%02X",
        (argb shr 24) and 0xFF,
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
    )
}

/**
 * Arbitrary-color picker: preset swatches plus RGB sliders with a hex field. Reports the picked
 * ARGB long through [onPick].
 */
@Composable
fun NovelColorPickerDialog(
    initial: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var red by remember { mutableFloatStateOf(((initial shr 16) and 0xFF).toFloat()) }
    var green by remember { mutableFloatStateOf(((initial shr 8) and 0xFF).toFloat()) }
    var blue by remember { mutableFloatStateOf((initial and 0xFF).toFloat()) }
    var hexInput by remember { mutableStateOf(formatNovelHighlightHexColor(initial)) }

    fun currentArgb(): Long {
        val r = red.toInt().coerceIn(0, 255)
        val g = green.toInt().coerceIn(0, 255)
        val b = blue.toInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(currentArgb()) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NOVEL_HIGHLIGHT_PRESET_COLORS.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(preset), CircleShape)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    red = ((preset shr 16) and 0xFF).toFloat()
                                    green = ((preset shr 8) and 0xFF).toFloat()
                                    blue = (preset and 0xFF).toFloat()
                                    hexInput = formatNovelHighlightHexColor(preset)
                                },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Color(currentArgb()),
                                CircleShape,
                            )
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                    )
                }
                ColorSlider(label = "R", value = red, onChange = {
                    red = it
                    hexInput =
                        formatNovelHighlightHexColor(currentArgb())
                })
                ColorSlider(label = "G", value = green, onChange = {
                    green = it
                    hexInput =
                        formatNovelHighlightHexColor(currentArgb())
                })
                ColorSlider(label = "B", value = blue, onChange = {
                    blue = it
                    hexInput =
                        formatNovelHighlightHexColor(currentArgb())
                })
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input
                        parseNovelHighlightHexColor(input)?.let { argb ->
                            red = ((argb shr 16) and 0xFF).toFloat()
                            green = ((argb shr 8) and 0xFF).toFloat()
                            blue = (argb and 0xFF).toFloat()
                        }
                    },
                    singleLine = true,
                    label = { Text(text = "#RRGGBB") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 12.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
    }
}
