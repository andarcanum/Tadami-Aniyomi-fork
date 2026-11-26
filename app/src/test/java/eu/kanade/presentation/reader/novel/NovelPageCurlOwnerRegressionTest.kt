@file:OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.novel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.PageCurlState
import eu.wewox.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Exercises the real attached owner, placement and native draw path, not a no-op composition.
 * This is an SDK30 characterization slice, not a reproduction of Android16/OEM behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class NovelPageCurlOwnerRegressionTest {

    @Test
    fun `plain curl pages survive draw-time reflow during next and previous frames`() =
        exerciseRawCurl(Surface.PLAIN)

    @Test
    fun `snapshot wrapped curl pages survive draw-time reflow during active frames`() =
        exerciseRawCurl(Surface.SNAPSHOT)

    @Test
    fun `nonfocusable AndroidView curl pages really draw through active reflow`() =
        exerciseRawCurl(Surface.ANDROID_VIEW)

    @Test
    fun `native reader text callbacks participate in active curl draw-time reflow`() =
        exerciseRawCurl(Surface.NATIVE_READER_TEXT)

    @Test
    fun `app renderer really draws intermediate next and previous frames across layout generations`() = runTest {
        withDrawFixture(this, Surface.APP_RENDERER) { fixture ->
            for (direction in PageTurnChapterNavigationDirection.entries) {
                fixture.positionAppAtPageOne()
                val stationary = fixture.pixelSignature()
                fixture.frames(2)
                assertEquals(
                    "idle app content must be stable before the animation probe",
                    stationary,
                    fixture.pixelSignature(),
                )
                val drawsBefore = fixture.stats.rootDraws
                val consumedBefore = fixture.stats.consumedNavigationRequests

                fixture.requestAppTurn(direction)
                fixture.frames(5)

                assertTrue(
                    "actual app navigation request was not consumed",
                    fixture.stats.consumedNavigationRequests > consumedBefore,
                )
                assertEquals("the app page must not settle before the intermediate frames", 1, fixture.lastSettledPage)
                assertNotEquals(
                    "the real app renderer did not draw a changing intermediate curl",
                    stationary,
                    fixture.pixelSignature(),
                )
                assertTrue(
                    "app content did not actually draw during the animation",
                    fixture.stats.rootDraws > drawsBefore,
                )

                fixture.reflowOnDraw(LANDSCAPE, 2)
                fixture.frames(3)
                fixture.reflowOnDraw(PORTRAIT, 6)
                fixture.frames(40)
                fixture.assertHealthyDraw()
            }
            assertTrue("app native TextViews never received a real layout", fixture.stats.laidOutTextViews.isNotEmpty())
            fixture.report()
        }
    }

    private fun exerciseRawCurl(surface: Surface) = runTest {
        withDrawFixture(this, surface) { fixture ->
            for (forward in listOf(true, false)) {
                fixture.positionRawAtPage(if (forward) 3 else 1)
                if (surface == Surface.NATIVE_READER_TEXT) {
                    shadowOf(Looper.getMainLooper()).idle()
                }
                val mountsBefore = fixture.stats.pageMounts
                val releasesBefore = fixture.stats.pageReleases
                val drawsBefore = fixture.stats.pageDraws
                val callbacksBefore = fixture.stats.nativeSelectionCallbacks
                val androidDrawsBefore = fixture.stats.androidDraws
                val animation = fixture.turnRaw(forward)

                fixture.frames(5)
                val progress = fixture.rawState.progress
                assertTrue("real curl animation did not remain active: progress=$progress", animation.isActive)
                assertTrue(
                    "real curl never reached a partial ${if (forward) "next" else "previous"} frame: $progress",
                    if (forward) progress > 0f && progress < 1f else progress < 0f && progress > -1f,
                )
                assertTrue(
                    "real keyed pages were not remounted during animation",
                    fixture.stats.pageMounts > mountsBefore,
                )
                assertTrue(
                    "real keyed pages were not released during animation",
                    fixture.stats.pageReleases > releasesBefore,
                )
                assertTrue("real page draw was skipped during animation", fixture.stats.pageDraws > drawsBefore)
                if (surface == Surface.NATIVE_READER_TEXT) {
                    shadowOf(Looper.getMainLooper()).idle()
                    assertTrue(
                        "actual native text callback did not run in the active frames",
                        fixture.stats.nativeSelectionCallbacks > callbacksBefore,
                    )
                }

                if (surface == Surface.ANDROID_VIEW) {
                    assertTrue(
                        "AndroidView onDraw did not run in the active frames",
                        fixture.stats.androidDraws > androidDrawsBefore,
                    )
                }
                fixture.reflowOnDraw(LANDSCAPE, 2)
                assertTrue("real count shrink did not clamp the old page", fixture.rawState.current in 0..1)
                assertTrue("geometry was changed after, rather than during, the animation", animation.isActive)
                fixture.frames(3)
                fixture.reflowOnDraw(PORTRAIT, 6)
                fixture.frames(40)
                assertTrue(
                    "real curl animation failed to complete within the bounded frame loop",
                    animation.isCompleted,
                )
                assertTrue("count shrink left the real curl page out of bounds", fixture.rawState.current in 0..5)
                fixture.assertHealthyDraw()
            }
            if (surface == Surface.ANDROID_VIEW) {
                assertTrue("plain Android TextView onMeasure never ran", fixture.stats.androidMeasures > 0)
                assertTrue("plain Android TextView onLayout never ran", fixture.stats.androidLayouts > 0)
                assertTrue("plain Android TextView onDraw never ran", fixture.stats.androidDraws > 0)
                assertTrue(
                    "nonfocusable control unexpectedly became focusable",
                    fixture.stats.laidOutTextViews.all { !it.isFocusable },
                )
            }
            fixture.report()
        }
    }

    private fun withDrawFixture(scope: TestScope, surface: Surface, block: (DrawFixture) -> Unit) {
        val fixture = DrawFixture(scope, surface)
        try {
            fixture.attach()
            fixture.assertHealthyDraw()
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private enum class Surface {
        PLAIN,
        SNAPSHOT,
        ANDROID_VIEW,
        NATIVE_READER_TEXT,
        APP_RENDERER,
    }

    private class DrawStats {
        var rootMeasures = 0
        var rootPlacements = 0
        var rootDraws = 0
        var measuresInsideViewDraw = 0
        var placementsInsideViewDraw = 0
        var pageMeasures = 0
        var pagePlacements = 0
        var pageDraws = 0
        var pageMounts = 0
        var pageReleases = 0
        var androidMeasures = 0
        var androidLayouts = 0
        var androidDraws = 0
        var nativeSelectionCallbacks = 0
        var consumedNavigationRequests = 0
        var drawingView = false
        var measuredViewport = IntSize.Zero
        var composedViewport = IntSize.Zero
        val laidOutTextViews = mutableSetOf<TextView>()
        val currentTextLayouts = mutableSetOf<TextView>()
    }

    private class DrawFixture(private val scope: TestScope, private val surface: Surface) {
        val stats = DrawStats()
        lateinit var rawState: PageCurlState
            private set
        private lateinit var rawScope: CoroutineScope
        private lateinit var controller: ActivityController<ComponentActivity>
        private lateinit var view: ComposeView
        private val clock = BroadcastFrameClock()
        private val recomposer = Recomposer(scope.coroutineContext + clock)
        private val runner = scope.backgroundScope.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        private val bitmap = Bitmap.createBitmap(HOST_SIZE, HOST_SIZE, Bitmap.Config.ARGB_8888)
        private val canvas = Canvas(bitmap)
        private val pixels = IntArray(HOST_SIZE * HOST_SIZE)
        private var viewport by mutableStateOf(PORTRAIT)
        private var pageCount by mutableStateOf(6)
        private var layoutGeneration by mutableStateOf(0L)
        private var syncedPage by mutableStateOf(1)
        private var requestedPage by mutableStateOf(-1)
        private var navigationRequest by mutableStateOf<PageTurnChapterNavigationRequest?>(null)
        private var navigationToken = 0L
        var lastSettledPage = -1
            private set
        private val settings = readerSettings()

        fun attach() {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = controller.get()
            activity.setContent(parent = recomposer) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.fillMaxSize()) {
                        BoxWithConstraints(
                            Modifier
                                .requiredSize(viewport.width.dp, viewport.height.dp)
                                .recordLayoutAndDraw(page = false),
                        ) {
                            stats.composedViewport = IntSize(constraints.maxWidth, constraints.maxHeight)
                            if (surface == Surface.APP_RENDERER) {
                                AppRenderer()
                            } else {
                                rawState = rememberPageCurlState(initialCurrent = 1)
                                rawScope = rememberCoroutineScope()
                                PageCurl(
                                    count = pageCount,
                                    key = { it },
                                    state = rawState,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page -> RawPage(page) }
                            }
                        }
                    }
                }
            }
            view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ComposeView
            shadowOf(Looper.getMainLooper()).idle()
            scope.runCurrent()
            assertTrue("fixture ComposeView is not attached to a real window", view.isAttachedToWindow)
            assertNotNull("fixture ComposeView has no window token", view.windowToken)

            // Only initial Android host measurement/layout. Later inner viewport changes are left
            // pending until AndroidComposeView.dispatchDraw calls measureAndLayout itself.
            val spec = View.MeasureSpec.makeMeasureSpec(HOST_SIZE, View.MeasureSpec.EXACTLY)
            view.measure(spec, spec)
            view.layout(0, 0, HOST_SIZE, HOST_SIZE)
            frames(5)
        }

        fun frames(count: Int) {
            repeat(count) {
                scope.advanceTimeBy(16)
                Snapshot.sendApplyNotifications()
                scope.runCurrent()
                clock.sendFrame(scope.testScheduler.currentTime * 1_000_000)
                scope.runCurrent()
                Snapshot.sendApplyNotifications()
                scope.runCurrent()
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                view.invalidate()
                stats.drawingView = true
                try {
                    view.draw(canvas)
                } finally {
                    stats.drawingView = false
                }
                stats.currentTextLayouts.clear()
                sampleTextLayouts(view)
            }
        }

        fun reflowOnDraw(nextViewport: IntSize, nextCount: Int) {
            val measuresBefore = stats.measuresInsideViewDraw
            val placementsBefore = stats.placementsInsideViewDraw
            val drawsBefore = stats.rootDraws
            println(
                "owner-probe surface=$surface viewport=$nextViewport count=$nextCount generation=${layoutGeneration + 1}",
            )
            viewport = nextViewport
            pageCount = nextCount
            layoutGeneration += 1
            // No View.measure(), View.layout(), or Android looper traversal between these inputs
            // and draw: a pre-measure would erase the replacement/placement path being probed.
            frames(1)
            assertEquals("new viewport constraints were not actually subcomposed", nextViewport, stats.composedViewport)
            assertEquals("new viewport was not actually measured", nextViewport, stats.measuredViewport)
            assertTrue("new geometry was not measured inside View.draw", stats.measuresInsideViewDraw > measuresBefore)
            assertTrue(
                "new geometry was not placed inside View.draw",
                stats.placementsInsideViewDraw > placementsBefore,
            )
            assertTrue("new geometry never reached real content drawing", stats.rootDraws > drawsBefore)
        }

        fun positionRawAtPage(page: Int) {
            val snap = rawScope.launch { rawState.snapTo(page) }
            frames(3)
            assertTrue("real curl initial snap did not complete", snap.isCompleted)
            assertEquals(page, rawState.current)
        }

        fun turnRaw(forward: Boolean): Job = rawScope.launch {
            if (forward) rawState.next() else rawState.prev()
        }

        fun positionAppAtPageOne() {
            requestedPage = 1
            frames(6)
            assertEquals("actual renderer did not consume the positioning request", -1, requestedPage)
            assertEquals("actual renderer did not publish page one", 1, lastSettledPage)
        }

        fun requestAppTurn(direction: PageTurnChapterNavigationDirection) {
            navigationRequest = PageTurnChapterNavigationRequest(direction, ++navigationToken)
        }

        fun assertHealthyDraw() {
            assertTrue("fixture was detached during the probe", view.isAttachedToWindow)
            assertEquals(HOST_SIZE, view.width)
            assertEquals(HOST_SIZE, view.height)
            assertTrue("Compose never measured a nonzero root", stats.rootMeasures > 0)
            assertTrue("Compose never placed the root", stats.rootPlacements > 0)
            assertTrue("Compose never drew real root content", stats.rootDraws > 0)
            assertTrue("native Canvas stayed blank", hasInk())
            if (surface != Surface.APP_RENDERER) {
                assertTrue("Compose never measured page content", stats.pageMeasures > 0)
                assertTrue("Compose never placed page content", stats.pagePlacements > 0)
                assertTrue("Compose never drew page content", stats.pageDraws > 0)
            }
            if (surface == Surface.ANDROID_VIEW || surface == Surface.NATIVE_READER_TEXT ||
                surface == Surface.APP_RENDERER
            ) {
                assertTrue(
                    "current native text has no nonzero layout with lines",
                    stats.currentTextLayouts.isNotEmpty(),
                )
                val textColor = if (surface == Surface.APP_RENDERER) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
                assertTrue("native text glyphs never reached the bitmap Canvas", pixels.any { it == textColor })
                if (surface != Surface.APP_RENDERER) {
                    assertTrue(
                        "native TextView background was not drawn (a black Column header is not enough)",
                        pixels.any {
                            it == android.graphics.Color.RED ||
                                it == android.graphics.Color.GREEN ||
                                it == android.graphics.Color.BLUE
                        },
                    )
                }
            }
        }

        fun pixelSignature(): Long {
            bitmap.getPixels(pixels, 0, HOST_SIZE, 0, 0, HOST_SIZE, HOST_SIZE)
            var hash = 17L
            for (index in pixels.indices step 97) {
                hash = hash * 31 + pixels[index]
            }
            return hash
        }

        private fun hasInk(): Boolean {
            bitmap.getPixels(pixels, 0, HOST_SIZE, 0, 0, HOST_SIZE, HOST_SIZE)
            return pixels.any { it ushr 24 != 0 }
        }

        private fun sampleTextLayouts(node: View) {
            if (node is TextView &&
                node.measuredWidth > 0 && node.measuredHeight > 0 &&
                node.width > 0 && node.height > 0 &&
                (node.layout?.lineCount ?: 0) > 0
            ) {
                stats.laidOutTextViews += node
                stats.currentTextLayouts += node
            }
            if (node is ViewGroup) {
                for (index in 0 until node.childCount) sampleTextLayouts(node.getChildAt(index))
            }
        }

        private fun Modifier.recordLayoutAndDraw(page: Boolean): Modifier = this
            .layout { measurable, constraints ->
                if (page) stats.pageMeasures++ else stats.rootMeasures++
                if (stats.drawingView) stats.measuresInsideViewDraw++
                val placeable = measurable.measure(constraints)
                if (!page) stats.measuredViewport = IntSize(placeable.width, placeable.height)
                layout(placeable.width, placeable.height) {
                    if (page) stats.pagePlacements++ else stats.rootPlacements++
                    if (stats.drawingView) stats.placementsInsideViewDraw++
                    placeable.placeRelative(0, 0)
                }
            }
            .drawWithContent {
                if (page) stats.pageDraws++ else stats.rootDraws++
                drawContent()
            }

        @Composable
        private fun RawPage(page: Int) {
            DisposableEffect(Unit) {
                stats.pageMounts++
                onDispose { stats.pageReleases++ }
            }
            if (surface == Surface.PLAIN) {
                PageColumn(page)
            } else {
                NovelPageTurnSnapshotRenderer { PageColumn(page) }
            }
        }

        @Composable
        private fun PageColumn(page: Int) {
            Column(Modifier.fillMaxSize().recordLayoutAndDraw(page = true)) {
                Box(Modifier.fillMaxWidth().height(32.dp).background(Color.Black))
                Box(Modifier.fillMaxWidth().weight(1f).recordLayoutAndDraw(page = true)) {
                    when (surface) {
                        Surface.ANDROID_VIEW -> AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                CountingTextView(context, stats).apply {
                                    isFocusable = false
                                    isFocusableInTouchMode = false
                                    configureText(page)
                                }
                            },
                        )
                        Surface.NATIVE_READER_TEXT -> AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                NovelPageReaderTextView(
                                    context = context,
                                    selectionRenderer = NovelSelectedTextRenderer.PAGE_READER,
                                    selectionSessionIdProvider = { 1L },
                                    onSelectedTextSelectionChanged = { stats.nativeSelectionCallbacks++ },
                                    onPlainTap = null,
                                    touchHandlingEnabled = false,
                                    selectionInteractionEnabled = false,
                                ).apply {
                                    configureText(page)
                                    // Seed a selection range so updateSelectionInteractionEnabled(false) exercises
                                    // the real deferred clearSelection() -> publishSelectionCleared() callback.
                                    (text as? Spannable)?.let { Selection.setSelection(it, 0, minOf(4, it.length)) }
                                }
                            },
                            update = { textView -> textView.updateSelectionInteractionEnabled(false) },
                            onRelease = { textView -> textView.clearFocus() },
                        )
                        else -> Box(Modifier.fillMaxSize().background(pageColor(page)))
                    }
                }
            }
        }

        @Composable
        private fun AppRenderer() {
            val pages = remember(pageCount) { contentPages(pageCount) }
            PageTurnPageRenderer(
                chapterId = 11L,
                contentPages = pages,
                spreadColumns = if (viewport.width > viewport.height) 2 else 1,
                transitionStyle = NovelPageTransitionStyle.CURL,
                readerSettings = settings,
                textColor = Color.Black,
                textBackground = Color.White,
                chapterTitleTextColor = Color.Black,
                backgroundTexture = NovelReaderBackgroundTexture.NONE,
                nativeTextureStrengthPercent = 0,
                backgroundImageModel = null,
                backgroundModeIdentity = "owner-probe",
                isBackgroundMode = false,
                activeBackgroundTexture = NovelReaderBackgroundTexture.NONE,
                activeOledEdgeGradient = false,
                isDarkTheme = false,
                pageEdgeShadow = false,
                pageEdgeShadowAlpha = 0f,
                textTypeface = null,
                chapterTitleTypeface = null,
                contentPadding = 0.dp,
                statusBarTopPadding = 0.dp,
                hasPreviousChapter = false,
                previousChapterName = null,
                hasNextChapter = false,
                nextChapterName = null,
                previousChapterLabel = "Previous",
                nextChapterLabel = "Next",
                boundaryChapterHint = "Boundary",
                showBoundaryChapterPages = false,
                syncedContentPageIndex = syncedPage,
                onToggleUi = {},
                requestedPage = requestedPage,
                onRequestedPageConsumed = { requestedPage = -1 },
                onCurrentPageChange = {
                    lastSettledPage = it
                    syncedPage = it
                },
                onMoveBackward = {},
                onMoveForward = {},
                onOpenPreviousChapter = {},
                onOpenNextChapter = {},
                chapterNavigationRequest = navigationRequest,
                onChapterNavigationRequestConsumed = {
                    stats.consumedNavigationRequests++
                    navigationRequest = null
                },
                onSelectedTextSelectionChanged = { stats.nativeSelectionCallbacks++ },
                layoutGeneration = layoutGeneration,
            )
        }

        private fun TextView.configureText(page: Int) {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 18f)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(pageColor(page).toArgb())
            // Supply real spannable text so the actual native disabled-selection clear callback
            // is exercised; this is not an active editor/focus/action-mode parity test.
            setText(
                SpannableString("Page $page\n" + "Real native text on an attached curl page.\n".repeat(8)),
                TextView.BufferType.SPANNABLE,
            )
        }

        fun report() {
            println(
                "owner-probe exercised surface=$surface" +
                    " root=${stats.rootMeasures}/${stats.rootPlacements}/${stats.rootDraws}" +
                    " pages=${stats.pageMeasures}/${stats.pagePlacements}/${stats.pageDraws}" +
                    " draw-time=${stats.measuresInsideViewDraw}/${stats.placementsInsideViewDraw}" +
                    " mounts/releases=${stats.pageMounts}/${stats.pageReleases}" +
                    " android=${stats.androidMeasures}/${stats.androidLayouts}" +
                    "/${stats.androidDraws}" +
                    " text-layouts=${stats.laidOutTextViews.size} native-callbacks=${stats.nativeSelectionCallbacks}",
            )
        }

        fun close() {
            if (::view.isInitialized) view.disposeComposition()
            recomposer.cancel()
            runner.cancel()
            scope.runCurrent()
            if (::controller.isInitialized) controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
            bitmap.recycle()
        }
    }

    private class CountingTextView(context: Context, private val stats: DrawStats) : TextView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            stats.androidMeasures++
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            stats.androidLayouts++
            super.onLayout(changed, left, top, right, bottom)
        }

        override fun onDraw(canvas: Canvas) {
            stats.androidDraws++
            super.onDraw(canvas)
        }
    }

    private companion object {
        const val HOST_SIZE = 832
        val PORTRAIT = IntSize(400, 800)
        val LANDSCAPE = IntSize(800, 400)

        fun pageColor(page: Int): Color = when (page % 3) {
            0 -> Color.Red
            1 -> Color.Blue
            else -> Color.Green
        }

        fun contentPages(count: Int): List<NovelPageContentPage> = List(count) { page ->
            val text = "Page $page. " + "Actual reader text for native draw and curl placement. ".repeat(12)
            NovelPageContentPage(
                blocks = listOf(
                    NovelPageContentBlock.Plain(
                        sourceBlockIndex = page,
                        text = text,
                        sourceTextStart = 0,
                        sourceTextEndExclusive = text.length,
                        spacingBeforePx = 0,
                        firstLineIndentEm = null,
                    ),
                ),
                pageIndex = page,
            )
        }

        fun readerSettings() = NovelReaderSettings(
            fontSize = 18,
            lineHeight = 1.2f,
            margin = 8,
            textAlign = TextAlign.LEFT,
            paragraphSpacing = 8,
            forceParagraphIndent = false,
            preserveSourceTextAlignInNative = false,
            fontFamily = "",
            forceBoldText = false,
            forceItalicText = false,
            textShadow = false,
            textShadowColor = "#000000",
            textShadowBlur = 0f,
            textShadowX = 0f,
            textShadowY = 0f,
            pageEdgeShadow = false,
            pageEdgeShadowAlpha = 0f,
            theme = NovelReaderTheme.LIGHT,
            backgroundColor = null,
            textColor = null,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            nativeTextureStrengthPercent = 0,
            appearanceMode = NovelReaderAppearanceMode.THEME,
            backgroundSource = NovelReaderBackgroundSource.PRESET,
            backgroundPresetId = "",
            customBackgroundPath = "",
            oledEdgeGradient = false,
            customThemes = emptyList(),
            useVolumeButtons = false,
            swipeGestures = true,
            pageReader = true,
            showPageChapterTitle = false,
            twoPageLandscape = true,
            preferWebViewRenderer = false,
            richNativeRendererExperimental = false,
            pageTransitionStyle = NovelPageTransitionStyle.CURL,
            verticalSeekbar = false,
            swipeToNextChapter = false,
            swipeToPrevChapter = false,
            tapToScroll = true,
            autoScroll = false,
            autoScrollInterval = 1000,
            autoScrollOffset = 100,
            showAutoScrollFloatingButton = false,
            prefetchNextChapter = false,
            fullScreenMode = false,
            keepScreenOn = false,
            showScrollPercentage = false,
            showBatteryAndTime = false,
            showKindleInfoBlock = false,
            showTimeToEnd = false,
            showWordCount = false,
            bionicReading = false,
            customCSS = "",
            customJS = "",
        )
    }
}
