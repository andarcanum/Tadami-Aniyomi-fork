package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import android.os.SystemClock
import android.webkit.WebResourceResponse
import android.widget.Toast
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSection
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSpine
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookWindowState
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsNavigationAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Stable
internal class NovelBookContentHandle {
    var surface by mutableStateOf<NovelBookScrollSurface?>(null)
    var nativeRenderer by mutableStateOf(false)
    var nativeEntries by mutableStateOf<List<NovelBookNativeEntry>>(emptyList())
    var ttsNavigationAdapter by mutableStateOf<NovelTtsNavigationAdapter?>(null)
    var ttsBlockAnchor by mutableStateOf<NovelBlockAnchor?>(null)
    var isReady by mutableStateOf(false)
    var hasRenderableItems by mutableStateOf(false)

    /** Block positions of the mounted native list; written by the rows, read by TTS scroll. */
    val ttsBlockPositions = NovelBookTtsBlockPositions()
}

@Composable
internal fun NovelBookContentHost(
    state: NovelReaderScreenModel.State.Success,
    spine: NovelBookSpine,
    initialLocation: NovelBookLocation,
    seekRequest: BookSeekRequest?,
    window: NovelBookWindowState,
    textListState: androidx.compose.foundation.lazy.LazyListState,
    handle: NovelBookContentHandle,
    loadDocument: (suspend (NovelBookSection) -> NovelBookDocument)?,
    loadSectionHtml: suspend (Int) -> String?,
    nativeBookBlocksForSection: (Int) -> List<eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock>?,
    onSeekApplied: (Long) -> Unit,
    onLocationChanged: (NovelBookLocation) -> Unit,
    onScroll: (Int, Float) -> Unit,
    onToggleReaderUi: () -> Unit,
    onShortTap: (Float, Float, Float, Float) -> Unit,
    onSurfaceChanged: (NovelBookScrollSurface?) -> Unit,
    readerCss: String,
    resolveResource: (String) -> WebResourceResponse?,
    textColor: Color,
    textBackground: Color,
    activeBackgroundTexture: NovelReaderBackgroundTexture,
    activeOledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    isBackgroundMode: Boolean,
    nativeTextureStrengthPercent: Int,
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    backgroundImageModel: Any?,
    activePageTransitionStyle: NovelPageTransitionStyle,
    // Native list appearance (used only when the native renderer is mounted).
    statusBarTopPadding: Dp,
    paragraphSpacing: Dp,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    ttsHighlightState: NovelReaderTtsHighlightState?,
    ttsHighlightColor: Color,
    selectionSessionIdProvider: () -> Long,
    onSelectedTextSelectionChanged: (eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection?) -> Unit,
    onPlainTap: (Float, Float, Float, Float) -> Unit,
    onRetrySection: (Int) -> Unit,
    contentPaddingTop: Dp,
    contentPaddingBottom: Dp,
    /**
     * Renderer chosen by the parent content host. The decision is made exactly once, in
     * [NovelReaderContentHost]; passing it down keeps the two hosts from ever disagreeing about
     * which renderer is mounted.
     */
    bookRendererDecision: NovelBookRendererDecision,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // The WebView book engine must not leave the composition in the same frame the renderer
    // decision flips: removing the focused WebView re-focuses the window root, and that cascade
    // re-enters Compose layout inside applyChanges ("Cannot start a writer when another writer is
    // pending"). The unmount lags one focus-safe frame; the mount direction stays immediate.
    val requestedNativeBookScroll = bookRendererDecision.renderer.usesWebView.not()
    val useNativeBookScroll = rememberFocusSafeInteropUnmount(
        unmountRequested = requestedNativeBookScroll,
        prepareUnmount = { handle.surface?.prepareForFocusSafeUnmount() },
        cancelPrepare = { handle.surface?.cancelFocusSafeUnmount() },
    )
    val nativeSections = remember(state.novel.id) {
        mutableStateOf<NovelBookNativeSections>(emptyList())
    }
    val nativeEntries = remember(nativeSections.value, state.bookMode.failedSectionIndices) {
        buildNovelBookNativeEntries(
            sections = nativeSections.value,
            failedSectionIndices = state.bookMode.failedSectionIndices,
        )
    }
    // Publish the renderer identity and resident entries on the same frame they are computed.
    // Publishing through SideEffect delayed them by one frame, which left the native book empty
    // on its first composition: the reader's LazyColumn already decided which branch to draw
    // before the handle was updated.
    handle.nativeRenderer = useNativeBookScroll
    handle.nativeEntries = nativeEntries
    handle.isReady = state.bookMode.isReady
    handle.hasRenderableItems = state.bookMode.sectionCount > 0 &&
        (handle.surface != null || nativeEntries.isNotEmpty())
    val ttsBlockPositions = handle.ttsBlockPositions
    val appliedSeekId = remember(state.novel.id) { longArrayOf(0L) }
    val latestEntries by androidx.compose.runtime.rememberUpdatedState(nativeEntries)
    val latestUseNativeRenderer by androidx.compose.runtime.rememberUpdatedState(useNativeBookScroll)
    val ttsNavigationAdapter = remember(state.novel.id, spine) {
        BookTtsNavigationAdapter(
            surface = { handle.surface },
            sectionIndexForChapter = { chapterId ->
                spine.indexOf(chapterId).takeIf { it >= 0 }
            },
            scrollNativeToSection = { sectionIndex ->
                val itemIndex = latestEntries.indexOfFirst { it.sectionIndex == sectionIndex }
                if (itemIndex < 0) {
                    false
                } else {
                    runCatching { textListState.animateScrollToItem(itemIndex) }.isSuccess
                }
            },
            onNativeAnchor = { anchor -> handle.ttsBlockAnchor = anchor },
            isNativeRenderer = { latestUseNativeRenderer },
            isNativeBlockLaidOut = { anchor -> ttsBlockPositions.isLaidOut(anchor) },
        )
    }

    SideEffect {
        handle.ttsNavigationAdapter = ttsNavigationAdapter
    }
    LaunchedEffect(state.bookMode.isEnabled, bookRendererDecision) {
        if (!state.bookMode.isEnabled || !bookRendererDecision.isSilentFallback) return@LaunchedEffect
        val reasonMessage = when (bookRendererDecision.reason) {
            NovelBookRendererReason.BIONIC_READING -> AYMR.strings.novel_book_renderer_fallback_bionic
            NovelBookRendererReason.CUSTOM_STYLES -> AYMR.strings.novel_book_renderer_fallback_custom_styles
            else -> AYMR.strings.novel_book_renderer_fallback_unsupported_content
        }
        Toast.makeText(context, context.getString(reasonMessage.resourceId), Toast.LENGTH_SHORT).show()
    }
    DisposableEffect(textListState, useNativeBookScroll) {
        if (!useNativeBookScroll) return@DisposableEffect onDispose { }
        val previous = handle.surface
        val surface = LazyListNovelBookScrollSurface(textListState)
        handle.surface = surface
        onSurfaceChanged(surface)
        onDispose {
            if (handle.surface === surface) {
                handle.surface = previous
                onSurfaceChanged(previous)
            }
        }
    }
    LaunchedEffect(useNativeBookScroll, window) {
        if (!useNativeBookScroll) return@LaunchedEffect
        val currentSections = nativeSections.value
        val pruned = pruneNovelBookNativeSections(currentSections, window)
        if (pruned !== currentSections) nativeSections.value = pruned
        var sections = pruned
        missingNovelBookNativeSections(sections, window).forEach { sectionIndex ->
            val blocks = nativeBookBlocksForSection(sectionIndex)
                ?: withContext(Dispatchers.Default) {
                    loadSectionHtml(sectionIndex)?.let(::parseNovelBookNativeSection)
                }
                ?: return@forEach
            sections = withNovelBookNativeSection(
                sections = sections,
                section = NovelBookNativeSection(
                    sectionIndex = sectionIndex,
                    blocks = blocks,
                    revision = window.revisionOf(sectionIndex),
                ),
            )
            nativeSections.value = sections
        }
    }
    LaunchedEffect(useNativeBookScroll, seekRequest, nativeEntries) {
        if (!useNativeBookScroll) return@LaunchedEffect
        val seekTarget = resolveNovelBookNativeSeekTarget(
            entries = nativeEntries,
            request = seekRequest,
            lastAppliedSeekId = appliedSeekId[0],
            sectionFraction = state.bookMode.currentSectionFraction,
        ) ?: return@LaunchedEffect
        appliedSeekId[0] = seekTarget.seekRequestId
        textListState.scrollToItem(seekTarget.itemIndex)
        val itemHeight = textListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == seekTarget.itemIndex }
            ?.size
            ?: 0
        val offsetPx = (itemHeight * seekTarget.fraction).toInt()
        if (offsetPx > 0) textListState.scrollToItem(seekTarget.itemIndex, offsetPx)
        onSeekApplied(seekTarget.seekRequestId)
    }
    LaunchedEffect(useNativeBookScroll, nativeEntries) {
        if (!useNativeBookScroll) return@LaunchedEffect
        // The list used to report its position on every scroll frame, which re-created the book
        // window and the whole Success state per frame. Reporting on section changes and at the
        // cadence below keeps the position fresh enough for progress persistence without that
        // churn; the section change still reports immediately because the window depends on it.
        var lastReportMillis = 0L
        var lastSectionIndex = -1
        var lastSectionFraction = -1f
        snapshotFlow {
            textListState.firstVisibleItemIndex to textListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect {
                val viewportItems = textListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    val entry = nativeEntries.getOrNull(item.index) as? NovelBookNativeEntry.Block
                        ?: return@mapNotNull null
                    NovelBookNativeViewportItem(
                        sectionIndex = entry.sectionIndex,
                        charOffsetBefore = entry.charOffsetBefore,
                        blockCharCount = entry.blockCharCount,
                        sectionCharCount = entry.sectionCharCount,
                        offsetPx = item.offset,
                        heightPx = item.size,
                    )
                }
                val location = resolveNovelBookNativeRelocate(viewportItems) ?: return@collect
                val now = SystemClock.uptimeMillis()
                val sectionChanged = location.sectionIndex != lastSectionIndex
                if (!sectionChanged && location.sectionFraction == lastSectionFraction) return@collect
                if (!sectionChanged && now - lastReportMillis < NATIVE_SCROLL_REPORT_INTERVAL_MILLIS) {
                    return@collect
                }
                lastReportMillis = now
                lastSectionIndex = location.sectionIndex
                lastSectionFraction = location.sectionFraction
                onScroll(location.sectionIndex, location.sectionFraction)
            }
    }
    LaunchedEffect(
        handle.surface,
        nativeEntries,
        spine,
        useNativeBookScroll,
        state.readerSettings.ttsFollowAlong,
    ) {
        if (!state.readerSettings.ttsFollowAlong) return@LaunchedEffect
        ttsNavigationAdapter.retryPendingAnchor()
    }
    LaunchedEffect(
        handle.ttsBlockAnchor,
        nativeEntries,
        useNativeBookScroll,
        state.readerSettings.ttsFollowAlong,
    ) {
        if (!useNativeBookScroll || !state.readerSettings.ttsFollowAlong) return@LaunchedEffect
        val anchor = handle.ttsBlockAnchor ?: return@LaunchedEffect
        repeat(NOVEL_BOOK_TTS_SCROLL_ATTEMPTS) {
            val delta = ttsBlockPositions.scrollDeltaFor(anchor)
            if (delta != null) {
                if (kotlin.math.abs(delta) > NOVEL_BOOK_TTS_SCROLL_EPSILON_PX) {
                    try {
                        textListState.animateScrollBy(delta)
                    } catch (_: Exception) {
                    }
                }
                return@LaunchedEffect
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }
    DisposableEffect(handle) {
        onDispose {
            if (handle.ttsNavigationAdapter === ttsNavigationAdapter) {
                handle.ttsNavigationAdapter = null
                handle.ttsBlockAnchor = null
                handle.nativeEntries = emptyList()
                handle.nativeRenderer = false
                handle.isReady = false
                handle.hasRenderableItems = false
            }
        }
    }

    if (useNativeBookScroll) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = textListState,
            contentPadding = PaddingValues(
                top = contentPaddingTop,
                bottom = contentPaddingBottom,
                start = state.readerSettings.margin.dp,
                end = state.readerSettings.margin.dp,
            ),
        ) {
            novelBookNativeContent(
                entries = nativeEntries,
                handle = handle,
                state = state,
                textColor = textColor,
                textBackground = textBackground,
                statusBarTopPadding = statusBarTopPadding,
                paragraphSpacing = paragraphSpacing,
                textTypeface = textTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                readerSettings = state.readerSettings,
                ttsHighlightState = ttsHighlightState,
                ttsHighlightColor = ttsHighlightColor,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onPlainTap = onPlainTap,
                onRetrySection = onRetrySection,
            )
        }
    } else if (loadDocument != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            NovelBookReader(
                spine = spine,
                initialLocation = initialLocation,
                seekRequest = seekRequest,
                onSeekApplied = onSeekApplied,
                sectionRevisions = window.sectionRevisions,
                loadSectionHtml = loadSectionHtml,
                flow = if (state.readerSettings.pageReader) {
                    NovelBookEngineFlow.PAGINATED
                } else {
                    NovelBookEngineFlow.SCROLLED
                },
                transitionStyleName = activePageTransitionStyle.name,
                loadDocument = loadDocument,
                onLocationChanged = onLocationChanged,
                onToggleReaderUi = onToggleReaderUi,
                onShortTap = onShortTap,
                onSurfaceChanged = { surface ->
                    handle.surface = surface
                    onSurfaceChanged(surface)
                },
                readerCss = readerCss,
                resolveResource = resolveResource,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    var localRestoringPosition by remember(state.bookMode.isRestoringPosition) {
        mutableStateOf(state.bookMode.isRestoringPosition)
    }
    LaunchedEffect(state.bookMode.isRestoringPosition) {
        if (state.bookMode.isRestoringPosition) {
            // The cover is dropped by the controller when the resume seek is acknowledged
            // (onBookSeekApplied clears isRestoringPosition). This timeout is only a safety net so
            // a stuck renderer cannot leave the book covered forever; it must never decide when the
            // resume is "probably" done, which is what made the first page flash before the jump.
            delay(BOOK_RESTORING_COVER_SAFETY_TIMEOUT_MS)
            localRestoringPosition = false
        }
    }
    if (localRestoringPosition) {
        NovelBookRestoringPositionCover(
            textColor = textColor,
            textBackground = textBackground,
            activeBackgroundTexture = activeBackgroundTexture,
            nativeTextureStrengthPercent = if (isBackgroundMode) 0 else nativeTextureStrengthPercent,
            oledEdgeGradient = activeOledEdgeGradient,
            isDarkTheme = isDarkTheme,
            pageEdgeShadow = pageEdgeShadow,
            pageEdgeShadowAlpha = pageEdgeShadowAlpha,
            backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
        )
    }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.novelBookNativeContent(
    entries: List<NovelBookNativeEntry>,
    handle: NovelBookContentHandle,
    state: NovelReaderScreenModel.State.Success,
    textColor: Color,
    textBackground: Color,
    statusBarTopPadding: androidx.compose.ui.unit.Dp,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    textTypeface: android.graphics.Typeface?,
    chapterTitleTypeface: android.graphics.Typeface?,
    readerSettings: NovelReaderSettings,
    ttsHighlightState: NovelReaderTtsHighlightState?,
    ttsHighlightColor: Color,
    selectionSessionIdProvider: () -> Long,
    onSelectedTextSelectionChanged: (eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection?) -> Unit,
    onPlainTap: (Float, Float, Float, Float) -> Unit,
    onRetrySection: (Int) -> Unit,
) {
    itemsIndexed(
        entries,
        key = { _, entry ->
            when (entry) {
                is NovelBookNativeEntry.Block -> "book-${entry.sectionIndex}-${entry.blockIndex}"
                is NovelBookNativeEntry.Failed -> "book-${entry.sectionIndex}-failed"
            }
        },
    ) { _, entry ->
        when (entry) {
            is NovelBookNativeEntry.Block -> {
                val blockAnchor = entry.block.anchor
                DisposableEffect(blockAnchor) {
                    onDispose { handle.ttsBlockPositions.forgetBlock(blockAnchor) }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Block positions are only read by TTS follow-along, so recording
                        // them unconditionally wrote to a snapshot state map for every
                        // visible block on every scroll frame. With follow-along off the
                        // modifier is not attached at all.
                        .then(
                            if (state.readerSettings.ttsFollowAlong) {
                                Modifier.onGloballyPositioned { coordinates ->
                                    handle.ttsBlockPositions.recordBlock(
                                        anchor = blockAnchor,
                                        topInWindow = coordinates.positionInWindow().y,
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    NovelRichNativeScrollItem(
                        block = entry.block,
                        index = entry.blockIndex,
                        lastIndex = entry.sectionBlockCount - 1,
                        chapterTitle = state.chapter.name,
                        novelTitle = state.novel.title,
                        sourceId = state.novel.source,
                        chapterWebUrl = state.chapterWebUrl,
                        novelUrl = state.novel.url,
                        statusBarTopPadding = statusBarTopPadding,
                        textColor = textColor,
                        backgroundColor = textBackground,
                        readerSettings = readerSettings,
                        textTypeface = textTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                        paragraphSpacing = paragraphSpacing,
                        ttsHighlightState = ttsHighlightState,
                        ttsHighlightColor = ttsHighlightColor,
                        selectionSessionIdProvider = selectionSessionIdProvider,
                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        onPlainTap = onPlainTap,
                    )
                }
            }
            is NovelBookNativeEntry.Failed -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                ) {
                    Text(
                        text = stringResource(
                            AYMR.strings.novel_reader_book_mode_section_failed,
                        ),
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { onRetrySection(entry.sectionIndex) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                AYMR.strings.novel_reader_book_mode_section_retry,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * How often the native book list reports its reading position while scrolling.
 *
 * The list used to report on every scroll frame, which re-created the book window and the whole
 * Success state per frame. Section changes still report immediately; everything else is throttled
 * to this cadence, which is far below the 1.5s progress persistence debounce.
 */
private const val NATIVE_SCROLL_REPORT_INTERVAL_MILLIS = 150L

/**
 * Safety net for the "restoring position" cover: the cover is normally dropped by the controller
 * when the resume seek is acknowledged (onBookSeekApplied), this only bounds the worst case of a
 * renderer that never acknowledges so the book cannot stay covered forever.
 */
private const val BOOK_RESTORING_COVER_SAFETY_TIMEOUT_MS = 15_000L

/**
 * Covers the book while the resume position has not landed yet.
 *
 * A freshly mounted document paints its own start before the queued resume scroll lands, so without
 * the cover the reader flashed the first page of the book and then jumped. The cover shows the
 * theme background (or the chosen background image) instead, exactly like the WebView chapter reader
 * hides its document until the first reveal.
 */
@Composable
private fun NovelBookRestoringPositionCover(
    textColor: Color,
    textBackground: Color,
    activeBackgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    oledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    backgroundImageModel: Any?,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        NovelAtmosphereBackground(
            backgroundColor = textBackground,
            backgroundTexture = activeBackgroundTexture,
            nativeTextureStrengthPercent = nativeTextureStrengthPercent,
            oledEdgeGradient = oledEdgeGradient,
            isDarkTheme = isDarkTheme,
            pageEdgeShadow = pageEdgeShadow,
            pageEdgeShadowAlpha = pageEdgeShadowAlpha,
            backgroundImageModel = backgroundImageModel,
        )
        CircularProgressIndicator(color = textColor.copy(alpha = 0.4f))
    }
}
