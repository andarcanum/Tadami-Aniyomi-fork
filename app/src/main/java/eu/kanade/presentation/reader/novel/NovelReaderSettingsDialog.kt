@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.presentation.reader.novel

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.reader.settings.AuroraTabRow
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

/**
 * Aurora glass sheet for novel reader quick settings — same chrome language as manga
 * [eu.kanade.presentation.reader.settings.ReaderSettingsDialog]: AdaptiveSheet, progressive
 * window blur, Aurora tabs, three pages (mode / reading / behavior).
 */
@Composable
fun NovelReaderSettingsDialog(
    sourceId: Long,
    currentWebViewActive: Boolean,
    currentPageReaderActive: Boolean,
    onDismissRequest: () -> Unit,
) {
    val preferences = remember { Injekt.get<NovelReaderPreferences>() }
    val sourceOverrides = remember { preferences.sourceOverrides() }
    val overrides by sourceOverrides.changes().collectAsStateWithLifecycle(initialValue = sourceOverrides.get())
    val overrideEnabled = overrides[sourceId] != null
    val settingsFlow = remember(sourceId) { preferences.settingsFlow(sourceId) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = preferences.resolveSettings(sourceId))

    val tabTitles = listOf(
        stringResource(AYMR.strings.novel_reader_tab_general),
        stringResource(AYMR.strings.novel_reader_tab_reading),
        stringResource(AYMR.strings.novel_reader_tab_behavior),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()

    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    var sheetReveal by remember { mutableFloatStateOf(0f) }

    val sheetContainer = remember(aurora.isDark, aurora.isEInk) {
        when {
            aurora.isEInk -> baseScheme.surfaceContainerHigh
            aurora.isDark -> Color.Black.copy(alpha = 0.58f)
            else -> Color.White.copy(alpha = 0.82f)
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
                        w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                        w.setDimAmount(0f)
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }

            LaunchedEffect(window, supportsBlurBehind) {
                val w = window ?: return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                    .distinctUntilChanged()
                    .collect { step ->
                        applyNovelSheetWindowFx(
                            window = w,
                            reveal = step / 20f,
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
                HorizontalDivider(color = auroraRimColor())
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
                            0 -> GeneralTab(
                                settings = settings,
                                sourceId = sourceId,
                                currentWebViewActive = currentWebViewActive,
                                currentPageReaderActive = currentPageReaderActive,
                                overrideEnabled = overrideEnabled,
                                preferences = preferences,
                                onDismissRequest = onDismissRequest,
                            )
                            1 -> ReadingTab(
                                settings = settings,
                                sourceId = sourceId,
                                overrideEnabled = overrideEnabled,
                                preferences = preferences,
                            )
                            2 -> BehaviorTab(
                                settings = settings,
                                sourceId = sourceId,
                                currentPageReaderActive = currentPageReaderActive,
                                overrideEnabled = overrideEnabled,
                                preferences = preferences,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun applyNovelSheetWindowFx(
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

@Composable
fun NovelReaderTtsBehaviorSettingsDialog(
    sourceId: Long,
    onDismissRequest: () -> Unit,
) {
    val preferences = remember { Injekt.get<NovelReaderPreferences>() }
    val sourceOverrides = remember { preferences.sourceOverrides() }
    val overrides by sourceOverrides.changes().collectAsStateWithLifecycle(initialValue = sourceOverrides.get())
    val overrideEnabled = overrides[sourceId] != null
    val settingsFlow = remember(sourceId) { preferences.settingsFlow(sourceId) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = preferences.resolveSettings(sourceId))

    fun <T> update(
        value: T,
        copyOverride: (NovelReaderOverride, T) -> NovelReaderOverride,
        setGlobal: (T) -> Unit,
    ) {
        if (overrideEnabled) {
            preferences.updateSourceOverride(sourceId) { copyOverride(it, value) }
        } else {
            setGlobal(value)
        }
    }

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(stringResource(AYMR.strings.novel_reader_tts_behavior_settings)),
        enableSwipeDismiss = false,
        modifier = Modifier.fillMaxHeight(0.7f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_tts_speech_rate),
                valueText = { formatTtsPercentage(it / 100f) },
                committedValue = settings.ttsSpeechRate * 100f,
                range = 50f..200f,
                steps = 149,
                enabled = true,
                onCommit = {
                    update(
                        it / 100f,
                        { o, v -> o.copy(ttsSpeechRate = v) },
                        { preferences.ttsSpeechRate().set(it) },
                    )
                },
            )
            LnReaderSliderRow(
                label = stringResource(AYMR.strings.novel_reader_tts_pitch),
                valueText = { formatTtsPercentage(it / 100f) },
                committedValue = settings.ttsPitch * 100f,
                range = 50f..200f,
                steps = 149,
                enabled = true,
                onCommit = {
                    update(
                        it / 100f,
                        { o, v -> o.copy(ttsPitch = v) },
                        { preferences.ttsPitch().set(it) },
                    )
                },
            )
            ListPreferenceWidget(
                value = settings.ttsHighlightMode,
                title = stringResource(AYMR.strings.novel_reader_tts_highlight_mode),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_highlight_mode_summary),
                icon = null,
                entries = NovelTtsHighlightMode.entries.associateWith { getTtsHighlightModeLabel(it) },
                onValueChange = {
                    update(
                        it,
                        { o, v -> o.copy(ttsHighlightMode = v) },
                        { preferences.ttsHighlightMode().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_tts_word_highlight_enabled),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_word_highlight_enabled_summary),
                checked = settings.ttsWordHighlightEnabled,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(ttsWordHighlightEnabled = v) },
                        { preferences.ttsWordHighlightEnabled().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_tts_auto_advance_chapter),
                checked = settings.ttsAutoAdvanceChapter,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(ttsAutoAdvanceChapter = v) },
                        { preferences.ttsAutoAdvanceChapter().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_tts_follow_along),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_follow_along_summary),
                checked = settings.ttsFollowAlong,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(ttsFollowAlong = v) },
                        { preferences.ttsFollowAlong().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_tts_pause_on_manual_navigation),
                checked = settings.ttsPauseOnManualNavigation,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(ttsPauseOnManualNavigation = v) },
                        { preferences.ttsPauseOnManualNavigation().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_tts_keep_screen_on_during_playback),
                checked = settings.ttsKeepScreenOnDuringPlayback,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(ttsKeepScreenOnDuringPlayback = v) },
                        { preferences.ttsKeepScreenOnDuringPlayback().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_tts_prefer_translated_text),
                subtitle = stringResource(AYMR.strings.novel_reader_tts_prefer_translated_text_summary),
                checked = settings.ttsPreferTranslatedText,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(ttsPreferTranslatedText = v) },
                        { preferences.ttsPreferTranslatedText().set(it) },
                    )
                },
            )
            SwitchPreferenceWidget(
                title = stringResource(AYMR.strings.novel_reader_tts_read_chapter_title),
                checked = settings.ttsReadChapterTitle,
                onCheckedChanged = {
                    update(
                        it,
                        { o, v -> o.copy(ttsReadChapterTitle = v) },
                        { preferences.ttsReadChapterTitle().set(it) },
                    )
                },
            )
        }
    }
}

@Composable
private fun getTtsHighlightModeLabel(mode: NovelTtsHighlightMode): String {
    return when (mode) {
        NovelTtsHighlightMode.AUTO -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_auto)
        NovelTtsHighlightMode.EXACT -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_exact)
        NovelTtsHighlightMode.ESTIMATED -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_estimated)
        NovelTtsHighlightMode.OFF -> stringResource(AYMR.strings.novel_reader_tts_highlight_mode_off)
    }
}

private fun formatTtsPercentage(value: Float): String {
    return "${(value * 100).roundToInt()}%"
}
