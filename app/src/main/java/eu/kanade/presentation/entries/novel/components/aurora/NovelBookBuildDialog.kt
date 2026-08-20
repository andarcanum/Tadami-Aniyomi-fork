package eu.kanade.presentation.entries.novel.components.aurora

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.book.novel.NovelBookBuildProgress
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * Single dialog for the whole "make a book" flow.
 *
 * Confirming the missing downloads used to close the prompt and open a second, unrelated dialog for
 * the progress bar. Both are the same step for the reader, so they now share one window that simply
 * swaps its body once the build starts.
 *
 * Aurora glass chrome, same language as the other aurora dialogs (reader quick settings, image
 * actions, translation): the window is transparent, Android 12+ blurs whatever is behind it, and
 * the card itself is a translucent panel (70% black in dark / 88% white in light) with a thin rim.
 * Where blur is unavailable (older Android, E-Ink) the card falls back to an opaque surface and the
 * classic dim scrim, so the text never floats over a see-through background.
 */
@Composable
fun NovelBookBuildDialog(
    progress: NovelBookBuildProgress?,
    missingChapterCount: Int?,
    onDownloadMissing: () -> Unit,
    onBuildPartial: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (progress == null && missingChapterCount == null) return
    val colors = AuroraTheme.colors
    val building = progress != null
    val supportsBlurBehind = eu.kanade.presentation.util.rememberSupportsBlurBehind(colors.isEInk)
    val containerColor = when {
        colors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
        !supportsBlurBehind -> colors.surface
        colors.isDark -> Color.Black.copy(alpha = 0.70f)
        else -> Color.White.copy(alpha = 0.88f)
    }

    Dialog(
        // A running build must not be dismissed by a stray tap outside the dialog.
        onDismissRequest = { if (!building) onDismissRequest() },
        properties = DialogProperties(
            dismissOnBackPress = !building,
            dismissOnClickOutside = !building,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window

        // One-shot window chrome setup — never add/clear BLUR flags per frame (flicker source).
        DisposableEffect(window, supportsBlurBehind) {
            val w = window
            if (w != null) {
                w.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                w.setDimAmount(0f)
                if (supportsBlurBehind) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                }
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

        // A centered dialog has no drag reveal to track, so the settled aurora glass is applied
        // once instead of being animated from the sheet open fraction.
        LaunchedEffect(window, supportsBlurBehind) {
            val w = window ?: return@LaunchedEffect
            applyBookBuildDialogWindowFx(
                window = w,
                reveal = 1f,
                supportsBlurBehind = supportsBlurBehind,
            )
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            // Transparent window clips elevation shadows, so the card is rimmed instead.
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(fraction = 0.92f)
                .widthIn(max = 480.dp)
                .heightIn(max = 680.dp)
                .border(
                    width = 1.dp,
                    color = auroraRimColor(),
                    shape = RoundedCornerShape(28.dp),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(
                        if (building) {
                            AYMR.strings.novel_book_building
                        } else {
                            AYMR.strings.novel_book_missing_downloads_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (progress != null) {
                    val phaseText = when (progress.phase) {
                        NovelBookBuildProgress.Phase.DOWNLOADING ->
                            stringResource(AYMR.strings.novel_book_downloading_chapters)
                        NovelBookBuildProgress.Phase.MERGING,
                        NovelBookBuildProgress.Phase.PARSING,
                        -> stringResource(AYMR.strings.novel_book_building)
                    }
                    Text(
                        text = "$phaseText  ${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = colors.accent,
                        )
                    } else {
                        // The first seconds have no chapter count yet, so the bar stays indeterminate
                        // instead of sitting at zero and looking stuck.
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = colors.accent,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = stringResource(
                            AYMR.strings.novel_book_missing_downloads_body,
                            missingChapterCount ?: 0,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        when (resolveNovelBookBuildActionsLayout(maxWidth)) {
                            NovelBookBuildActionsLayout.INLINE -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = onBuildPartial) {
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_book_missing_downloads_partial,
                                            ),
                                            color = colors.textSecondary,
                                        )
                                    }
                                    TextButton(onClick = onDownloadMissing) {
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_book_missing_downloads_download,
                                            ),
                                            color = colors.accent,
                                        )
                                    }
                                }
                            }
                            NovelBookBuildActionsLayout.STACKED -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    TextButton(
                                        onClick = onBuildPartial,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_book_missing_downloads_partial,
                                            ),
                                            color = colors.textSecondary,
                                        )
                                    }
                                    TextButton(
                                        onClick = onDownloadMissing,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_book_missing_downloads_download,
                                            ),
                                            color = colors.accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun applyBookBuildDialogWindowFx(
    window: Window,
    reveal: Float,
    supportsBlurBehind: Boolean,
) {
    val glass = ((reveal - 0.18f) / 0.82f).coerceIn(0f, 1f)
    if (supportsBlurBehind) {
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
    } else {
        window.setDimAmount(0.26f * glass)
    }
}

internal enum class NovelBookBuildActionsLayout {
    INLINE,
    STACKED,
}

internal fun resolveNovelBookBuildActionsLayout(maxWidth: Dp): NovelBookBuildActionsLayout {
    return if (maxWidth < 420.dp) {
        NovelBookBuildActionsLayout.STACKED
    } else {
        NovelBookBuildActionsLayout.INLINE
    }
}
