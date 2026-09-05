package eu.kanade.presentation.reader

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
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
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

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

    // B-A1: full aurora-glass treatment mirroring NovelImageActionsDialog (the novel reader's
    // image actions sheet). The manga sheet was plain Material surfaceVariant on a bare
    // AdaptiveSheet - no rim, no blur-behind, no e-ink branch - inside an otherwise aurora-styled
    // reader settings family.
    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    val supportsBlurBehind = eu.kanade.presentation.util.rememberSupportsBlurBehind(aurora.isEInk)

    val sheetContainer = remember(aurora.isDark, aurora.isEInk, supportsBlurBehind) {
        when {
            aurora.isEInk -> baseScheme.surfaceContainerHigh
            !supportsBlurBehind -> aurora.surface
            aurora.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        }
    }
    val auroraScheme = remember(baseScheme, aurora, sheetContainer) {
        baseScheme.copy(
            primary = aurora.accent,
            onPrimary = if (aurora.isDark) aurora.background else Color.White,
            surfaceContainerHigh = sheetContainer,
            surfaceContainerHighest = sheetContainer,
            secondaryContainer = aurora.accent.copy(alpha = 0.22f),
            onSecondaryContainer = aurora.accent,
        )
    }
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )

    val buttonColor = resolveReaderPageActionColor(buttonColorValue, aurora.accent)
    val labelColor = resolveReaderPageActionColor(labelColorValue, aurora.textPrimary)

    MaterialTheme(
        colorScheme = auroraScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        AdaptiveSheet(
            onDismissRequest = onDismissRequest,
            modifier = Modifier.border(
                width = 1.dp,
                color = auroraRimColor(),
                shape = sheetShape,
            ),
            containerColor = sheetContainer,
            scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
            applyStatusBarsPadding = false,
            onRevealChange = { sheetReveal = it },
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val revealState = rememberUpdatedState(sheetReveal)

            DisposableEffect(window, supportsBlurBehind) {
                val w = window
                if (w != null && supportsBlurBehind) {
                    w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    w.setDimAmount(0f)
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                }
                onDispose {
                    if (w != null && supportsBlurBehind) {
                        w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                        w.setDimAmount(0f)
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }

            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                if (!supportsBlurBehind) return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                    .distinctUntilChanged()
                    .collect { step ->
                        val glass = ((step / 20f - 0.18f) / 0.82f).coerceIn(0f, 1f)
                        val radius = if (glass <= 0.02f) 0 else (44f * glass).roundToInt().coerceIn(1, 48)
                        val attrs = w.attributes
                        if (attrs.blurBehindRadius != radius) {
                            w.attributes = attrs.apply { blurBehindRadius = radius }
                        }
                        w.setDimAmount(0.18f * glass)
                    }
            }

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
    // B-A2: was a bare M3 AlertDialog floating over the aurora-styled sheet; palette and
    // surfaces now follow the aurora scheme (e-ink gets an opaque container).
    val aurora = AuroraTheme.colors
    AlertDialog(
        containerColor = if (aurora.isEInk) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            aurora.surface
        },
        shape = RoundedCornerShape(24.dp),
        text = {
            Text(
                text = stringResource(MR.strings.confirm_set_image_as_cover),
                color = aurora.textPrimary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(MR.strings.action_ok), color = aurora.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel), color = aurora.textSecondary)
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
