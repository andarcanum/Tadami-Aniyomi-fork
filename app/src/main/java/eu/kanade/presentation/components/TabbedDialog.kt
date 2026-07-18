package eu.kanade.presentation.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

object TabbedDialogPaddings {
    val Horizontal = 24.dp
    val Vertical = 8.dp
}

@Composable
fun TabbedDialog(
    onDismissRequest: () -> Unit,
    tabTitles: ImmutableList<String>,
    modifier: Modifier = Modifier,
    enableSwipeDismiss: Boolean = true,
    tabOverflowMenuContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    onOverflowMenuClicked: (() -> Unit)? = null,
    overflowIcon: ImageVector? = null,
    pagerState: PagerState = rememberPagerState { tabTitles.size },
    content: @Composable (Int) -> Unit,
) {
    val auroraColors = AuroraTheme.colors
    val supportsBlurBehind = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !auroraColors.isEInk
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    AdaptiveSheet(
        modifier = modifier,
        enableSwipeDismiss = enableSwipeDismiss,
        onDismissRequest = onDismissRequest,
        containerColor = when {
            auroraColors.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
            !supportsBlurBehind -> auroraColors.surface
            auroraColors.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        },
        scrimAlpha = if (supportsBlurBehind) 0f else 0.5f,
        onRevealChange = { sheetReveal = it },
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val revealState = rememberUpdatedState(sheetReveal)

        // One-shot window chrome setup - never add/clear BLUR flags per frame (flicker source).
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

        // Progressive radius/dim, quantized to cut attribute spam / edge ghosts.
        LaunchedEffect(window, supportsBlurBehind) {
            val w = window ?: return@LaunchedEffect
            if (!supportsBlurBehind) return@LaunchedEffect
            snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                .distinctUntilChanged()
                .collect { step -> applyTabbedSheetWindowFx(w, step / 20f) }
        }

        val scope = rememberCoroutineScope()

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuroraCapsuleTabs(
                    titles = tabTitles,
                    selectedIndex = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = 16.dp,
                            top = 12.dp,
                            bottom = 4.dp,
                            end = if (tabOverflowMenuContent != null || onOverflowMenuClicked != null) {
                                4.dp
                            } else {
                                16.dp
                            },
                        ),
                )

                MoreMenu(onOverflowMenuClicked, tabOverflowMenuContent, overflowIcon)
            }

            HorizontalPager(
                modifier = Modifier.animateContentSize(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
                pageContent = { page -> content(page) },
            )
        }
    }
}

@Composable
private fun MoreMenu(
    onClickIcon: (() -> Unit)?,
    content: @Composable (ColumnScope.(() -> Unit) -> Unit)?,
    overflowIcon: ImageVector? = null,
) {
    if (onClickIcon == null && content == null) return

    var expanded by remember { mutableStateOf(false) }
    val onClick = onClickIcon ?: { expanded = true }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = overflowIcon ?: Icons.Default.MoreVert,
                contentDescription = stringResource(MR.strings.label_more),
            )
        }
        if (onClickIcon == null) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                content!! { expanded = false }
            }
        }
    }
}

private fun applyTabbedSheetWindowFx(window: Window, reveal: Float) {
    // 0..~0.2 of travel = sheet still mostly off-screen, keep FX off.
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
    window.setDimAmount(0.22f * glass)
}
