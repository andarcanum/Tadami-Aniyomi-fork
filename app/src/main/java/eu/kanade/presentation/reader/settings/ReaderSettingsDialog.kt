package eu.kanade.presentation.reader.settings

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * Aurora glass sheet for manga reader quick settings.
 *
 * Window blur/dim track sheet reveal, but flags are set once (no add/clear thrash)
 * to avoid open flicker. Border shape matches the phone sheet (top corners only)
 * so the top rim does not leave a phantom edge during drag.
 */
@Composable
fun ReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    val tabTitles = listOf(
        stringResource(MR.strings.pref_category_reading_mode),
        stringResource(MR.strings.pref_category_general),
        stringResource(MR.strings.reader_settings_tab_color),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()

    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    // Start closed — initial 1f + closed offset used to flash full blur before open anim.
    var sheetReveal by remember { mutableFloatStateOf(0f) }

    val sheetContainer = remember(aurora.isDark, aurora.isEInk) {
        when {
            aurora.isEInk -> baseScheme.surfaceContainerHigh
            aurora.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        }
    }
    val auroraScheme = remember(baseScheme, aurora) {
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
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    val supportsBlurBehind = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !aurora.isEInk

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
            scrimAlpha = 0f,
            applyStatusBarsPadding = false,
            onRevealChange = { sheetReveal = it },
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val revealState = rememberUpdatedState(sheetReveal)

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

            // Progressive radius/dim only — quantized to cut attribute spam / edge ghosts.
            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal ->
                        // 20 steps is smooth enough; avoids every-pixel attribute rewrites.
                        (reveal * 20f).roundToInt().coerceIn(0, 20)
                    }
                    .distinctUntilChanged()
                    .collect { step ->
                        val reveal = step / 20f
                        applyReaderSheetWindowFx(
                            window = w,
                            reveal = reveal,
                            supportsBlurBehind = supportsBlurBehind,
                        )
                    }
            }

            Column {
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
                AuroraTabRow(
                    titles = tabTitles,
                    selectedIndex = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                )
                // No divider under tabs — glass cards already separate content; a rim line
                // reads as a flat "belt" between the capsule tabs and the first section.
                HorizontalPager(
                    modifier = Modifier.heightIn(max = pageMaxHeight),
                    state = pagerState,
                    verticalAlignment = Alignment.Top,
                    beyondViewportPageCount = 0,
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = pageMaxHeight)
                            .padding(vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when (page) {
                            0 -> ReadingModePage(screenModel)
                            1 -> GeneralPage(screenModel)
                            2 -> ColorFilterPage(screenModel)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Soft glass intensity — radius/dim only, flags already on.
 * Glass eases in after the sheet has mostly slid on-screen so open does not flash
 * a full-window blur before the panel is visible.
 */
private fun applyReaderSheetWindowFx(
    window: Window,
    reveal: Float,
    supportsBlurBehind: Boolean,
) {
    // 0..~0.2 of travel = sheet still mostly off-screen → keep FX off.
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
