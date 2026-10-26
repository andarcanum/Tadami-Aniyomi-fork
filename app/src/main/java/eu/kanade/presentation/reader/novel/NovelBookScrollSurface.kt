package eu.kanade.presentation.reader.novel

import android.os.SystemClock
import android.view.View
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * The scrollable surface the book renderer actually shows.
 *
 * The reader chrome (auto-scroll, volume keys, tap zones, TTS follow-along) was written against the
 * three chapter surfaces: the chapter WebView, the chapter pager and the chapter lazy list. Book mode
 * renders none of them - [NovelBookReader] owns a private WebView - so every one of those features
 * addressed a view that is not on screen and silently did nothing. The book renderer publishes this
 * small surface instead, and the chrome drives whatever is mounted.
 */
internal interface NovelBookScrollSurface {

    /** True while the book advances by discrete pages instead of a continuous scroll. */
    fun isPaginated(): Boolean

    /** True while the surface can still scroll down inside the mounted document. */
    fun canScrollForward(): Boolean

    /** Scrolls by [distancePx] and returns how much of it the surface consumed. */
    fun scrollBy(distancePx: Int): Int

    /** Steps one page (paginated) or one viewport (scrolled). */
    fun step(forward: Boolean)

    /**
     * Runs [script] inside the mounted book document and reports what it returned.
     *
     * TTS follow-along used to address the chapter WebView, which book mode never mounts, so it
     * scrolled a view that is not on screen. The book renderer owns its own document and this is the
     * only channel the reader chrome has into it.
     *
     * [onResult] receives the raw `evaluateJavascript` answer, or `null` when the script could not
     * run at all. Follow-along needs it to tell "the block was found and scrolled to" from
     * "the section is not in the document yet", which is the difference between a finished
     * navigation and one that has to be replayed after the section mounts.
     */
    fun evaluate(script: String, onResult: (String?) -> Unit = {}) = onResult(null)

    /**
     * Re-syncs the document's continuous auto-scroll speed.
     *
     * Returns true when the surface owns in-document movement (i.e. scrolled WebView) and
     * accepts the continuous speed. Surfaces that return false rely on discrete [scrollBy] frames.
     */
    fun syncAutoScrollSpeed(physicalPixelsPerSecond: Float): Boolean = false

    /**
     * Stops any continuous in-document scrolling the surface started.
     *
     * The scrolled WebView book runs auto-scroll as a requestAnimationFrame loop inside its
     * document; this is how the chrome tells it to stop when auto-scroll pauses (reader UI visible)
     * or is disabled. Surfaces without such a loop have nothing to stop.
     */
    fun stopAutoScroll() = Unit

    /**
     * Strips Android focus from the surface's view before the renderer switch unmounts it.
     *
     * Removing a focused AndroidView re-focuses the window root, and that cascade re-enters Compose
     * layout inside `applyChanges` (see [rememberFocusSafeInteropUnmount]). Surfaces without a
     * focusable view have nothing to strip.
     */
    fun prepareForFocusSafeUnmount() = Unit

    /** Restores focusability after a cancelled [prepareForFocusSafeUnmount]. */
    fun cancelFocusSafeUnmount() = Unit
}

/** What the book engine answered to one auto-scroll sync. */
internal data class NovelBookAutoScrollSyncReport(
    /** Whether the document's own rAF loop is still advancing the viewport. */
    val loopRunning: Boolean,
    /** Whether the document can still move down. */
    val canScrollForward: Boolean,
)

/**
 * Re-syncs the document's continuous auto-scroll loop with the chrome.
 *
 * Auto-scroll runs as a requestAnimationFrame loop inside the document, so the chrome only has to
 * tell it the continuous css speed and pixel quantum, and read back whether the loop is still moving
 * and whether the document has room.
 */
internal fun buildBookAutoScrollSyncJavascript(
    cssPixelsPerSecond: Float,
    cssPixelsPerPhysicalPixel: Float = 1f,
): String {
    val rate = if (cssPixelsPerSecond.isFinite() && cssPixelsPerSecond > 0f) cssPixelsPerSecond else 0f
    val quantum = if (cssPixelsPerPhysicalPixel.isFinite() &&
        cssPixelsPerPhysicalPixel > 0f
    ) {
        cssPixelsPerPhysicalPixel
    } else {
        1f
    }
    return listOf(
        "(function() {",
        "  var engine = window.__anBookEngine;",
        "  if (engine && typeof engine.setAutoScroll === 'function') {",
        "    engine.setAutoScroll($rate, $quantum);",
        "    var running = typeof engine.autoScrollActive === 'function' ? engine.autoScrollActive() : false;",
        "    var forward = typeof engine.canScrollForward === 'function' ? engine.canScrollForward() : true;",
        "    return JSON.stringify({ running: running, forward: forward });",
        "  }",
        "  return JSON.stringify({ running: false, forward: false });",
        "})()",
    ).joinToString("\n")
}

