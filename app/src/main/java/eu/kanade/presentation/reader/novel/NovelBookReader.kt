package eu.kanade.presentation.reader.novel

import android.graphics.Color
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngine
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSection
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSpine
import eu.kanade.tachiyomi.ui.reader.novel.shouldApplyBookSeek
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Isolated whole-book reader surface.
 *
 * Unlike the chapter reader, the scrolled flow keeps several spine sections in one renderer document
 * and stitches the next chapter in before the reader reaches it, so crossing a chapter boundary is a
 * plain scroll. The paged flow still swaps one document per section. The location stays a
 * section/text offset shared by both layouts.
 *
 * The position flows in one direction only: the renderer owns it and reports it upwards through
 * [onLocationChanged]. The core never pushes the current position back down; it opens the book once
 * at [initialLocation] and asks for explicit moves through [seekRequest].
 */
@Composable
internal fun NovelBookReader(
    spine: NovelBookSpine,
    /**
     * Where the book is opened. Read once per book: later position changes belong to the renderer,
     * not to the caller.
     */
    initialLocation: NovelBookLocation,
    /**
     * Explicit move requested by the core (resume, seek bar, chapter picker, TTS, search).
     *
     * Applied at most once, identified by its id; [onSeekApplied] acknowledges it.
     */
    seekRequest: BookSeekRequest? = null,
    onSeekApplied: (Long) -> Unit = {},
    /**
     * Content revision per section, bumped by the core when a section's markup changed, e.g.
     * because a translation finished.
     *
     * The renderer pulls that one section again through [loadSectionHtml] and swaps it in place
     * instead of reopening the document, so the reading position survives a translation that lands
     * mid-chapter. There is no acknowledgement: the revision the renderer holds is the state.
     */
    sectionRevisions: Map<Int, Long> = emptyMap(),
    loadSectionHtml: suspend (Int) -> String? = { null },
    flow: NovelBookEngineFlow,
    transitionStyleName: String = "SLIDE",
    loadDocument: suspend (NovelBookSection) -> NovelBookDocument,
    onLocationChanged: (NovelBookLocation) -> Unit,
    onToggleReaderUi: () -> Unit,
    /**
     * Routes a short tap through the reader's configured tap zones instead of the hardcoded
     * left/center/right zones below.
     *
     * The book WebView has no access to the reader's tap-zone settings; without this the custom
     * zones (and tap-to-scroll) silently did nothing over a book.
     */
    onShortTap: ((tapX: Float, tapY: Float, width: Float, height: Float) -> Unit)? = null,
    /**
     * Publishes the mounted surface so the reader chrome can drive the book.
     *
     * Auto-scroll, volume keys and tap zones used to address the chapter WebView, which book mode
     * never shows. Null is sent when the renderer leaves the composition.
     */
    onSurfaceChanged: (NovelBookScrollSurface?) -> Unit = {},
    readerCss: String = "",
    resolveResource: (String) -> WebResourceResponse? = { null },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val operationMutex = remember { Mutex() }
    val latestSpine = rememberUpdatedState(spine)
    val latestFlow = rememberUpdatedState(flow)
    val latestTransitionStyleName = rememberUpdatedState(transitionStyleName)
    val latestLoadDocument = rememberUpdatedState(loadDocument)
    val latestOnLocationChanged = rememberUpdatedState(onLocationChanged)
    val latestOnToggleReaderUi = rememberUpdatedState(onToggleReaderUi)
    val latestOnShortTap = rememberUpdatedState(onShortTap)
    val latestReaderCss = rememberUpdatedState(readerCss)
    val latestOnSurfaceChanged = rememberUpdatedState(onSurfaceChanged)
    val latestResolveResource = rememberUpdatedState(resolveResource)

    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    var opened by remember { mutableStateOf(false) }
    var appliedFlow by remember { mutableStateOf<NovelBookEngineFlow?>(null) }
    var appliedReaderCss by remember { mutableStateOf<String?>(null) }
    val latestOpened = rememberUpdatedState(opened)

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            // The book document is loaded from a string over a non-file origin, so JS can never
            // read file:// directly: cross-origin file access needs the two deprecated flags below,
            // and they stay off. allowFileAccess only lets <img src="file://..."> load the images
            // the builder externalized next to the artifact, and the renderer's interceptor serves
            // those from the artifact images directory instead of opening the file system.
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false
        }
    }
    // Location the document is (re)opened at: the resume point first, then whatever the renderer
    // last reported. Kept in a holder so a recomposition can never reopen the book at a stale
    // coordinate, and keyed by the book itself (its first section) rather than by the spine object,
    // which is replaced on every window sync of the same book.
    val initialBookSectionChapterId = spine.sections.firstOrNull()?.chapterId
    val openLocation = remember(initialBookSectionChapterId) { arrayOf(initialLocation) }
    // Id of the last applied seek request, so the same request is never applied twice.
    val lastAppliedSeekId = remember { longArrayOf(0L) }
    var seekRetryVersion by remember { mutableStateOf(0) }
    val engineHolder = remember { arrayOfNulls<NovelBookEngine>(1) }
    // The renderer is created before the stitching lambda exists, so boundary reports from the
    // document are routed through this holder instead of duplicating the logic.
    val stitchHolder = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val renderer = remember(webView) {
        NovelBookWebViewRenderer(
            webView = webView,
            onRelocated = { charOffset, sectionIndex ->
                engineHolder[0]?.onRendererRelocated(charOffset, sectionIndex)
            },
            // Book mode runs over a compiled artifact whose section weights are already exact, so
            // DOM text lengths are no longer fed back into the spine. The JS bridge stays in place
            // for the renderer's own bookkeeping.
            onDocumentMeasured = { _, _, _ -> },
            onScrollBoundary = { forward ->
                stitchHolder[0]?.invoke(forward)
            },
            readerCss = { latestReaderCss.value },
            resolveResource = { requestUrl -> latestResolveResource.value(requestUrl) },
        )
    }
    val engine = remember(renderer) {
        NovelBookEngine(
            loadDocument = { section -> latestLoadDocument.value(section) },
            renderer = renderer,
            onLocationChanged = { nextLocation ->
                openLocation[0] = nextLocation
                latestOnLocationChanged.value(nextLocation)
            },
        )
    }
    SideEffect {
        engineHolder[0] = engine
    }
    DisposableEffect(lifecycleOwner, engine, operationMutex) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && latestOpened.value) {
                coroutineScope.launch {
                    runCatching {
                        operationMutex.withLock {
                            engine.flushLocation()
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val spineIdentity = remember(spine.sections) { spine.sections.map { it.chapterId } }
    LaunchedEffect(spineIdentity) {
        if (spine.isEmpty) return@LaunchedEffect
        loading = true
        failure = null
        val openingFlow = latestFlow.value
        val openingReaderCss = latestReaderCss.value
        val result = runCatching {
            operationMutex.withLock {
                engine.open(
                    spine = spine,
                    location = openLocation[0],
                    flow = openingFlow,
                )
            }
        }
        result.onSuccess {
            opened = true
            appliedFlow = openingFlow
            appliedReaderCss = openingReaderCss
        }
        result.onFailure { failure = it }
        loading = false
    }
    LaunchedEffect(flow, readerCss, opened) {
        if (spine.isEmpty || !opened) return@LaunchedEffect
        val flowChanged = appliedFlow != flow
        val styleChanged = appliedReaderCss != readerCss
        if (!flowChanged && !styleChanged) return@LaunchedEffect
        // Switching between scroll and pages is a different layout, so the document is rebuilt and
        // the reader sees that it is loading. Changing a style is not: the book stays on screen and
        // only its stylesheet is swapped, so there is no spinner and nothing to re-open.
        if (flowChanged) loading = true
        failure = null
        runCatching {
            operationMutex.withLock {
                engine.flushLocation()
                if (flowChanged) {
                    engine.setFlow(flow)
                } else {
                    engine.applyReaderCss(readerCss)
                }
            }
        }.onSuccess {
            appliedFlow = flow
            appliedReaderCss = readerCss
        }.onFailure { failure = it }
        if (flowChanged) loading = false
    }
    LaunchedEffect(seekRequest, opened, seekRetryVersion) {
        if (!opened) return@LaunchedEffect
        if (!shouldApplyBookSeek(seekRequest, lastAppliedSeekId[0])) return@LaunchedEffect
        val request = seekRequest ?: return@LaunchedEffect
        val location = request.location
        val isSameSection = location.sectionIndex == engine.location.sectionIndex
        failure = null
        if (!isSameSection) loading = true
        var acknowledgedLocation = location
        try {
            runNovelReaderOperation {
                operationMutex.withLock {
                    if (isSameSection) {
                        engine.goTo(location)
                    } else {
                        engine.open(
                            spine = latestSpine.value,
                            location = location,
                            flow = latestFlow.value,
                        )
                    }
                    // Reads back the clamped/acknowledged position; the raw request must not
                    // become the baseline that later seeks are compared against.
                    acknowledgedLocation = engine.location
                }
            }.onSuccess {
                openLocation[0] = acknowledgedLocation
                lastAppliedSeekId[0] = request.id
                if (!isSameSection) {
                    appliedFlow = latestFlow.value
                    appliedReaderCss = latestReaderCss.value
                }
                onSeekApplied(request.id)
            }.onFailure { failure = it }
        } finally {
            if (!isSameSection) loading = false
        }
    }

    val appliedSectionRevisions = remember(spineIdentity) { mutableMapOf<Int, Long>() }
    LaunchedEffect(sectionRevisions, opened, spineIdentity) {
        if (!opened || sectionRevisions.isEmpty()) return@LaunchedEffect
        sectionRevisions.forEach { (sectionIndex, revision) ->
            if (appliedSectionRevisions[sectionIndex] == revision) return@forEach
            for (attempt in 0 until 3) {
                val applied = runNovelReaderOperation {
                    val html = loadSectionHtml(sectionIndex) ?: return@runNovelReaderOperation false
                    operationMutex.withLock {
                        engine.replaceSection(sectionIndex = sectionIndex, html = html)
                    }
                }.onFailure {
                    android.util.Log.w("NovelReader", "Section replacement failed: $sectionIndex", it)
                }.getOrDefault(false)
                if (applied) {
                    appliedSectionRevisions[sectionIndex] = revision
                    break
                }
                if (attempt < 2) kotlinx.coroutines.delay(100L * (attempt + 1))
            }
        }
    }

    val navigate: (Boolean) -> Unit = remember(engine, coroutineScope, operationMutex) {
        { forward ->
            coroutineScope.launch {
                failure = null
                runCatching {
                    operationMutex.withLock {
                        val style = latestTransitionStyleName.value
                        if (forward) engine.next(style) else engine.previous(style)
                    }
                }.onFailure { failure = it }
            }
        }
    }
    // A boundary report asks for the neighbouring chapter to be stitched into the live document, so
    // the reader scrolls into it instead of watching the document be replaced. A failed stitch is
    // deliberately silent: the chapter on screen stays perfectly readable.
    val stitch: (Boolean) -> Unit = remember(engine, coroutineScope, operationMutex) {
        { forward ->
            coroutineScope.launch {
                runCatching {
                    operationMutex.withLock {
                        engine.stitch(forward)
                    }
                }
            }
        }
    }
    DisposableEffect(webView, navigate) {
        val surface = ViewNovelBookScrollSurface(
            view = webView,
            paginated = { latestFlow.value == NovelBookEngineFlow.PAGINATED },
            onStep = navigate,
        )
        latestOnSurfaceChanged.value(surface)
        onDispose { latestOnSurfaceChanged.value(null) }
    }
    DisposableEffect(webView, navigate, stitch) {
        stitchHolder[0] = stitch
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var downX = 0f
        var downY = 0f
        var downAt = 0L
        webView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downAt = event.eventTime
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    val isHorizontalSwipe = latestFlow.value == NovelBookEngineFlow.PAGINATED &&
                        abs(deltaX) >= touchSlop * 4f &&
                        abs(deltaX) > abs(deltaY) * 1.25f
                    when {
                        isHorizontalSwipe -> navigate(deltaX < 0f)
                        event.eventTime - downAt <= TAP_TIMEOUT_MILLIS &&
                            abs(deltaX) <= touchSlop &&
                            abs(deltaY) <= touchSlop -> {
                            // Let the WebView open links itself: a tap on an anchor must not
                            // also run the configured tap zone action.
                            val hitResultType = webView.hitTestResult.type
                            val isAnchorTap =
                                hitResultType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                    hitResultType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                            if (isAnchorTap) return@setOnTouchListener false
                            val handler = latestOnShortTap.value
                            if (handler != null) {
                                // The reader's configured tap zones (or tap-to-scroll) decide.
                                handler(
                                    event.x,
                                    event.y,
                                    view.width.toFloat(),
                                    view.height.toFloat(),
                                )
                            } else {
                                when {
                                    event.x < view.width * PREVIOUS_TAP_ZONE_FRACTION -> navigate(false)
                                    event.x > view.width * NEXT_TAP_ZONE_FRACTION -> navigate(true)
                                    else -> latestOnToggleReaderUi.value()
                                }
                            }
                        }
                    }
                }
            }
            false
        }
        onDispose {
            webView.setOnTouchListener(null)
            // The renderer is disposed from AndroidView.onRelease, which runs before this effect's
            // onDispose on composition removal. dispose() is idempotent, so calling it here as well
            // covers the case where the effect is torn down without the view being released.
            renderer.dispose()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view ->
                view.clearFocus()
                // dispose() is the single place that tears the book document down and destroys the
                // WebView; AndroidView must not call destroy() again on its own.
                renderer.dispose()
            },
        )
        if (loading && !opened) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (failure != null) {
            Surface(modifier = Modifier.align(Alignment.Center)) {
                IconButton(
                    onClick = retry@{
                        if (opened && shouldApplyBookSeek(seekRequest, lastAppliedSeekId[0])) {
                            seekRetryVersion += 1
                            return@retry
                        }
                        coroutineScope.launch {
                            loading = true
                            failure = null
                            runCatching {
                                operationMutex.withLock {
                                    engine.open(
                                        spine = latestSpine.value,
                                        location = openLocation[0],
                                        flow = latestFlow.value,
                                    )
                                }
                            }.onSuccess {
                                opened = true
                                appliedFlow = latestFlow.value
                                appliedReaderCss = latestReaderCss.value
                            }
                                .onFailure { failure = it }
                            loading = false
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                }
            }
        }
    }
}

private const val TAP_TIMEOUT_MILLIS = 300L
private const val PREVIOUS_TAP_ZONE_FRACTION = 0.3f
private const val NEXT_TAP_ZONE_FRACTION = 0.7f
