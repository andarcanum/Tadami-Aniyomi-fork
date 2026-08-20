package eu.kanade.presentation.reader.components

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.rememberSupportsBlurBehind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * Translucent Aurora glass sheet shared by the manga reader sheets (quick settings, reading mode,
 * orientation, chapter list).
 *
 * Window blur/dim track the sheet reveal, but the flags are set once (no add/clear thrash) to avoid
 * open flicker. The border shape matches the phone sheet (top corners only) so the top rim does not
 * leave a phantom edge while dragging.
 */
@Composable
internal fun AuroraReaderSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    // Start closed — initial 1f + closed offset used to flash full blur before open anim.
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    val supportsBlurBehind = rememberSupportsBlurBehind(aurora.isEInk)

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
    // Must match PhoneAdaptiveSheet Surface shape (top rounded, bottom flush).
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
    )

    MaterialTheme(
        colorScheme = auroraScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        AdaptiveSheet(
            onDismissRequest = onDismissRequest,
            // Border shape == surface shape → no floating top rim ghost while offsetting.
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

            // One-shot window chrome setup — never add/clear BLUR flags per frame (flicker source).
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
                        // Reset so the next dialog does not inherit a residual blur edge.
                        w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                        w.setDimAmount(0f)
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }

            // Progressive radius/dim only — quantized to cut attribute spam / edge ghosts.
            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                if (!supportsBlurBehind) return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal ->
                        // 20 steps is smooth enough; avoids every-pixel attribute rewrites.
                        (reveal * 20f).roundToInt().coerceIn(0, 20)
                    }
                    .distinctUntilChanged()
                    .collect { step ->
                        applyReaderSheetWindowFx(
                            window = w,
                            reveal = step / 20f,
                        )
                    }
            }

            Column {
                AuroraSheetDragHandle()
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun AuroraSheetDragHandle() {
    val aurora = AuroraTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (aurora.isDark) {
                        Color.White.copy(alpha = 0.22f)
                    } else {
                        Color.Black.copy(alpha = 0.18f)
                    },
                ),
        )
    }
}

/**
 * Soft glass intensity — radius/dim only, flags already on.
 * Glass eases in after the sheet has mostly slid on-screen so open does not flash a full-window
 * blur before the panel is visible.
 */
private fun applyReaderSheetWindowFx(
    window: Window,
    reveal: Float,
) {
    // 0..~0.2 of travel = sheet still mostly off-screen → keep FX off.
    val glass = ((reveal - 0.18f) / 0.82f).coerceIn(0f, 1f)
    val radius = if (glass <= 0.02f) {
        0
    } else {
        (44f * glass).roundToInt().coerceIn(1, 48)
    }
    val attrs = window.attributes
    if (attrs.blurBehindRadius != radius) {
        window.attributes = attrs.apply { blurBehindRadius = radius }
    }
    window.setDimAmount(0.18f * glass)
}
