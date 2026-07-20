package eu.kanade.presentation.entries.components.aurora

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.applyAuroraSheetWindowFx
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import android.graphics.Color as AndroidColor

@Composable
fun AuroraNotePreviewCard(
    note: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (note.isBlank()) return

    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(18.dp)

    GlassmorphismCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        innerPadding = 14.dp,
        overlayColor = colors.accent.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            width = 1.dp,
                            color = colors.accent.copy(alpha = 0.24f),
                            shape = shape,
                        )
                        .background(colors.accent.copy(alpha = 0.14f), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = stringResource(MR.strings.action_notes),
                    color = colors.accent,
                    fontSize = 12.sp,
                )
            }

            Text(
                text = note,
                color = colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 2.dp),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Notes editor — variant N3: minimal AdaptiveSheet.
 * Top bar Cancel | title | Save, almost full-height bare text field, char count.
 */
@Composable
fun AuroraNoteEditorDialog(
    initialText: String,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val accent = if (colors.isEInk) colors.textPrimary else colors.accent
    val supportsBlurBehind = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !colors.isEInk
    var sheetReveal by remember { mutableFloatStateOf(0f) }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        containerColor = when {
            colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
            !supportsBlurBehind -> colors.surface
            colors.isDark -> Color.Black.copy(alpha = 0.78f)
            else -> Color.White.copy(alpha = 0.92f)
        },
        scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
        onRevealChange = { sheetReveal = it },
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val revealState = rememberUpdatedState(sheetReveal)

        DisposableEffect(window, supportsBlurBehind) {
            val w = window
            if (w != null && supportsBlurBehind) {
                w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
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
                .collect { step -> applyAuroraSheetWindowFx(w, step / 20f) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = if (colors.isDark) 0.18f else 0.14f)),
            )

            // Top chrome: Cancel · Notes · Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MR.strings.action_cancel),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            appHaptics.tap()
                            onDismissRequest()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )

                Text(
                    text = stringResource(MR.strings.action_notes),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                        .clickable {
                            appHaptics.tap()
                            onSave(text)
                            onDismissRequest()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.action_save),
                        color = if (colors.isEInk) colors.background else colors.textOnAccent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Bare multi-line field — max writing space (N3)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp)
                    .focusRequester(focusRequester)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                ),
                cursorBrush = SolidColor(accent),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(MR.strings.information_required_plain),
                                color = colors.textSecondary.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                            )
                        }
                        inner()
                    }
                },
            )

            Text(
                text = text.length.toString(),
                color = colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp, bottom = 12.dp, end = 4.dp),
            )
        }
    }

    LaunchedEffect(focusRequester) {
        delay(100.milliseconds)
        focusRequester.requestFocus()
    }
}