internal fun buildBookScrollByJavascript(cssDistance: Float): String {
    val distance = if (cssDistance.isFinite()) cssDistance else 0f
    return listOf(
        "(function() {",
        "  var engine = window.__anBookEngine;",
        "  if (engine && typeof engine.scrollBy === 'function') {",
        "    return engine.scrollBy($distance);",
        "  }",
        "  return 0;",
        "})()",
    ).joinToString("\n")
}

internal fun resolveBookAutoScrollHasSectionsLeft(
    sectionCount: Int,
    currentSectionIndex: Int,
    renderedSectionIndices: List<Int>,
    preparingSectionIndices: List<Int>,
    failedSectionIndices: List<Int>,
    isReady: Boolean,
): Boolean {
    if (!isReady || sectionCount <= 0) return true
    if (failedSectionIndices.isNotEmpty()) return true
    val maxRendered = renderedSectionIndices.maxOrNull() ?: currentSectionIndex
    val isLastSectionResident = maxRendered >= sectionCount - 1
    val isPreparing = preparingSectionIndices.isNotEmpty()
    return !isLastSectionResident || isPreparing
}

/**
 * Parses the sync answer.
 *
 * `evaluateJavascript` hands the returned JSON back as a quoted and escaped JSON string, so the
 * payload is unwrapped once and then read as real JSON, exactly like the section mutations already
 * do.
 */
internal fun parseBookAutoScrollSyncReport(rawResult: String?): NovelBookAutoScrollSyncReport? {
    if (rawResult.isNullOrBlank() || rawResult == "null") return null
    val payload = decodeBookScrollPayload(rawResult) ?: return null
    val running = payload["running"]?.jsonPrimitive?.booleanOrNull ?: false
    val forward = payload["forward"]?.jsonPrimitive?.booleanOrNull ?: true
    return NovelBookAutoScrollSyncReport(loopRunning = running, canScrollForward = forward)
}

/**
 * Unwraps what `evaluateJavascript` returned into a JSON object.
 *
 * The bridge returns the script's value as JSON, so a script returning a JSON *string* arrives
 * double encoded (`"{\"consumed\":1}"`). That string is decoded once more before being read.
 */
private fun decodeBookScrollPayload(rawResult: String): JsonObject? = runCatching {
    when (val element = BOOK_SCROLL_JSON.parseToJsonElement(rawResult.trim())) {
        is JsonObject -> element
        is JsonPrimitive -> if (element.isString) {
            BOOK_SCROLL_JSON.parseToJsonElement(element.content) as? JsonObject
        } else {
            null
        }
        else -> null
    }
}.getOrNull()

private val BOOK_SCROLL_JSON = Json { ignoreUnknownKeys = true }

/**
 * How often the auto-scroll chrome re-syncs the document's rAF loop while it is running.
 *
 * The loop keeps the speed it was last given between syncs, so the chrome only has to reach into
 * the renderer when the requested speed changed or this interval elapsed.
 */
private const val AUTO_SCROLL_SYNC_INTERVAL_MILLIS = 250L

/**
 * [NovelBookScrollSurface] backed by the renderer's own view.
 *
 * Paginated flow deliberately consumes nothing from [scrollBy]: the document lays itself out in
 * columns, so pixel scrolling would tear the page instead of turning it. Callers step pages with
 * [step] in that flow.
 */
