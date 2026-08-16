package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * Vertical seekbar with the Gemini/Google quick actions, extracted from [NovelReaderContentHost].
 *
 * Takes narrow data plus callbacks instead of the whole reader state.
 */
@Composable
internal fun NovelReaderVerticalSeekbar(
    showReaderUi: Boolean,
    settings: NovelReaderSettings,
    showWebView: Boolean,
    usePageReader: Boolean,
    webProgressPercent: Int,
    pagerCurrentPage: Int,
    pageTurnCurrentPage: Int,
    firstVisibleItemIndex: Int,
    canScrollForward: Boolean,
    seekbarItemsCount: Int,
    readingProgressPercent: Int,
    isBookMode: Boolean,
    pageReaderRendererRoute: NovelPageReaderRendererRoute,
    pageReaderItemsCount: Int,
    // pagerCurrentPage above is a spread-slot index when a two-page spread is active, so resolving
    // it back to a real page (for the seekbar's own position math) needs the slot count, not the
    // real page count pageReaderItemsCount holds.
    pageReaderSpreadSlotCount: Int = pageReaderItemsCount,
    spreadColumns: Int = 1,
    composePagerHasPreviousChapter: Boolean,
    nativeScrollItemsCount: Int,
    pageReaderProgressPageIndex: Int,
    isGeminiTranslating: Boolean,
    hasGeminiTranslationCache: Boolean,
    isGeminiTranslationVisible: Boolean,
    geminiTranslationProgress: Int,
    backgroundTranslatingChapterCount: Int,
    isGoogleTranslating: Boolean,
    hasGoogleTranslationCache: Boolean,
    isGoogleTranslationVisible: Boolean,
    googleTranslationProgress: Int,
    onSetShowReaderUi: (Boolean) -> Unit,
    onSeekBookModeProgress: (Float) -> Unit,
    onSeekWebProgress: (Float, Boolean) -> Unit,
    onSeekPage: (Int) -> Unit,
    onScrollToNativeIndex: (Int) -> Unit,
    onScrollToPagerPage: (Int) -> Unit,
    onStopGeminiTranslation: () -> Unit,
    onToggleGeminiTranslationVisibility: () -> Unit,
    onStartGeminiTranslation: () -> Unit,
    onStopGoogleTranslation: () -> Unit,
    onToggleGoogleTranslationVisibility: () -> Unit,
    onStartGoogleTranslation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showPageReaderDismissLayer = shouldShowPageReaderDismissLayer(
        showReaderUi = showReaderUi,
        usePageReader = usePageReader,
    )
    if (showPageReaderDismissLayer) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showPageReaderDismissLayer) {
                    detectTapGestures(
                        onTap = {
                            onSetShowReaderUi(false)
                        },
                    )
                },
        )
    }
    if (
        shouldShowVerticalSeekbar(
            showReaderUi = showReaderUi,
            verticalSeekbarEnabled = settings.verticalSeekbar,
            showWebView = showWebView,
            usePageReader = usePageReader,
            textBlocksCount = seekbarItemsCount,
        )
    ) {
        val seekbarValue by remember(
            showWebView,
            webProgressPercent,
            usePageReader,
            pagerCurrentPage,
            pageTurnCurrentPage,
            firstVisibleItemIndex,
            canScrollForward,
            seekbarItemsCount,
            readingProgressPercent,
            isBookMode,
            spreadColumns,
        ) {
            derivedStateOf {
                resolveReaderVerticalSeekbarValue(
                    showWebView = showWebView,
                    webProgressPercent = webProgressPercent,
                    usePageReader = usePageReader,
                    pageReaderRendererRoute = pageReaderRendererRoute,
                    pagerCurrentPage = pagerCurrentPage,
                    pageTurnCurrentPage = pageTurnCurrentPage,
                    composePagerContentPageCount = pageReaderSpreadSlotCount,
                    composePagerHasPreviousChapter = composePagerHasPreviousChapter,
                    pageTurnContentPageCount = pageReaderItemsCount,
                    pageTurnHasPreviousChapter = composePagerHasPreviousChapter,
                    seekbarItemsCount = seekbarItemsCount,
                    readingProgressPercent = readingProgressPercent,
                    nativeFirstVisibleItemIndex = firstVisibleItemIndex,
                    nativeCanScrollForward = canScrollForward,
                    bookModeEnabled = isBookMode,
                    spreadColumns = spreadColumns,
                )
            }
        }
        // The chrome asks once what mode it is drawing for, instead of branching on it again
        // for the labels and again for the ticks.
        val chromeState = resolveReaderChromeState(
            bookModeEnabled = isBookMode,
            readingProgressPercent = readingProgressPercent,
            usePageReader = usePageReader,
            pageIndex = pageReaderProgressPageIndex,
            pageCount = pageReaderItemsCount,
            showScrollPercentage = settings.showScrollPercentage,
        )
        val pageRailTopLabel = chromeState.railTopLabel
        val pageRailBottomLabel = chromeState.railBottomLabel
        val pageSeekbarTickFractions = chromeState.tickFractions
        var seekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val coroutineScope = rememberCoroutineScope()
        fun seekToVerticalProgress(value: Float, isFinal: Boolean) {
            val clampedValue = value.coerceIn(0f, 1f)
            if (isBookMode) {
                onSeekBookModeProgress(clampedValue)
            } else if (showWebView) {
                onSeekWebProgress(clampedValue, isFinal)
            } else {
                val maxIndex = (seekbarItemsCount - 1).coerceAtLeast(0)
                val target = (clampedValue * maxIndex.toFloat())
                    .roundToInt()
                    .coerceIn(0, maxIndex)
                if (usePageReader) {
                    onSeekPage(target)
                } else {
                    seekJob?.cancel()
                    seekJob = coroutineScope.launch {
                        onScrollToNativeIndex(target)
                    }
                }
            }
        }
        Column(
            modifier = modifier
                .padding(end = MaterialTheme.padding.small)
                .size(
                    width = if (usePageReader && !isBookMode) 40.dp else 30.dp,
                    height = 270.dp,
                ),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            if (settings.geminiEnabled) {
                val hasTranslationResult =
                    hasGeminiTranslationCache || geminiTranslationProgress == 100
                val quickActionIcon = when {
                    isGeminiTranslating -> Icons.Filled.Pause
                    hasTranslationResult && isGeminiTranslationVisible -> Icons.Filled.Public
                    else -> Icons.Filled.PlayArrow
                }
                val quickActionDescription = when {
                    isGeminiTranslating -> stringResource(MR.strings.reader_action_stop_translation)
                    hasTranslationResult && isGeminiTranslationVisible -> stringResource(
                        MR.strings.reader_action_show_original,
                    )
                    hasTranslationResult -> stringResource(MR.strings.reader_action_show_translation)
                    else -> stringResource(MR.strings.reader_action_start_translation)
                }
                val quickActionContainerColor = when {
                    isGeminiTranslating -> MaterialTheme.colorScheme.errorContainer
                    hasTranslationResult && isGeminiTranslationVisible ->
                        MaterialTheme.colorScheme.tertiaryContainer
                    hasTranslationResult -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val quickActionContentColor = when {
                    isGeminiTranslating -> MaterialTheme.colorScheme.onErrorContainer
                    hasTranslationResult && isGeminiTranslationVisible ->
                        MaterialTheme.colorScheme.onTertiaryContainer
                    hasTranslationResult ->
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }

                Column(
                    modifier = Modifier.padding(bottom = 6.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = quickActionContainerColor,
                        contentColor = quickActionContentColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        ),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                when {
                                    isGeminiTranslating -> onStopGeminiTranslation()
                                    hasTranslationResult -> onToggleGeminiTranslationVisibility()
                                    else -> onStartGeminiTranslation()
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = quickActionIcon,
                                contentDescription = quickActionDescription,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (isGeminiTranslating) {
                        LinearProgressIndicator(
                            progress = { geminiTranslationProgress.coerceIn(0, 100) / 100f },
                            modifier = Modifier
                                .size(width = 24.dp, height = 3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    // Over a book the bar above describes the chapter under the reading
                    // position; the queue keeps working on the other chapters of the book,
                    // which would otherwise be invisible here.
                    if (isBookMode && backgroundTranslatingChapterCount > 0) {
                        Text(
                            text = "+$backgroundTranslatingChapterCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (settings.googleTranslationEnabled) {
                val hasGoogleResult = hasGoogleTranslationCache || googleTranslationProgress == 100
                val googleQuickActionIcon = when {
                    isGoogleTranslating -> Icons.Filled.Pause
                    hasGoogleResult && isGoogleTranslationVisible -> Icons.Filled.Public
                    else -> Icons.Filled.PlayArrow
                }
                val googleQuickActionDescription = when {
                    isGoogleTranslating -> stringResource(AYMR.strings.novel_reader_google_translate_stop)
                    hasGoogleResult && isGoogleTranslationVisible -> stringResource(
                        AYMR.strings.novel_reader_google_translate_original,
                    )
                    hasGoogleResult -> stringResource(AYMR.strings.novel_reader_google_translate_translated)
                    else -> stringResource(AYMR.strings.novel_reader_google_translate_start)
                }
                val googleQuickActionContainerColor = when {
                    isGoogleTranslating -> MaterialTheme.colorScheme.errorContainer
                    hasGoogleResult && isGoogleTranslationVisible ->
                        MaterialTheme.colorScheme.tertiaryContainer
                    hasGoogleResult -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val googleQuickActionContentColor = when {
                    isGoogleTranslating -> MaterialTheme.colorScheme.onErrorContainer
                    hasGoogleResult && isGoogleTranslationVisible ->
                        MaterialTheme.colorScheme.onTertiaryContainer
                    hasGoogleResult ->
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }

                Column(
                    modifier = Modifier.padding(bottom = 6.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = googleQuickActionContainerColor,
                        contentColor = googleQuickActionContentColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        ),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                when {
                                    isGoogleTranslating -> onStopGoogleTranslation()
                                    hasGoogleResult -> onToggleGoogleTranslationVisibility()
                                    else -> onStartGoogleTranslation()
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = googleQuickActionIcon,
                                contentDescription = googleQuickActionDescription,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (isGoogleTranslating) {
                        LinearProgressIndicator(
                            progress = { googleTranslationProgress.coerceIn(0, 100) / 100f },
                            modifier = Modifier
                                .size(width = 24.dp, height = 3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
            LnReaderVerticalSeekbar(
                progress = seekbarValue,
                topLabel = pageRailTopLabel,
                bottomLabel = pageRailBottomLabel,
                tickFractions = pageSeekbarTickFractions,
                onProgressChange = { value -> seekToVerticalProgress(value, isFinal = false) },
                onProgressChangeFinished = { value -> seekToVerticalProgress(value, isFinal = true) },
                modifier = Modifier
                    .fillMaxSize(),
            )
        }
    }
}