internal class ViewNovelBookScrollSurface(
    private val view: View,
    private val paginated: () -> Boolean,
    private val onStep: (Boolean) -> Unit,
    private val clockMs: () -> Long = { SystemClock.uptimeMillis() },
) : NovelBookScrollSurface {

    @Volatile
    private var isScrollPending = false

    /** Pixels the engine reported for the last finished sync, reused while one is in flight. */
    @Volatile
    private var lastConsumedPx = 0

    /** Whether the engine still has room below the viewport. Optimistic until it answers once. */
    @Volatile
    private var engineCanScrollForward = true

    /** Whether the document's own rAF loop is currently advancing the viewport. */
    @Volatile
    private var jsLoopRunning = false

    /** Last physical rate (px/s) the JS loop was asked for. */
    @Volatile
    private var lastRequestedPhysicalRate = 0f

    @Volatile
    private var stopGeneration = 0

    /** Last time the JS loop was re-synced, bounding how stale [jsLoopRunning] can get. */
    @Volatile
    private var lastSyncMillis = 0L

    override fun isPaginated(): Boolean = paginated()

    // Paginated flow turns pages instead of scrolling, so forward scrollability is never the reason
    // to stop there. In scrolled flow the engine's own answer decides, instead of the previous
    // hardcoded `true` that made the end of the book unreachable for auto-scroll.
    override fun canScrollForward(): Boolean = paginated() || engineCanScrollForward

    override fun syncAutoScrollSpeed(physicalPixelsPerSecond: Float): Boolean {
        if (paginated()) return false
        val webView = view as? android.webkit.WebView ?: return false
        val rate = physicalPixelsPerSecond.coerceAtLeast(0f)
        val density = view.resources.displayMetrics.density.takeIf { it > 0f && it.isFinite() } ?: 1f
        val cssPixelsPerPhysicalPixel = 1f / density
        val cssPixelsPerSecond = rate / density
        val now = clockMs()
        val rateChanged = kotlin.math.abs(rate - lastRequestedPhysicalRate) > 0.01f

        if (rate <= 0f) {
            lastRequestedPhysicalRate = 0f
            stopGeneration++
            webView.evaluateJavascript(buildBookAutoScrollSyncJavascript(0f, cssPixelsPerPhysicalPixel), null)
            jsLoopRunning = false
            return true
        }

        if (jsLoopRunning && !rateChanged && now - lastSyncMillis < AUTO_SCROLL_SYNC_INTERVAL_MILLIS) {
            return true
        }

        if (isScrollPending) return true
        isScrollPending = true
        lastRequestedPhysicalRate = rate
        lastSyncMillis = now
        val currentGeneration = stopGeneration
        webView.evaluateJavascript(
            buildBookAutoScrollSyncJavascript(cssPixelsPerSecond, cssPixelsPerPhysicalPixel),
        ) { result ->
            isScrollPending = false
            if (currentGeneration == stopGeneration) {
                parseBookAutoScrollSyncReport(result)?.let { report ->
                    jsLoopRunning = report.loopRunning
                    engineCanScrollForward = report.canScrollForward
                    lastConsumedPx = if (report.canScrollForward) (rate * 0.016f).toInt().coerceAtLeast(1) else 0
                }
            }
        }
        return true
    }

    override fun scrollBy(distancePx: Int): Int {
        if (paginated() || distancePx == 0) return 0
        val webView = view as? android.webkit.WebView ?: return 0
        val density = view.resources.displayMetrics.density.takeIf { it > 0f && it.isFinite() } ?: 1f
        val cssDistance = distancePx / density
        webView.evaluateJavascript(buildBookScrollByJavascript(cssDistance)) { result ->
            val consumedCss = result?.trim('"', ' ')?.toDoubleOrNull() ?: 0.0
            lastConsumedPx = (consumedCss * density).roundToInt()
        }
        return lastConsumedPx.takeIf { it != 0 } ?: distancePx
    }

    override fun stopAutoScroll() {
        val webView = view as? android.webkit.WebView ?: return
        stopGeneration++
        jsLoopRunning = false
        lastRequestedPhysicalRate = 0f
        val density = view.resources.displayMetrics.density.takeIf { it > 0f && it.isFinite() } ?: 1f
        webView.evaluateJavascript(buildBookAutoScrollSyncJavascript(0f, 1f / density), null)
    }

    override fun step(forward: Boolean) = onStep(forward)

    override fun evaluate(script: String, onResult: (String?) -> Unit) {
        val webView = view as? android.webkit.WebView
        if (webView == null) {
            onResult(null)
            return
        }
        webView.post { webView.evaluateJavascript(script) { result -> onResult(result) } }
    }

    override fun prepareForFocusSafeUnmount() = view.prepareForFocusSafeUnmount()

    override fun cancelFocusSafeUnmount() = view.cancelFocusSafeUnmount()
}

/**
 * [NovelBookScrollSurface] backed by the native book list ([LazyListState]).
 *
 * The native renderer hosts the resident book sections in the reader's LazyColumn, so the chrome
 * (auto-scroll, volume keys, TTS follow-along) used to find no surface there and silently did
 * nothing. This surface translates the same calls onto the list state the native renderer already
 * owns.
 */
internal class LazyListNovelBookScrollSurface(
    private val listState: androidx.compose.foundation.lazy.LazyListState,
) : NovelBookScrollSurface {

    override fun isPaginated(): Boolean = false

    override fun canScrollForward(): Boolean = listState.canScrollForward

    override fun scrollBy(distancePx: Int): Int {
        if (distancePx == 0) return 0
        // dispatchRawDelta moves the list synchronously without a coroutine, which keeps this
        // surface usable from the auto-scroll frame loop. The list exposes its real scrollability
        // through canScrollForward, so "moved" is reported only while there was actually room: at
        // the end of the book the list stops moving, canScrollForward turns false and auto-scroll
        // reads the end instead of looping forever.
        val couldMove = listState.canScrollForward
        if (couldMove) {
            runCatching { listState.dispatchRawDelta(distancePx.toFloat()) }
        }
        return if (couldMove) distancePx else 0
    }

    override fun step(forward: Boolean) {
        // One "page" in the native list is roughly one viewport, mirroring the WebView flow where a
        // step scrolls ~90% of the viewport. dispatchRawDelta keeps this synchronous.
        val viewportSize = listState.layoutInfo.viewportEndOffset
        val distance = if (forward) viewportSize.coerceAtLeast(1) else -viewportSize.coerceAtLeast(1)
        runCatching { listState.dispatchRawDelta(distance.toFloat()) }
    }

    override fun evaluate(script: String, onResult: (String?) -> Unit) {
        // The native list has no JS document to run the anchor script in; the anchor is applied
        // directly by the reader chrome instead. Reporting null keeps the anchor pending until the
        // native block is actually laid out.
        onResult(null)
    }
}
