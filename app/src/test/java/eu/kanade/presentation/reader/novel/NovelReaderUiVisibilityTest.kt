package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextStyle
import eu.kanade.tachiyomi.ui.reader.novel.PageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.decodePageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeWebScrollProgressPercent
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnActivationZone
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnShadowIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelBookFlipAnimationSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

class NovelReaderUiVisibilityTest {
    @AfterEach
    fun resetNovelReaderSystemUiSession() {
        NovelReaderSystemUiSession.clear()
    }

    @Test
    fun `background preset catalog exposes six unique built-ins`() {
        assertTrue(novelReaderBackgroundPresets.size == 6)
        assertTrue(novelReaderBackgroundPresets.map { it.id }.toSet().size == 6)
    }

    @Test
    fun `background mode resolves dark text on bright backgrounds`() {
        val textColor = resolveReaderTextColorForBackgroundMode(averageLuminance = 0.82f)
        assertTrue(textColor.luminance() < 0.5f)
    }

    @Test
    fun `background mode resolves light text on dark backgrounds`() {
        val textColor = resolveReaderTextColorForBackgroundMode(averageLuminance = 0.18f)
        assertTrue(textColor.luminance() > 0.5f)
    }

    @Test
    fun `background mode falls back to preset when custom file is missing`() {
        val selection = resolveReaderBackgroundSelection(
            backgroundSource = NovelReaderBackgroundSource.CUSTOM,
            backgroundPresetId = NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID,
            customBackgroundPath = "D:/missing/custom.jpg",
            customBackgroundExists = false,
            customBackgroundId = "",
            customBackgroundItems = emptyList(),
        )

        assertTrue(selection.source == NovelReaderBackgroundSource.PRESET)
        assertTrue(selection.customPath == null)
        assertTrue(selection.preset.id == NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID)
    }

    @Test
    fun `background mode resolves selected custom background by id`() {
        val selection = resolveReaderBackgroundSelection(
            backgroundSource = NovelReaderBackgroundSource.CUSTOM,
            backgroundPresetId = NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID,
            customBackgroundPath = "",
            customBackgroundExists = false,
            customBackgroundId = "custom-2",
            customBackgroundItems = listOf(
                NovelReaderCustomBackgroundItem(
                    id = "custom-1",
                    displayName = "Custom One",
                    fileName = "custom-1.jpg",
                    absolutePath = "D:/reader/custom-1.jpg",
                    isDarkHint = false,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
                NovelReaderCustomBackgroundItem(
                    id = "custom-2",
                    displayName = "Custom Two",
                    fileName = "custom-2.jpg",
                    absolutePath = "D:/reader/custom-2.jpg",
                    isDarkHint = true,
                    createdAt = 2L,
                    updatedAt = 2L,
                ),
            ),
        )

        assertTrue(selection.source == NovelReaderBackgroundSource.CUSTOM)
        assertTrue(selection.customId == "custom-2")
        assertTrue(selection.customPath == "D:/reader/custom-2.jpg")
    }

    @Test
    fun `custom background web identity uses selected custom id`() {
        val selection = ReaderBackgroundSelection(
            source = NovelReaderBackgroundSource.CUSTOM,
            preset = novelReaderBackgroundPresets.first(),
            customId = "custom-2",
            customPath = "D:/reader/custom-2.jpg",
            customIsDarkHint = true,
        )

        val imageUrl = resolveReaderBackgroundWebImageUrl(selection)
        val identity = resolveReaderBackgroundIdentity(selection)

        assertTrue(imageUrl.contains("id=custom-2"))
        assertTrue(identity == "custom:custom-2")
    }

    @Test
    fun `unified background cards include presets and custom items`() {
        val customItems = listOf(
            ReaderBackgroundCatalogItem(
                id = "custom-1",
                displayName = "Custom One",
                fileName = "custom-1.jpg",
                createdAt = 1L,
                updatedAt = 1L,
                isDarkHint = false,
            ),
            ReaderBackgroundCatalogItem(
                id = "custom-2",
                displayName = "Custom Two",
                fileName = "custom-2.jpg",
                createdAt = 2L,
                updatedAt = 2L,
                isDarkHint = true,
            ),
        )

        val cards = buildNovelReaderBackgroundCards(customItems = customItems)

        assertTrue(cards.size == 8)
        assertTrue(cards.take(6).all { it.isBuiltIn })
        assertTrue(cards.drop(6).all { !it.isBuiltIn })
        assertTrue(cards.drop(6).map { it.id } == listOf("custom-1", "custom-2"))
    }

    @Test
    fun `web atmosphere css keeps parchment and oled stop thresholds`() {
        val css = buildWebReaderAtmosphereCss(
            appearanceMode = NovelReaderAppearanceMode.THEME,
            backgroundTexture = NovelReaderBackgroundTexture.PARCHMENT,
            oledEdgeGradient = true,
            backgroundImageUrl = null,
        )

        assertTrue(css.contains("radial-gradient(circle at 20% 20%, rgba(255,255,255,0.14), transparent 45%)"))
        assertTrue(css.contains("radial-gradient(circle at 80% 75%, rgba(0,0,0,0.12), transparent 42%)"))
        assertTrue(css.contains("radial-gradient(circle at center, rgba(0,0,0,0.0) 38%, rgba(0,0,0,0.36) 100%)"))
    }

    @Test
    fun `native atmosphere specs use css matching color stops`() {
        val layers = buildReaderAtmosphereRadialLayers(
            backgroundTexture = NovelReaderBackgroundTexture.PARCHMENT,
            oledEdgeGradient = true,
            isDarkTheme = true,
            intensityFactor = 1f,
        )

        assertTrue(layers.size == 3)
        assertTrue(
            layers.any {
                it.colorStops ==
                    listOf(0f to Color.Transparent, 0.38f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.36f))
            },
        )
        assertTrue(
            layers.any {
                it.colorStops ==
                    listOf(0f to Color.White.copy(alpha = 0.14f), 0.45f to Color.Transparent, 1f to Color.Transparent)
            },
        )
        assertTrue(
            layers.any {
                it.colorStops ==
                    listOf(0f to Color.Black.copy(alpha = 0.12f), 0.42f to Color.Transparent, 1f to Color.Transparent)
            },
        )
    }

    @Test
    fun `native atmosphere keeps oled vignette disabled on light theme`() {
        val layers = buildReaderAtmosphereRadialLayers(
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            oledEdgeGradient = true,
            isDarkTheme = false,
            intensityFactor = 1f,
        )

        assertTrue(layers.isEmpty())
    }

    @Test
    fun `native atmosphere layer order matches css stacking`() {
        val layers = buildReaderAtmosphereRadialLayers(
            backgroundTexture = NovelReaderBackgroundTexture.PARCHMENT,
            oledEdgeGradient = true,
            isDarkTheme = true,
            intensityFactor = 1f,
        )

        assertTrue(layers.size == 3)
        assertTrue(
            layers[0].colorStops ==
                listOf(0f to Color.Black.copy(alpha = 0.12f), 0.42f to Color.Transparent, 1f to Color.Transparent),
        )
        assertTrue(
            layers[1].colorStops ==
                listOf(0f to Color.White.copy(alpha = 0.14f), 0.45f to Color.Transparent, 1f to Color.Transparent),
        )
        assertTrue(
            layers[2].colorStops ==
                listOf(0f to Color.Transparent, 0.38f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.36f)),
        )
    }

    @Test
    fun `farthest corner radius matches geometric expectation`() {
        val centered = calculateRadialGradientFarthestCornerRadius(
            size = Size(100f, 50f),
            center = Offset(50f, 25f),
        )
        val offset = calculateRadialGradientFarthestCornerRadius(
            size = Size(100f, 50f),
            center = Offset(20f, 10f),
        )

        assertTrue(abs(centered - 55.9017f) < 0.001f)
        assertTrue(abs(offset - 89.44272f) < 0.001f)
    }

    @Test
    fun `native texture strength maps baseline at fifty percent`() {
        assertTrue(resolveNativeTextureIntensityFactor(50) == 1f)
    }

    @Test
    fun `native texture strength clamps out of range and scales monotonically`() {
        val zero = resolveNativeTextureIntensityFactor(0)
        val fifty = resolveNativeTextureIntensityFactor(50)
        val hundred = resolveNativeTextureIntensityFactor(100)
        val twoHundred = resolveNativeTextureIntensityFactor(200)
        val clampedLow = resolveNativeTextureIntensityFactor(-999)
        val clampedHigh = resolveNativeTextureIntensityFactor(999)

        assertTrue(zero == 0f)
        assertTrue(fifty == 1f)
        assertTrue(hundred > fifty)
        assertTrue(twoHundred > hundred)
        assertTrue(clampedLow == zero)
        assertTrue(clampedHigh == twoHundred)
    }

    @Test
    fun `native texture strength increases parchment alpha contribution`() {
        val base = buildReaderAtmosphereRadialLayers(
            backgroundTexture = NovelReaderBackgroundTexture.PARCHMENT,
            oledEdgeGradient = false,
            isDarkTheme = true,
            intensityFactor = resolveNativeTextureIntensityFactor(50),
        )
        val boosted = buildReaderAtmosphereRadialLayers(
            backgroundTexture = NovelReaderBackgroundTexture.PARCHMENT,
            oledEdgeGradient = false,
            isDarkTheme = true,
            intensityFactor = resolveNativeTextureIntensityFactor(120),
        )

        val baseDarkAlpha = base[0].colorStops.first().second.alpha
        val boostedDarkAlpha = boosted[0].colorStops.first().second.alpha
        val baseLightAlpha = base[1].colorStops.first().second.alpha
        val boostedLightAlpha = boosted[1].colorStops.first().second.alpha

        assertTrue(boostedDarkAlpha > baseDarkAlpha)
        assertTrue(boostedLightAlpha > baseLightAlpha)
    }

    @Test
    fun `bottom overlay hidden when reader ui hidden`() {
        val visible = shouldShowBottomInfoOverlay(
            showReaderUi = false,
            showBatteryAndTime = true,
            showKindleInfoBlock = true,
            showTimeToEnd = true,
            showWordCount = true,
        )

        assertFalse(visible)
    }

    @Test
    fun `bottom overlay hidden when only percentage is enabled`() {
        val visible = shouldShowBottomInfoOverlay(
            showReaderUi = true,
            showBatteryAndTime = false,
            showKindleInfoBlock = true,
            showTimeToEnd = false,
            showWordCount = false,
        )

        assertFalse(visible)
    }

    @Test
    fun `bottom overlay stays visible for battery and time`() {
        val visible = shouldShowBottomInfoOverlay(
            showReaderUi = true,
            showBatteryAndTime = true,
            showKindleInfoBlock = true,
            showTimeToEnd = false,
            showWordCount = false,
        )

        assertTrue(visible)
    }

    @Test
    fun `battery percentage is resolved from intent extras`() {
        val percent = resolveBatteryLevelPercent(level = 42, scale = 84)
        assertTrue(percent == 50)
    }

    @Test
    fun `battery percentage returns null for invalid extras`() {
        assertTrue(resolveBatteryLevelPercent(level = -1, scale = 100) == null)
        assertTrue(resolveBatteryLevelPercent(level = 50, scale = 0) == null)
    }

    @Test
    fun `bottom overlay stays visible for kindle informers`() {
        assertTrue(
            shouldShowBottomInfoOverlay(
                showReaderUi = true,
                showBatteryAndTime = false,
                showKindleInfoBlock = true,
                showTimeToEnd = true,
                showWordCount = false,
            ),
        )
        assertTrue(
            shouldShowBottomInfoOverlay(
                showReaderUi = true,
                showBatteryAndTime = false,
                showKindleInfoBlock = true,
                showTimeToEnd = false,
                showWordCount = true,
            ),
        )
    }

    @Test
    fun `bottom overlay hides kindle informers when kindle block is disabled`() {
        val visible = shouldShowBottomInfoOverlay(
            showReaderUi = true,
            showBatteryAndTime = false,
            showKindleInfoBlock = false,
            showTimeToEnd = true,
            showWordCount = true,
        )

        assertFalse(visible)
    }

    @Test
    fun `persistent progress line is visible only in fullscreen reading mode`() {
        assertTrue(shouldShowPersistentProgressLine(showReaderUi = false))
        assertFalse(shouldShowPersistentProgressLine(showReaderUi = true))
    }

    @Test
    fun `paragraph spacing slider resolves exact dp values within bounds`() {
        assertTrue(resolveParagraphSpacingDp(0).value == 0f)
        assertTrue(resolveParagraphSpacingDp(12).value == 12f)
        assertTrue(resolveParagraphSpacingDp(32).value == 32f)
        assertTrue(resolveParagraphSpacingDp(99).value == 32f)
    }

    @Test
    fun `word counter handles punctuation and unicode`() {
        val words = countNovelWords(
            listOf(
                "Hello, world!",
                "Привет, ранобэ: 123",
                "can't won't",
            ),
        )

        assertTrue(words == 7)
    }

    @Test
    fun `read words are derived from chapter progress`() {
        val readWords = estimateNovelReadWords(
            totalWords = 2000,
            readingProgressPercent = 25,
        )

        assertTrue(readWords == 500)
    }

    @Test
    fun `page reader progress percent starts at zero and ends at one hundred`() {
        assertEquals(0, resolvePageReaderReadingProgressPercent(pageIndex = 0, pageCount = 5))
        assertEquals(50, resolvePageReaderReadingProgressPercent(pageIndex = 2, pageCount = 5))
        assertEquals(100, resolvePageReaderReadingProgressPercent(pageIndex = 4, pageCount = 5))
        assertEquals(100, resolvePageReaderReadingProgressPercent(pageIndex = 0, pageCount = 1))
    }

    @Test
    fun `time to end is unknown before reading pace is collected`() {
        val minutes = estimateNovelReaderRemainingMinutes(
            paceState = NovelReaderReadingPaceState(),
            readingProgressPercent = 35,
        )

        assertTrue(minutes == null)
    }

    @Test
    fun `time to end follows measured progress pace`() {
        var paceState = NovelReaderReadingPaceState()
        paceState = updateNovelReaderReadingPace(
            paceState = paceState,
            readingProgressPercent = 0,
            timestampMs = 0L,
        )
        paceState = updateNovelReaderReadingPace(
            paceState = paceState,
            readingProgressPercent = 10,
            timestampMs = 60_000L,
        )
        paceState = updateNovelReaderReadingPace(
            paceState = paceState,
            readingProgressPercent = 40,
            timestampMs = 240_000L,
        )

        val minutes = estimateNovelReaderRemainingMinutes(
            paceState = paceState,
            readingProgressPercent = 40,
        )

        assertTrue(minutes == 6)
    }

    @Test
    fun `seekbar shown only with visible ui and eligible state`() {
        assertTrue(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = false,
                usePageReader = false,
                textBlocksCount = 10,
            ),
        )

        assertFalse(
            shouldShowVerticalSeekbar(
                showReaderUi = false,
                verticalSeekbarEnabled = true,
                showWebView = false,
                usePageReader = false,
                textBlocksCount = 10,
            ),
        )

        assertFalse(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = true,
                usePageReader = false,
                textBlocksCount = 1,
            ),
        )
        assertTrue(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = true,
                usePageReader = false,
                textBlocksCount = 10,
            ),
        )
        assertTrue(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = false,
                usePageReader = true,
                textBlocksCount = 1,
            ),
        )
    }

    @Test
    fun `page reader dismiss layer is only shown when reader ui is visible`() {
        assertTrue(
            shouldShowPageReaderDismissLayer(
                showReaderUi = true,
                usePageReader = true,
            ),
        )
        assertFalse(
            shouldShowPageReaderDismissLayer(
                showReaderUi = false,
                usePageReader = true,
            ),
        )
        assertFalse(
            shouldShowPageReaderDismissLayer(
                showReaderUi = true,
                usePageReader = false,
            ),
        )
    }

    @Test
    fun `page rail labels show first and last page inside seekbar`() {
        assertEquals(
            Pair(null, null),
            resolveReaderPageRailLabels(pageIndex = 0, pageCount = 0),
        )
        assertEquals(
            Pair("1", "1"),
            resolveReaderPageRailLabels(pageIndex = 0, pageCount = 1),
        )
        assertEquals(
            Pair("7", "10"),
            resolveReaderPageRailLabels(pageIndex = 6, pageCount = 10),
        )
    }

    @Test
    fun `page seekbar ticks are resolved per page`() {
        assertEquals(
            emptyList<Float>(),
            resolveReaderVerticalSeekbarTickFractions(pageCount = 0),
        )
        assertEquals(
            emptyList<Float>(),
            resolveReaderVerticalSeekbarTickFractions(pageCount = 1),
        )
        assertEquals(
            listOf(0f, 1f / 3f, 2f / 3f, 1f),
            resolveReaderVerticalSeekbarTickFractions(pageCount = 4),
        )
    }

    @Test
    fun `vertical seekbar labels show dynamic top and fixed bottom`() {
        val labels = verticalSeekbarLabels(
            readingProgressPercent = 42,
            showScrollPercentage = true,
        )

        assertTrue(labels.first == "42")
        assertTrue(labels.second == "100")
    }

    @Test
    fun `vertical seekbar labels hidden when percentage disabled`() {
        val labels = verticalSeekbarLabels(
            readingProgressPercent = 42,
            showScrollPercentage = false,
        )

        assertTrue(labels.first == null)
        assertTrue(labels.second == null)
    }

    @Test
    fun `content padding remains invariant when reader ui toggles`() {
        val hiddenPadding = resolveReaderContentPaddingPx(
            showReaderUi = false,
            basePaddingPx = 24,
        )
        val visiblePadding = resolveReaderContentPaddingPx(
            showReaderUi = true,
            basePaddingPx = 24,
        )

        assertTrue(hiddenPadding == visiblePadding)
        assertTrue(hiddenPadding == 24)
    }

    @Test
    fun `webview top padding remains stable when reader ui toggles`() {
        val hiddenPadding = resolveWebViewPaddingTopPx(
            statusBarHeightPx = 48,
            showReaderUi = false,
            appBarHeightPx = 120,
            basePaddingPx = 4,
            maxStatusBarInsetPx = 16,
        )
        val visiblePadding = resolveWebViewPaddingTopPx(
            statusBarHeightPx = 48,
            showReaderUi = true,
            appBarHeightPx = 120,
            basePaddingPx = 4,
            maxStatusBarInsetPx = 16,
        )

        assertTrue(hiddenPadding == 20)
        assertTrue(visiblePadding == 20)
    }

    @Test
    fun `webview top padding limits oversized status insets`() {
        val padded = resolveWebViewPaddingTopPx(
            statusBarHeightPx = 120,
            showReaderUi = false,
            appBarHeightPx = 120,
            basePaddingPx = 6,
            maxStatusBarInsetPx = 16,
        )

        assertTrue(padded == 22)
    }

    @Test
    fun `webview text align keeps site alignment when source mode is selected`() {
        val alignCss = resolveWebViewTextAlignCss(
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.SOURCE,
        )

        assertTrue(alignCss == null)
    }

    @Test
    fun `webview text align applies explicit alignments`() {
        assertTrue(
            resolveWebViewTextAlignCss(
                textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.LEFT,
            ) == "left",
        )
        assertTrue(
            resolveWebViewTextAlignCss(
                textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.CENTER,
            ) == "center",
        )
        assertTrue(
            resolveWebViewTextAlignCss(
                textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.JUSTIFY,
            ) == "justify",
        )
        assertTrue(
            resolveWebViewTextAlignCss(
                textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.RIGHT,
            ) == "right",
        )
    }

    @Test
    fun `webview first-line indent css is controlled by force paragraph indent setting`() {
        assertTrue(resolveWebViewFirstLineIndentCss(forceParagraphIndent = false) == null)
        assertTrue(resolveWebViewFirstLineIndentCss(forceParagraphIndent = true) == "2.0em")
    }

    @Test
    fun `webview bottom padding uses system navigation inset only`() {
        val hiddenPadding = resolveWebViewPaddingBottomPx(
            navigationBarHeightPx = 32,
            showReaderUi = false,
            bottomBarHeightPx = 128,
            basePaddingPx = 4,
        )
        val visiblePadding = resolveWebViewPaddingBottomPx(
            navigationBarHeightPx = 32,
            showReaderUi = true,
            bottomBarHeightPx = 128,
            basePaddingPx = 4,
        )

        assertTrue(hiddenPadding == 36)
        assertTrue(visiblePadding == 36)
    }

    @Test
    fun `webview progress update is blocked until restore completes`() {
        assertFalse(shouldTrackWebViewProgress(shouldRestoreWebScroll = true))
        assertFalse(
            shouldDispatchWebProgressUpdate(
                shouldRestoreWebScroll = true,
                newPercent = 0,
                currentPercent = 55,
            ),
        )

        assertTrue(shouldTrackWebViewProgress(shouldRestoreWebScroll = false))
        assertTrue(
            shouldDispatchWebProgressUpdate(
                shouldRestoreWebScroll = false,
                newPercent = 56,
                currentPercent = 55,
            ),
        )
    }

    @Test
    fun `webview progress for non-scrollable content stays at start`() {
        assertTrue(resolveWebViewTotalScrollablePx(contentHeightPx = 800, viewHeightPx = 900) == 0)
        assertTrue(resolveWebViewScrollProgressPercent(scrollY = 0, totalScrollable = 0) == 0)
        assertTrue(resolveWebViewScrollProgressPercent(scrollY = 50, totalScrollable = 100) == 50)
    }

    @Test
    fun `final webview progress keeps cached value when resolved progress drops to zero`() {
        assertTrue(resolveFinalWebViewProgressPercent(resolvedPercent = 0, cachedPercent = 57) == 57)
        assertTrue(resolveFinalWebViewProgressPercent(resolvedPercent = 43, cachedPercent = 57) == 43)
        assertTrue(resolveFinalWebViewProgressPercent(resolvedPercent = null, cachedPercent = 57) == 57)
    }

    @Test
    fun `multi block native tracking marks chapter complete at scroll end`() {
        val completed = resolveNativeScrollProgressForTracking(
            firstVisibleItemIndex = 3,
            textBlocksCount = 10,
            canScrollForward = false,
        )

        assertTrue(completed.first == 9 && completed.second == 10)
    }

    @Test
    fun `single block tracking progress marks as complete only at chapter end`() {
        val inProgress = resolveNativeScrollProgressForTracking(
            firstVisibleItemIndex = 0,
            textBlocksCount = 1,
            canScrollForward = true,
        )
        val completed = resolveNativeScrollProgressForTracking(
            firstVisibleItemIndex = 0,
            textBlocksCount = 1,
            canScrollForward = false,
        )

        assertTrue(inProgress.first == 0 && inProgress.second == 2)
        assertTrue(completed.first == 1 && completed.second == 2)
    }

    @Test
    fun `system bars are hidden only when fullscreen and reader ui hidden`() {
        assertTrue(shouldHideSystemBars(fullScreenMode = true, showReaderUi = false))
        assertFalse(shouldHideSystemBars(fullScreenMode = true, showReaderUi = true))
        assertFalse(shouldHideSystemBars(fullScreenMode = false, showReaderUi = false))
    }

    @Test
    fun `chapter handoff should not restore system bars on dispose`() {
        assertFalse(shouldRestoreSystemBarsOnDispose(isInternalChapterReplace = true))
        assertTrue(shouldRestoreSystemBarsOnDispose(isInternalChapterReplace = false))
    }

    @Test
    fun `internal chapter replace token is consumed once`() {
        NovelReaderSystemUiSession.markInternalChapterReplace()

        assertTrue(NovelReaderSystemUiSession.consumeInternalChapterReplace())
        assertFalse(NovelReaderSystemUiSession.consumeInternalChapterReplace())
    }

    @Test
    fun `reader exit restores captured system bars state`() {
        val captured = ReaderSystemBarsState(
            isLightStatusBars = true,
            isLightNavigationBars = false,
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
        val current = captured.copy(
            isLightStatusBars = false,
            isLightNavigationBars = true,
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
        )

        assertEquals(captured, resolveReaderExitSystemBarsState(captured = captured, current = current))
        assertEquals(current, resolveReaderExitSystemBarsState(captured = null, current = current))
    }

    @Test
    fun `page reader resets ui visibility on chapter change`() {
        assertFalse(
            resolveReaderUiAfterChapterChange(
                currentShowReaderUi = true,
                usePageReader = true,
            ),
        )
        assertTrue(
            resolveReaderUiAfterChapterChange(
                currentShowReaderUi = true,
                usePageReader = false,
            ),
        )
    }

    @Test
    fun `auto-scroll speed mapping keeps bounds and round-trips`() {
        assertTrue(intervalToAutoScrollSpeed(1) in 1..100)
        assertTrue(intervalToAutoScrollSpeed(60) in 1..100)
        assertTrue(autoScrollSpeedToInterval(1) in 1..60)
        assertTrue(autoScrollSpeedToInterval(100) in 1..60)

        val speed = intervalToAutoScrollSpeed(10)
        val interval = autoScrollSpeedToInterval(speed)
        assertTrue(interval in 8..12)
    }

    @Test
    fun `auto-scroll frame step keeps 60hz baseline speed`() {
        val speed = 55
        val baseline = autoScrollScrollStepPx(speed)
        val frameStep = autoScrollFrameStepPx(speed = speed, frameDeltaNanos = 16_000_000L)

        assertTrue(kotlin.math.abs(frameStep - baseline) < 0.0001f)
    }

    @Test
    fun `auto-scroll frame step scales with frame delta and stays positive`() {
        val speed = 55
        val baseline = autoScrollScrollStepPx(speed)

        val halfFrame = autoScrollFrameStepPx(speed = speed, frameDeltaNanos = 8_000_000L)
        val doubleFrame = autoScrollFrameStepPx(speed = speed, frameDeltaNanos = 32_000_000L)
        val tinyFrame = autoScrollFrameStepPx(speed = speed, frameDeltaNanos = 1L)

        assertTrue(kotlin.math.abs(halfFrame - baseline * 0.5f) < 0.0001f)
        assertTrue(kotlin.math.abs(doubleFrame - baseline * 2f) < 0.0001f)
        assertTrue(tinyFrame > 0f)
    }

    @Test
    fun `auto-scroll step resolver accumulates remainder until full pixel`() {
        val first = resolveAutoScrollStep(frameStepPx = 0.4f, previousRemainderPx = 0f)
        assertTrue(first.stepPx == 0)
        assertTrue(kotlin.math.abs(first.remainderPx - 0.4f) < 0.0001f)

        val second = resolveAutoScrollStep(frameStepPx = 0.7f, previousRemainderPx = first.remainderPx)
        assertTrue(second.stepPx == 1)
        assertTrue(kotlin.math.abs(second.remainderPx - 0.1f) < 0.0001f)
    }

    @Test
    fun `auto-scroll toggle hides reader panels when enabling`() {
        val state = resolveAutoScrollUiStateOnToggle(
            currentEnabled = false,
            showReaderUi = true,
            autoScrollExpanded = true,
        )

        assertTrue(state.autoScrollEnabled)
        assertFalse(state.showReaderUi)
        assertFalse(state.autoScrollExpanded)
    }

    @Test
    fun `auto-scroll toggle preserves panel states when disabling`() {
        val state = resolveAutoScrollUiStateOnToggle(
            currentEnabled = true,
            showReaderUi = true,
            autoScrollExpanded = true,
        )

        assertFalse(state.autoScrollEnabled)
        assertTrue(state.showReaderUi)
        assertTrue(state.autoScrollExpanded)
    }

    @Test
    fun `auto-scroll starts disabled for every reader session`() {
        assertFalse(resolveInitialAutoScrollEnabled(savedPreferenceEnabled = true))
        assertFalse(resolveInitialAutoScrollEnabled(savedPreferenceEnabled = false))
    }

    @Test
    fun `page pagination splits long text and keeps order`() {
        val text = buildString {
            repeat(120) { append("Paragraph $it line of text.\n\n") }
        }

        val pages = paginateTextIntoPages(
            text = text,
            widthPx = 720,
            heightPx = 480,
            textSizePx = 42f,
            lineHeightMultiplier = 1.6f,
            typeface = null,
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.LEFT,
        )

        assertTrue(pages.size > 1)
        assertTrue(pages.first().contains("Paragraph 0"))
        assertTrue(pages.last().contains("Paragraph 119"))
        assertNotEquals(pages.first(), pages.last())
    }

    @Test
    fun `page paginator is skipped when page reader mode is disabled`() {
        var invocationCount = 0

        val blocks = resolvePageReaderBlocks(
            shouldPaginate = false,
            textBlocks = listOf("First", "Second"),
            paragraphSpacingDp = 12,
        ) { _, _ ->
            invocationCount++
            listOf("paged")
        }

        assertTrue(blocks == listOf("First", "Second"))
        assertTrue(invocationCount == 0)
    }

    @Test
    fun `page reader spacing slider changes pagination separator`() {
        var paginatedInput: String? = null

        resolvePageReaderBlocks(
            shouldPaginate = true,
            textBlocks = listOf("First paragraph", "Second paragraph"),
            paragraphSpacingDp = 24,
        ) { textBlocks, paragraphSpacingDp ->
            paginatedInput = textBlocks.joinToString(resolvePageReaderParagraphSeparator(paragraphSpacingDp))
            listOf(paginatedInput.orEmpty())
        }

        assertTrue(paginatedInput == "First paragraph\n\nSecond paragraph")
    }

    @Test
    fun `paged plain renderer uses exact paragraph spacing when packing pages`() {
        val textBlocks = listOf("First paragraph", "Second paragraph")

        val compactPages = resolvePageReaderBlocks(
            shouldPaginate = true,
            textBlocks = textBlocks,
            paragraphSpacingDp = 0,
        ) { blocks, paragraphSpacingPx ->
            paginatePlainPageBlocks(
                textBlocks = blocks,
                paragraphSpacingPx = paragraphSpacingPx,
                widthPx = 600,
                heightPx = 76,
                textSizePx = 20f,
                lineHeightMultiplier = 1.2f,
                typeface = null,
                textAlign = ReaderTextAlign.LEFT,
            ).map { page ->
                page.joinToString("\n") { slice ->
                    blocks[slice.blockIndex].substring(slice.range.start, slice.range.endExclusive)
                }
            }
        }
        val spacedPages = resolvePageReaderBlocks(
            shouldPaginate = true,
            textBlocks = textBlocks,
            paragraphSpacingDp = 32,
        ) { blocks, paragraphSpacingPx ->
            paginatePlainPageBlocks(
                textBlocks = blocks,
                paragraphSpacingPx = paragraphSpacingPx,
                widthPx = 600,
                heightPx = 76,
                textSizePx = 20f,
                lineHeightMultiplier = 1.2f,
                typeface = null,
                textAlign = ReaderTextAlign.LEFT,
            ).map { page ->
                page.joinToString("\n") { slice ->
                    blocks[slice.blockIndex].substring(slice.range.start, slice.range.endExclusive)
                }
            }
        }

        assertEquals(1, compactPages.size)
        assertEquals(2, spacedPages.size)
        assertEquals("First paragraph", spacedPages.first())
        assertEquals("Second paragraph", spacedPages.last())
    }

    @Test
    fun `long paragraph continuation does not get paragraph spacing`() {
        val pages = resolvePageReaderBlocks(
            shouldPaginate = true,
            textBlocks = listOf("Long paragraph ".repeat(40).trim()),
            paragraphSpacingDp = 32,
        ) { blocks, paragraphSpacingPx ->
            paginatePlainPageBlocks(
                textBlocks = blocks,
                paragraphSpacingPx = paragraphSpacingPx,
                widthPx = 260,
                heightPx = 120,
                textSizePx = 24f,
                lineHeightMultiplier = 1.2f,
                typeface = null,
                textAlign = ReaderTextAlign.LEFT,
            ).map { page ->
                page.joinToString("\n") { slice ->
                    blocks[slice.blockIndex].substring(slice.range.start, slice.range.endExclusive)
                }
            }
        }

        assertTrue(pages.size > 1)
        val renderBlocks = buildPlainPageRenderBlocks(
            page = paginatePlainPageBlocks(
                textBlocks = listOf("Long paragraph ".repeat(40).trim()),
                paragraphSpacingPx = 32,
                widthPx = 260,
                heightPx = 120,
                textSizePx = 24f,
                lineHeightMultiplier = 1.2f,
                typeface = null,
                textAlign = ReaderTextAlign.LEFT,
            )[1],
            textBlocks = listOf("Long paragraph ".repeat(40).trim()),
            paragraphSpacingPx = 32,
            forceParagraphIndent = true,
        )

        assertFalse(pages.drop(1).first().startsWith("\n"))
        assertEquals(0, renderBlocks.first().spacingBeforePx)
        assertEquals(null, renderBlocks.first().firstLineIndentEm)
    }

    @Test
    fun `rich paged renderer uses exact paragraph spacing when packing pages`() {
        val richBlocks = listOf(
            NovelRichContentBlock.Paragraph(
                segments = listOf(NovelRichTextSegment(text = "First paragraph")),
            ),
            NovelRichContentBlock.Paragraph(
                segments = listOf(NovelRichTextSegment(text = "Second paragraph")),
            ),
        )
        val blockTexts = richBlocks.map { block ->
            buildRichPageReaderBlockText(block = block)
        }

        val compactPages = paginateRichPageBlocks(
            blockTexts = blockTexts,
            paragraphSpacingPx = 0,
            widthPx = 600,
            heightPx = 76,
            textSizePx = 20f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = ReaderTextAlign.LEFT,
        ).map { page ->
            buildAnnotatedString {
                page.forEachIndexed { index, slice ->
                    if (index > 0) append('\n')
                    append(
                        blockTexts[slice.blockIndex].text.subSequence(
                            TextRange(slice.range.start, slice.range.endExclusive),
                        ),
                    )
                }
            }
        }
        val spacedPages = paginateRichPageBlocks(
            blockTexts = blockTexts,
            paragraphSpacingPx = 32,
            widthPx = 600,
            heightPx = 76,
            textSizePx = 20f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = ReaderTextAlign.LEFT,
        ).map { page ->
            buildAnnotatedString {
                page.forEachIndexed { index, slice ->
                    if (index > 0) append('\n')
                    append(
                        blockTexts[slice.blockIndex].text.subSequence(
                            TextRange(slice.range.start, slice.range.endExclusive),
                        ),
                    )
                }
            }
        }

        assertEquals(1, compactPages.size)
        assertEquals(2, spacedPages.size)
        assertEquals("First paragraph", spacedPages.first().text)
        assertEquals("Second paragraph", spacedPages.last().text)
    }

    @Test
    fun `rich paragraph continuation does not get paragraph spacing`() {
        val blockTexts = listOf(
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Long paragraph ".repeat(40).trim())),
                ),
            ),
        )
        val pages = paginateRichPageBlocks(
            blockTexts = blockTexts,
            paragraphSpacingPx = 32,
            widthPx = 260,
            heightPx = 120,
            textSizePx = 24f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = ReaderTextAlign.LEFT,
        ).map { page ->
            buildAnnotatedString {
                page.forEachIndexed { index, slice ->
                    if (index > 0) append('\n')
                    append(
                        blockTexts[slice.blockIndex].text.subSequence(
                            TextRange(slice.range.start, slice.range.endExclusive),
                        ),
                    )
                }
            }
        }

        assertTrue(pages.size > 1)
        assertFalse(pages.drop(1).first().text.startsWith("\n"))
        val renderBlocks = buildRichPageRenderBlocks(
            page = paginateRichPageBlocks(
                blockTexts = blockTexts,
                paragraphSpacingPx = 32,
                widthPx = 260,
                heightPx = 120,
                textSizePx = 24f,
                lineHeightMultiplier = 1.2f,
                typeface = null,
                textAlign = ReaderTextAlign.LEFT,
            )[1],
            blockTexts = blockTexts,
            paragraphSpacingPx = 32,
        )
        assertEquals(0, renderBlocks.first().spacingBeforePx)
        assertEquals(null, renderBlocks.first().firstLineIndentEm)
    }

    @Test
    fun `plain paged page assembly preserves paragraph boundaries`() {
        val page = paginatePlainPageBlocks(
            textBlocks = listOf("First paragraph", "Second paragraph"),
            paragraphSpacingPx = 12,
            widthPx = 600,
            heightPx = 76,
            textSizePx = 20f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = ReaderTextAlign.LEFT,
        ).first()
        val renderBlocks = buildPlainPageRenderBlocks(
            page = page,
            textBlocks = listOf("First paragraph", "Second paragraph"),
            paragraphSpacingPx = 12,
            forceParagraphIndent = true,
        )

        assertEquals(listOf("First paragraph", "Second paragraph"), renderBlocks.map { it.text })
        assertEquals(listOf(0, 12), renderBlocks.map { it.spacingBeforePx })
        assertEquals(listOf(2f, 2f), renderBlocks.map { it.firstLineIndentEm })
    }

    @Test
    fun `plain paged page assembly marks chapter title block`() {
        val renderBlocks = buildPlainPageRenderBlocks(
            page = listOf(
                PlainPageSlice(
                    blockIndex = 0,
                    range = TextPageRange(start = 0, endExclusive = "Chapter 12".length),
                ),
                PlainPageSlice(
                    blockIndex = 1,
                    range = TextPageRange(start = 0, endExclusive = "First paragraph".length),
                ),
            ),
            textBlocks = listOf("Chapter 12", "First paragraph"),
            paragraphSpacingPx = 12,
            forceParagraphIndent = true,
            chapterTitle = "Chapter 12",
        )

        assertEquals(listOf(true, false), renderBlocks.map { it.isChapterTitle })
    }

    @Test
    fun `paragraph indent makes pagination more conservative`() {
        val textBlocks = listOf("The quick brown fox jumps over the lazy dog ".repeat(3).trim())
        val compactPages = paginatePlainPageBlocks(
            textBlocks = textBlocks,
            paragraphSpacingPx = 0,
            widthPx = 220,
            heightPx = 72,
            textSizePx = 20f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = ReaderTextAlign.LEFT,
            forceParagraphIndent = false,
        )
        val indentedPages = paginatePlainPageBlocks(
            textBlocks = textBlocks,
            paragraphSpacingPx = 0,
            widthPx = 220,
            heightPx = 72,
            textSizePx = 20f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = ReaderTextAlign.LEFT,
            forceParagraphIndent = true,
        )

        assertTrue(compactPages.isNotEmpty())
        assertTrue(indentedPages.size >= compactPages.size)
    }

    @Test
    fun `rich paged page assembly preserves spans and paragraph boundaries`() {
        val blockTexts = listOf(
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(
                        NovelRichTextSegment(text = "Alpha "),
                        NovelRichTextSegment(text = "link", linkUrl = "https://example.org"),
                    ),
                ),
            ),
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Second paragraph")),
                ),
            ),
        )
        val renderBlocks = buildRichPageRenderBlocks(
            page = paginateRichPageBlocks(
                blockTexts = blockTexts,
                paragraphSpacingPx = 12,
                widthPx = 600,
                heightPx = 76,
                textSizePx = 20f,
                lineHeightMultiplier = 1.2f,
                typeface = null,
                textAlign = ReaderTextAlign.LEFT,
            ).first(),
            blockTexts = blockTexts,
            paragraphSpacingPx = 12,
        )

        assertEquals(listOf("Alpha link", "Second paragraph"), renderBlocks.map { it.text.text })
        assertEquals(listOf(0, 12), renderBlocks.map { it.spacingBeforePx })
        assertTrue(
            renderBlocks.first().text.getStringAnnotations(
                tag = "URL",
                start = 0,
                end = renderBlocks.first().text.length,
            )
                .any { it.item == "https://example.org" },
        )
    }

    @Test
    fun `rich paged page assembly marks chapter title block`() {
        val blockTexts = listOf(
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Chapter 12")),
                ),
                forcedParagraphFirstLineIndentEm = FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM,
            ),
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "First paragraph")),
                ),
                forcedParagraphFirstLineIndentEm = FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM,
            ),
        )

        val renderBlocks = buildRichPageRenderBlocks(
            page = listOf(
                RichPageSlice.Text(
                    blockIndex = 0,
                    range = TextPageRange(start = 0, endExclusive = "Chapter 12".length),
                ),
                RichPageSlice.Text(
                    blockIndex = 1,
                    range = TextPageRange(start = 0, endExclusive = "First paragraph".length),
                ),
            ),
            blockTexts = blockTexts,
            paragraphSpacingPx = 12,
            chapterTitle = "Chapter 12",
        )

        assertEquals(listOf(true, false), renderBlocks.map { it.isChapterTitle })
        assertEquals(listOf(null, 2f), renderBlocks.map { it.firstLineIndentEm })
    }

    @Test
    fun `normalized page content preserves plain render blocks and page count`() {
        val contentPages = normalizePageReaderContentPages(
            useRichPageReader = false,
            plainPages = listOf(
                listOf(
                    PlainPageSlice(
                        blockIndex = 0,
                        range = TextPageRange(start = 0, endExclusive = "Chapter 12".length),
                    ),
                ),
                listOf(
                    PlainPageSlice(
                        blockIndex = 1,
                        range = TextPageRange(start = 0, endExclusive = "First paragraph".length),
                    ),
                ),
            ),
            richPages = emptyList(),
            plainTextBlocks = listOf("Chapter 12", "First paragraph"),
            richBlockTexts = emptyList(),
            paragraphSpacingPx = 12,
            forceParagraphIndent = true,
            chapterTitle = "Chapter 12",
        )

        assertEquals(2, contentPages.size)
        val firstBlock = contentPages.first().blocks.single() as NovelPageContentBlock.Plain
        val secondBlock = contentPages.last().blocks.single() as NovelPageContentBlock.Plain
        assertEquals("Chapter 12", firstBlock.text)
        assertTrue(firstBlock.isChapterTitle)
        assertEquals("First paragraph", secondBlock.text)
        assertEquals(2f, secondBlock.firstLineIndentEm)
    }

    @Test
    fun `normalized page content preserves rich render blocks and chapter title metadata`() {
        val richBlockTexts = listOf(
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(
                        NovelRichTextSegment(text = "Chapter 12"),
                    ),
                ),
            ),
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.BlockQuote(
                    segments = listOf(
                        NovelRichTextSegment(text = "Quoted "),
                        NovelRichTextSegment(text = "link", linkUrl = "https://example.org"),
                    ),
                    textAlign = NovelRichBlockTextAlign.JUSTIFY,
                ),
            ),
        )

        val contentPages = normalizePageReaderContentPages(
            useRichPageReader = true,
            plainPages = emptyList(),
            richPages = listOf(
                listOf(
                    RichPageSlice.Text(
                        blockIndex = 0,
                        range = TextPageRange(start = 0, endExclusive = "Chapter 12".length),
                    ),
                ),
                listOf(
                    RichPageSlice.Text(
                        blockIndex = 1,
                        range = TextPageRange(start = 0, endExclusive = "Quoted link".length),
                    ),
                ),
            ),
            plainTextBlocks = emptyList(),
            richBlockTexts = richBlockTexts,
            paragraphSpacingPx = 12,
            forceParagraphIndent = false,
            chapterTitle = "Chapter 12",
        )

        assertEquals(2, contentPages.size)
        val titleBlock = contentPages.first().blocks.single() as NovelPageContentBlock.Rich
        val quotedBlock = contentPages.last().blocks.single() as NovelPageContentBlock.Rich
        assertTrue(titleBlock.isChapterTitle)
        assertEquals("Quoted link", quotedBlock.text.text)
        assertEquals(NovelRichBlockTextAlign.JUSTIFY, quotedBlock.sourceTextAlign)
        assertTrue(
            quotedBlock.text.getStringAnnotations(
                tag = "URL",
                start = 0,
                end = quotedBlock.text.length,
            ).any { it.item == "https://example.org" },
        )
    }

    @Test
    fun `normalized page content preserves image pages between rich text pages`() {
        val richBlockTexts = listOf(
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Before")),
                ),
            ),
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "After")),
                ),
            ),
        )

        val contentPages = normalizePageReaderContentPages(
            useRichPageReader = true,
            plainPages = emptyList(),
            richPages = listOf(
                listOf(
                    RichPageSlice.Text(
                        blockIndex = 0,
                        range = TextPageRange(start = 0, endExclusive = "Before".length),
                    ),
                ),
                listOf(
                    RichPageSlice.Image(
                        sourceBlockIndex = 1,
                        imageUrl = "https://example.org/image.jpg",
                        contentDescription = "Cover art",
                    ),
                ),
                listOf(
                    RichPageSlice.Text(
                        blockIndex = 1,
                        range = TextPageRange(start = 0, endExclusive = "After".length),
                    ),
                ),
            ),
            plainTextBlocks = emptyList(),
            richBlockTexts = richBlockTexts,
            paragraphSpacingPx = 12,
            forceParagraphIndent = false,
            chapterTitle = "Before",
        )

        assertEquals(3, contentPages.size)
        val firstBlock = contentPages[0].blocks.single() as NovelPageContentBlock.Rich
        val imageBlock = contentPages[1].blocks.single() as NovelPageContentBlock.Image
        val thirdBlock = contentPages[2].blocks.single() as NovelPageContentBlock.Rich
        assertEquals("Before", firstBlock.text.text)
        assertEquals("https://example.org/image.jpg", imageBlock.imageUrl)
        assertEquals("Cover art", imageBlock.contentDescription)
        assertEquals("After", thirdBlock.text.text)
    }

    @Test
    fun `page mode no longer depends on separator heuristics`() {
        var receivedBlocks: List<String>? = null
        var receivedSpacing: Int? = null

        val pages = resolvePageReaderBlocks(
            shouldPaginate = true,
            textBlocks = listOf("First paragraph", "Second paragraph"),
            paragraphSpacingDp = 12,
        ) { blocks, paragraphSpacingPx ->
            receivedBlocks = blocks
            receivedSpacing = paragraphSpacingPx
            listOf("paged")
        }

        assertEquals(listOf("paged"), pages)
        assertEquals(listOf("First paragraph", "Second paragraph"), receivedBlocks)
        assertEquals(12, receivedSpacing)
    }

    @Test
    fun `vertical swipe up near chapter end opens next chapter`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeGesturesEnabled = true,
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 40f,
            deltaY = -320f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 240L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NEXT)
    }

    @Test
    fun `vertical swipe down near chapter start opens previous chapter`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeGesturesEnabled = true,
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 20f,
            deltaY = 300f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 260L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = false,
            wasNearChapterStartAtDown = true,
            isNearChapterEnd = false,
            isNearChapterStart = true,
        )

        assertTrue(result == VerticalChapterSwipeAction.PREVIOUS)
    }

    @Test
    fun `horizontal dominant swipe does not trigger vertical chapter switch`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeGesturesEnabled = true,
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 320f,
            deltaY = -200f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 260L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `webview vertical swipe up near chapter end opens next chapter`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeGesturesEnabled = true,
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 8f,
            deltaY = -180f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 240L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NEXT)
    }

    @Test
    fun `webview vertical swipe requires minimum distance`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeGesturesEnabled = true,
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 2f,
            deltaY = -45f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 240L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `webview vertical swipe ignores horizontal dominant gesture`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeGesturesEnabled = true,
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 220f,
            deltaY = -140f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 240L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `vertical swipe requires deliberate hold duration`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeGesturesEnabled = true,
            swipeToNextChapter = true,
            swipeToPrevChapter = false,
            deltaX = 4f,
            deltaY = -260f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 80L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `webview vertical swipe requires starting near chapter boundary`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeGesturesEnabled = true,
            swipeToNextChapter = true,
            swipeToPrevChapter = false,
            deltaX = 2f,
            deltaY = -200f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 220L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = false,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `vertical chapter swipe ignores gestures when master switch is off`() {
        val result = resolveVerticalChapterSwipeAction(
            swipeGesturesEnabled = false,
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 8f,
            deltaY = -260f,
            minSwipeDistancePx = 140f,
            horizontalTolerancePx = 24f,
            gestureDurationMillis = 220L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `webview vertical chapter swipe ignores gestures when master switch is off`() {
        val result = resolveWebViewVerticalChapterSwipeAction(
            swipeGesturesEnabled = false,
            swipeToNextChapter = true,
            swipeToPrevChapter = true,
            deltaX = 8f,
            deltaY = -180f,
            minSwipeDistancePx = 72f,
            horizontalTolerancePx = 20f,
            gestureDurationMillis = 220L,
            minHoldDurationMillis = 160L,
            wasNearChapterEndAtDown = true,
            wasNearChapterStartAtDown = false,
            isNearChapterEnd = true,
            isNearChapterStart = false,
        )

        assertTrue(result == VerticalChapterSwipeAction.NONE)
    }

    @Test
    fun `css color conversion keeps rgba order for alpha colors`() {
        val color = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.5f)
        val cssColor = colorToCssHex(color)

        assertTrue(cssColor == "#FFFFFF80")
    }

    @Test
    fun `webview renderer starts enabled by preference`() {
        assertTrue(
            shouldStartInWebView(
                preferWebViewRenderer = true,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = false,
                contentBlocksCount = 10,
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `webview renderer falls back only when no parsed content`() {
        assertFalse(
            shouldStartInWebView(
                preferWebViewRenderer = false,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = false,
                contentBlocksCount = 2,
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
        assertTrue(
            shouldStartInWebView(
                preferWebViewRenderer = false,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = false,
                contentBlocksCount = 0,
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `page reader preference overrides webview when parsed content exists`() {
        assertFalse(
            shouldStartInWebView(
                preferWebViewRenderer = true,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = true,
                contentBlocksCount = 5,
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
        assertTrue(
            shouldStartInWebView(
                preferWebViewRenderer = true,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = true,
                contentBlocksCount = 0,
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `page reader depends on parsed content blocks`() {
        assertTrue(
            shouldPaginateForPageReader(
                pageReaderEnabled = true,
                contentBlocksCount = 1,
            ),
        )
        assertFalse(
            shouldPaginateForPageReader(
                pageReaderEnabled = false,
                contentBlocksCount = 1,
            ),
        )
        assertFalse(
            shouldPaginateForPageReader(
                pageReaderEnabled = true,
                contentBlocksCount = 0,
            ),
        )
    }

    @Test
    fun `unsupported rich content forces webview startup only when experimental rich native is enabled`() {
        assertFalse(
            shouldStartInWebView(
                preferWebViewRenderer = false,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = false,
                contentBlocksCount = 5,
                richContentUnsupportedFeaturesDetected = true,
            ),
        )
        assertTrue(
            shouldStartInWebView(
                preferWebViewRenderer = false,
                richNativeRendererExperimentalEnabled = true,
                pageReaderEnabled = false,
                contentBlocksCount = 5,
                richContentUnsupportedFeaturesDetected = true,
            ),
        )
    }

    @Test
    fun `runtime sync disables webview immediately when page reader becomes active`() {
        assertFalse(
            syncShowWebViewWithReaderSettings(
                currentShowWebView = true,
                preferWebViewRenderer = true,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = true,
                contentBlocksCount = 5,
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `runtime sync re-enables webview when page reader turns off and preference still requires it`() {
        assertTrue(
            syncShowWebViewWithReaderSettings(
                currentShowWebView = false,
                preferWebViewRenderer = true,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = false,
                contentBlocksCount = 5,
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `runtime sync leaves webview disabled when active renderer is no longer valid`() {
        assertFalse(
            syncShowWebViewWithReaderSettings(
                currentShowWebView = true,
                preferWebViewRenderer = false,
                richNativeRendererExperimentalEnabled = false,
                pageReaderEnabled = false,
                contentBlocksCount = 5,
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `rich native scroll renderer supports images unless bionic or unsupported`() {
        assertTrue(
            shouldUseRichNativeScrollRenderer(
                richNativeRendererExperimentalEnabled = true,
                showWebView = false,
                usePageReader = false,
                bionicReadingEnabled = false,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(NovelRichTextSegment("Hello")),
                    ),
                ),
                richContentUnsupportedFeaturesDetected = false,
            ),
        )

        assertTrue(
            shouldUseRichNativeScrollRenderer(
                richNativeRendererExperimentalEnabled = true,
                showWebView = false,
                usePageReader = false,
                bionicReadingEnabled = false,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Image(url = "https://example.org/image.jpg"),
                ),
                richContentUnsupportedFeaturesDetected = false,
            ),
        )

        assertFalse(
            shouldUseRichNativeScrollRenderer(
                richNativeRendererExperimentalEnabled = true,
                showWebView = false,
                usePageReader = false,
                bionicReadingEnabled = true,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(NovelRichTextSegment("Hello")),
                    ),
                ),
                richContentUnsupportedFeaturesDetected = false,
            ),
        )

        assertFalse(
            shouldUseRichNativeScrollRenderer(
                richNativeRendererExperimentalEnabled = true,
                showWebView = false,
                usePageReader = false,
                bionicReadingEnabled = false,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(NovelRichTextSegment("Hello")),
                    ),
                ),
                richContentUnsupportedFeaturesDetected = true,
            ),
        )
        assertFalse(
            shouldUseRichNativeScrollRenderer(
                richNativeRendererExperimentalEnabled = false,
                showWebView = false,
                usePageReader = false,
                bionicReadingEnabled = false,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(NovelRichTextSegment("Hello")),
                    ),
                ),
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `tap edge navigation respects tap to scroll setting`() {
        assertTrue(
            resolveReaderTapAction(
                tapX = 5f,
                width = 100f,
                tapToScrollEnabled = false,
            ) == ReaderTapAction.TOGGLE_UI,
        )
        assertTrue(
            resolveReaderTapAction(
                tapX = 5f,
                width = 100f,
                tapToScrollEnabled = true,
            ) == ReaderTapAction.BACKWARD,
        )
    }

    @Test
    fun `book flip edge taps map speed presets to animation durations`() {
        assertEquals(
            500,
            resolveBookFlipEdgeTapAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
                animationSpeed = NovelBookFlipAnimationSpeed.FAST,
                tapToScrollEnabled = true,
            ),
        )
        assertEquals(
            1000,
            resolveBookFlipEdgeTapAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
                animationSpeed = NovelBookFlipAnimationSpeed.NORMAL,
                tapToScrollEnabled = true,
            ),
        )
        assertEquals(
            1500,
            resolveBookFlipEdgeTapAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
                animationSpeed = NovelBookFlipAnimationSpeed.SLOW,
                tapToScrollEnabled = true,
            ),
        )
        assertNull(
            resolveBookFlipEdgeTapAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
                animationSpeed = NovelBookFlipAnimationSpeed.FAST,
                tapToScrollEnabled = false,
            ),
        )
        assertNull(
            resolveBookFlipEdgeTapAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.SLIDE,
                animationSpeed = NovelBookFlipAnimationSpeed.FAST,
                tapToScrollEnabled = true,
            ),
        )
    }

    @Test
    fun `reader tap dispatcher routes left edge taps to backward action`() {
        var toggled = 0
        var backward = 0
        var forward = 0

        val action = dispatchReaderTapAction(
            tapX = 10f,
            width = 100f,
            tapToScrollEnabled = true,
            onToggleUi = { toggled += 1 },
            onBackward = { backward += 1 },
            onForward = { forward += 1 },
        )

        assertEquals(ReaderTapAction.BACKWARD, action)
        assertEquals(0, toggled)
        assertEquals(1, backward)
        assertEquals(0, forward)
    }

    @Test
    fun `reader tap dispatcher routes center taps to ui toggle`() {
        var toggled = 0
        var backward = 0
        var forward = 0

        val action = dispatchReaderTapAction(
            tapX = 50f,
            width = 100f,
            tapToScrollEnabled = true,
            onToggleUi = { toggled += 1 },
            onBackward = { backward += 1 },
            onForward = { forward += 1 },
        )

        assertEquals(ReaderTapAction.TOGGLE_UI, action)
        assertEquals(1, toggled)
        assertEquals(0, backward)
        assertEquals(0, forward)
    }

    @Test
    fun `reader tap dispatcher routes right edge taps to forward action`() {
        var toggled = 0
        var backward = 0
        var forward = 0

        val action = dispatchReaderTapAction(
            tapX = 90f,
            width = 100f,
            tapToScrollEnabled = true,
            onToggleUi = { toggled += 1 },
            onBackward = { backward += 1 },
            onForward = { forward += 1 },
        )

        assertEquals(ReaderTapAction.FORWARD, action)
        assertEquals(0, toggled)
        assertEquals(0, backward)
        assertEquals(1, forward)
    }

    @Test
    fun `book flip page advances map speed presets to animation durations`() {
        assertEquals(
            500,
            resolveBookFlipPageAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
                animationSpeed = NovelBookFlipAnimationSpeed.FAST,
            ),
        )
        assertEquals(
            1000,
            resolveBookFlipPageAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
                animationSpeed = NovelBookFlipAnimationSpeed.NORMAL,
            ),
        )
        assertEquals(
            1500,
            resolveBookFlipPageAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
                animationSpeed = NovelBookFlipAnimationSpeed.SLOW,
            ),
        )
        assertNull(
            resolveBookFlipPageAnimationDurationMillis(
                transitionStyle = NovelPageTransitionStyle.SLIDE,
                animationSpeed = NovelBookFlipAnimationSpeed.FAST,
            ),
        )
    }

    @Test
    fun `book flip speed slider maps presets to positions`() {
        assertEquals(0, novelBookFlipAnimationSpeedSliderIndex(NovelBookFlipAnimationSpeed.SLOW))
        assertEquals(1, novelBookFlipAnimationSpeedSliderIndex(NovelBookFlipAnimationSpeed.NORMAL))
        assertEquals(2, novelBookFlipAnimationSpeedSliderIndex(NovelBookFlipAnimationSpeed.FAST))
        assertEquals(NovelBookFlipAnimationSpeed.SLOW, resolveNovelBookFlipAnimationSpeedSliderValue(0))
        assertEquals(NovelBookFlipAnimationSpeed.NORMAL, resolveNovelBookFlipAnimationSpeedSliderValue(1))
        assertEquals(NovelBookFlipAnimationSpeed.FAST, resolveNovelBookFlipAnimationSpeedSliderValue(2))
    }

    @Test
    fun `horizontal chapter swipe helper supports webview gestures`() {
        assertTrue(
            resolveHorizontalChapterSwipeAction(
                swipeGesturesEnabled = true,
                deltaX = -220f,
                deltaY = 0f,
                thresholdPx = 160f,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ) == HorizontalChapterSwipeAction.NEXT,
        )
        assertTrue(
            resolveHorizontalChapterSwipeAction(
                swipeGesturesEnabled = false,
                deltaX = -220f,
                deltaY = 0f,
                thresholdPx = 160f,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ) == HorizontalChapterSwipeAction.NONE,
        )
    }

    @Test
    fun `horizontal chapter swipe helper ignores vertical dominant gestures`() {
        assertTrue(
            resolveHorizontalChapterSwipeAction(
                swipeGesturesEnabled = true,
                deltaX = -220f,
                deltaY = -520f,
                thresholdPx = 160f,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ) == HorizontalChapterSwipeAction.NONE,
        )
    }

    @Test
    fun `page reader boundary swipe helper opens adjacent chapter only at edges`() {
        assertEquals(
            HorizontalChapterSwipeAction.NEXT,
            resolvePageReaderBoundaryChapterSwipeAction(
                currentPage = 9,
                pageCount = 10,
                deltaX = -220f,
                deltaY = 12f,
                thresholdPx = 160f,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.PREVIOUS,
            resolvePageReaderBoundaryChapterSwipeAction(
                currentPage = 0,
                pageCount = 10,
                deltaX = 220f,
                deltaY = 10f,
                thresholdPx = 160f,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolvePageReaderBoundaryChapterSwipeAction(
                currentPage = 4,
                pageCount = 10,
                deltaX = -220f,
                deltaY = 10f,
                thresholdPx = 160f,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
    }

    @Test
    fun `page reader boundary swipe helper ignores vertical dominant gestures`() {
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolvePageReaderBoundaryChapterSwipeAction(
                currentPage = 9,
                pageCount = 10,
                deltaX = -150f,
                deltaY = 240f,
                thresholdPx = 160f,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolvePageReaderBoundaryChapterSwipeAction(
                currentPage = 0,
                pageCount = 10,
                deltaX = 150f,
                deltaY = -240f,
                thresholdPx = 160f,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
    }

    @Test
    fun `kindle dependent toggles disable when kindle info block is off`() {
        assertFalse(areQuickDialogKindleDependentControlsEnabled(showKindleInfoBlock = false))
        assertTrue(areQuickDialogKindleDependentControlsEnabled(showKindleInfoBlock = true))
    }

    @Test
    fun `renderer control state explains disabled combinations`() {
        val pageModeState = resolveRendererSettingsAvailability(
            pageReaderEnabled = true,
            showWebView = false,
            bionicReadingEnabled = false,
        )
        assertFalse(pageModeState.preferWebViewEnabled)
        assertTrue(pageModeState.preferWebViewReason == RendererSettingDisableReason.PAGE_MODE)
        assertTrue(pageModeState.richNativeEnabled)
        assertTrue(pageModeState.richNativeReason == null)

        val webViewState = resolveRendererSettingsAvailability(
            pageReaderEnabled = false,
            showWebView = true,
            bionicReadingEnabled = false,
        )
        assertTrue(webViewState.preferWebViewEnabled)
        assertTrue(webViewState.preferWebViewReason == null)
        assertFalse(webViewState.richNativeEnabled)
        assertTrue(webViewState.richNativeReason == RendererSettingDisableReason.WEBVIEW_ACTIVE)

        val bionicState = resolveRendererSettingsAvailability(
            pageReaderEnabled = false,
            showWebView = false,
            bionicReadingEnabled = true,
        )
        assertTrue(bionicState.preferWebViewEnabled)
        assertFalse(bionicState.richNativeEnabled)
        assertTrue(bionicState.richNativeReason == RendererSettingDisableReason.BIONIC_READING)
    }

    @Test
    fun `compose pager transition styles resolve to compose pager engine`() {
        assertEquals(
            NovelPageTransitionEngine.COMPOSE_PAGER,
            resolvePageTransitionEngine(NovelPageTransitionStyle.INSTANT),
        )
        assertEquals(
            NovelPageTransitionEngine.COMPOSE_PAGER,
            resolvePageTransitionEngine(NovelPageTransitionStyle.SLIDE),
        )
        assertEquals(
            NovelPageTransitionEngine.COMPOSE_PAGER,
            resolvePageTransitionEngine(NovelPageTransitionStyle.DEPTH),
        )
    }

    @Test
    fun `compose pager boundary preview only enables slide depth and book flip`() {
        assertTrue(shouldUseComposePagerBoundaryPreview(NovelPageTransitionStyle.SLIDE))
        assertTrue(shouldUseComposePagerBoundaryPreview(NovelPageTransitionStyle.DEPTH))
        assertTrue(shouldUseComposePagerBoundaryPreview(NovelPageTransitionStyle.BOOK_FLIP))
        assertFalse(shouldUseComposePagerBoundaryPreview(NovelPageTransitionStyle.INSTANT))
        assertFalse(shouldUseComposePagerBoundaryPreview(NovelPageTransitionStyle.BOOK))
        assertFalse(shouldUseComposePagerBoundaryPreview(NovelPageTransitionStyle.CURL))
    }

    @Test
    fun `book and curl transition styles resolve to page turn engine`() {
        assertEquals(
            NovelPageTransitionEngine.PAGE_TURN_RENDERER,
            resolvePageTransitionEngine(NovelPageTransitionStyle.BOOK),
        )
        assertEquals(
            NovelPageTransitionEngine.PAGE_TURN_RENDERER,
            resolvePageTransitionEngine(NovelPageTransitionStyle.CURL),
        )
    }

    @Test
    fun `unsupported page turn transition styles fall back to slide`() {
        assertEquals(
            NovelPageTransitionStyle.SLIDE,
            resolveActivePageTransitionStyle(
                requestedStyle = NovelPageTransitionStyle.BOOK,
                pageTurnRendererSupported = false,
            ),
        )
        assertEquals(
            NovelPageTransitionStyle.SLIDE,
            resolveActivePageTransitionStyle(
                requestedStyle = NovelPageTransitionStyle.CURL,
                pageTurnRendererSupported = false,
            ),
        )
        assertEquals(
            NovelPageTransitionStyle.DEPTH,
            resolveActivePageTransitionStyle(
                requestedStyle = NovelPageTransitionStyle.DEPTH,
                pageTurnRendererSupported = false,
            ),
        )
    }

    @Test
    fun `native scroll still shows atmosphere background for book flip style`() {
        assertTrue(
            shouldShowNovelAtmosphereBackground(
                usePageReader = false,
                activePageTransitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
            ),
        )
        assertFalse(
            shouldShowNovelAtmosphereBackground(
                usePageReader = true,
                activePageTransitionStyle = NovelPageTransitionStyle.BOOK_FLIP,
            ),
        )
    }

    @Test
    fun `page reader routes compose transition styles through compose pager renderer`() {
        assertEquals(
            NovelPageReaderRendererRoute.COMPOSE_PAGER,
            resolvePageReaderRendererRoute(
                usePageReader = true,
                activeStyle = NovelPageTransitionStyle.INSTANT,
            ),
        )
        assertEquals(
            NovelPageReaderRendererRoute.COMPOSE_PAGER,
            resolvePageReaderRendererRoute(
                usePageReader = true,
                activeStyle = NovelPageTransitionStyle.SLIDE,
            ),
        )
        assertEquals(
            NovelPageReaderRendererRoute.COMPOSE_PAGER,
            resolvePageReaderRendererRoute(
                usePageReader = true,
                activeStyle = NovelPageTransitionStyle.DEPTH,
            ),
        )
    }

    @Test
    fun `page reader routes book and curl through page turn renderer`() {
        assertEquals(
            NovelPageReaderRendererRoute.PAGE_TURN_RENDERER,
            resolvePageReaderRendererRoute(
                usePageReader = true,
                activeStyle = NovelPageTransitionStyle.BOOK,
            ),
        )
        assertEquals(
            NovelPageReaderRendererRoute.PAGE_TURN_RENDERER,
            resolvePageReaderRendererRoute(
                usePageReader = true,
                activeStyle = NovelPageTransitionStyle.CURL,
            ),
        )
    }

    @Test
    fun `page turn placeholder falls back to slide visuals`() {
        assertEquals(
            NovelPageTransitionStyle.SLIDE,
            resolvePageTurnRendererFallbackStyle(NovelPageTransitionStyle.BOOK),
        )
        assertEquals(
            NovelPageTransitionStyle.SLIDE,
            resolvePageTurnRendererFallbackStyle(NovelPageTransitionStyle.CURL),
        )
        assertEquals(
            NovelPageTransitionStyle.DEPTH,
            resolvePageTurnRendererFallbackStyle(NovelPageTransitionStyle.DEPTH),
        )
    }

    @Test
    fun `page turn placeholder keeps page progress index contract`() {
        assertEquals(7, resolvePageTurnRendererProgressPageIndex(currentPage = 7))
    }

    @Test
    fun `page turn renderer adds virtual boundary pages when adjacent chapters exist`() {
        assertEquals(
            7,
            resolvePageTurnRendererVirtualPageCount(
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            6,
            resolvePageTurnRendererVirtualPageCount(
                contentPageCount = 5,
                hasPreviousChapter = false,
                hasNextChapter = true,
            ),
        )
    }

    @Test
    fun `page turn renderer maps actual pages into virtual curl indices`() {
        assertEquals(
            1,
            resolvePageTurnRendererVirtualPageIndex(
                actualPageIndex = 0,
                hasPreviousChapter = true,
            ),
        )
        assertEquals(
            4,
            resolvePageTurnRendererVirtualPageIndex(
                actualPageIndex = 4,
                hasPreviousChapter = false,
            ),
        )
    }

    @Test
    fun `page turn renderer resolves chapter boundary targets from synthetic pages`() {
        assertEquals(
            HorizontalChapterSwipeAction.PREVIOUS,
            resolvePageTurnRendererBoundaryChapterTarget(
                currentPage = 0,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NEXT,
            resolvePageTurnRendererBoundaryChapterTarget(
                currentPage = 6,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolvePageTurnRendererBoundaryChapterTarget(
                currentPage = 3,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
    }

    @Test
    fun `page turn renderer opens boundary chapter only after curl settles`() {
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolvePageTurnRendererSettledBoundaryChapterTarget(
                currentPage = 0,
                progress = -0.42f,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.PREVIOUS,
            resolvePageTurnRendererSettledBoundaryChapterTarget(
                currentPage = 0,
                progress = 0f,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolvePageTurnRendererSettledBoundaryChapterTarget(
                currentPage = 6,
                progress = 0.24f,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NEXT,
            resolvePageTurnRendererSettledBoundaryChapterTarget(
                currentPage = 6,
                progress = 0f,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
    }

    @Test
    fun `compose pager renderer adds virtual boundary pages when adjacent chapters exist`() {
        assertEquals(
            7,
            resolveComposePagerVirtualPageCount(
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            6,
            resolveComposePagerVirtualPageCount(
                contentPageCount = 5,
                hasPreviousChapter = false,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            5,
            resolveComposePagerVirtualPageCount(
                contentPageCount = 5,
                hasPreviousChapter = false,
                hasNextChapter = false,
            ),
        )
    }

    @Test
    fun `compose pager renderer maps actual pages into virtual indices`() {
        assertEquals(
            1,
            resolveComposePagerVirtualPageIndex(
                actualPageIndex = 0,
                hasPreviousChapter = true,
            ),
        )
        assertEquals(
            4,
            resolveComposePagerVirtualPageIndex(
                actualPageIndex = 4,
                hasPreviousChapter = false,
            ),
        )
        assertEquals(
            5,
            resolveComposePagerVirtualPageIndex(
                actualPageIndex = 4,
                hasPreviousChapter = true,
            ),
        )
    }

    @Test
    fun `compose pager renderer maps virtual pages back to actual content pages`() {
        assertEquals(
            0,
            resolveComposePagerActualPageIndex(
                currentPage = 0,
                contentPageCount = 5,
                hasPreviousChapter = true,
            ),
        )
        assertEquals(
            0,
            resolveComposePagerActualPageIndex(
                currentPage = 1,
                contentPageCount = 5,
                hasPreviousChapter = true,
            ),
        )
        assertEquals(
            4,
            resolveComposePagerActualPageIndex(
                currentPage = 6,
                contentPageCount = 5,
                hasPreviousChapter = true,
            ),
        )
    }

    @Test
    fun `compose pager renderer round trips actual and virtual pages during resync`() {
        val virtualPage = resolveComposePagerVirtualPageIndex(
            actualPageIndex = 4,
            hasPreviousChapter = true,
        )

        assertEquals(5, virtualPage)
        assertEquals(
            4,
            resolveComposePagerActualPageIndex(
                currentPage = virtualPage,
                contentPageCount = 5,
                hasPreviousChapter = true,
            ),
        )
    }

    @Test
    fun `compose pager renderer resolves chapter boundary targets from synthetic pages`() {
        assertEquals(
            HorizontalChapterSwipeAction.PREVIOUS,
            resolveComposePagerBoundaryChapterTarget(
                currentPage = 0,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NEXT,
            resolveComposePagerBoundaryChapterTarget(
                currentPage = 6,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolveComposePagerBoundaryChapterTarget(
                currentPage = 3,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
    }

    @Test
    fun `compose pager renderer opens boundary chapter only after settle`() {
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolveComposePagerSettledBoundaryChapterTarget(
                currentPage = 0,
                progress = -0.42f,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.PREVIOUS,
            resolveComposePagerSettledBoundaryChapterTarget(
                currentPage = 0,
                progress = 0f,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NONE,
            resolveComposePagerSettledBoundaryChapterTarget(
                currentPage = 6,
                progress = 0.24f,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
        assertEquals(
            HorizontalChapterSwipeAction.NEXT,
            resolveComposePagerSettledBoundaryChapterTarget(
                currentPage = 6,
                progress = 0f,
                contentPageCount = 5,
                hasPreviousChapter = true,
                hasNextChapter = true,
            ),
        )
    }

    @Test
    fun `page turn renderer resolves current page from curl state instead of pager state`() {
        assertEquals(
            6,
            resolvePageReaderCurrentPage(
                pageReaderRendererRoute = NovelPageReaderRendererRoute.PAGE_TURN_RENDERER,
                pagerCurrentPage = 0,
                pageTurnCurrentPage = 6,
                composePagerContentPageCount = 5,
                composePagerHasPreviousChapter = true,
            ),
        )
    }

    @Test
    fun `compose pager keeps current page sourced from pager state`() {
        assertEquals(
            3,
            resolvePageReaderCurrentPage(
                pageReaderRendererRoute = NovelPageReaderRendererRoute.COMPOSE_PAGER,
                pagerCurrentPage = 3,
                pageTurnCurrentPage = 6,
                composePagerContentPageCount = 5,
                composePagerHasPreviousChapter = false,
            ),
        )
    }

    @Test
    fun `page turn seekbar thumb follows curl page rather than pager state`() {
        val seekbarValue = resolveReaderVerticalSeekbarValue(
            showWebView = false,
            webProgressPercent = 0,
            usePageReader = true,
            pageReaderRendererRoute = NovelPageReaderRendererRoute.PAGE_TURN_RENDERER,
            pagerCurrentPage = 0,
            pageTurnCurrentPage = 6,
            composePagerContentPageCount = 5,
            composePagerHasPreviousChapter = true,
            seekbarItemsCount = 11,
            readingProgressPercent = 60,
        )

        assertEquals(0.6f, seekbarValue, 0.0001f)
    }

    @Test
    fun `book and curl resolve to distinct page turn presets`() {
        val bookPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )
        val curlPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )

        assertTrue(bookPreset.curlAmount < curlPreset.curlAmount)
        assertTrue(bookPreset.shadowAlpha > curlPreset.shadowAlpha)
        assertTrue(bookPreset.backPageAlpha < curlPreset.backPageAlpha)
    }

    @Test
    fun `page turn preset widens the gap between slow and fast tuning`() {
        val slowPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.SLOW,
            intensity = NovelPageTurnIntensity.LOW,
            shadowIntensity = NovelPageTurnShadowIntensity.LOW,
        )
        val fastPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.FAST,
            intensity = NovelPageTurnIntensity.HIGH,
            shadowIntensity = NovelPageTurnShadowIntensity.HIGH,
        )

        assertTrue(slowPreset.animationDurationMillis - fastPreset.animationDurationMillis >= 360)
        assertEquals(slowPreset.curlAmount, fastPreset.curlAmount)
        assertTrue(slowPreset.shadowAlpha < fastPreset.shadowAlpha)
    }

    @Test
    fun `page turn slider mappings round trip the expanded value set`() {
        assertEquals(0, novelPageTurnSpeedSliderIndex(NovelPageTurnSpeed.SLOWER))
        assertEquals(NovelPageTurnSpeed.SLOWER, resolveNovelPageTurnSpeedSliderValue(0))
        assertEquals(4, novelPageTurnSpeedSliderIndex(NovelPageTurnSpeed.FASTER))
        assertEquals(NovelPageTurnSpeed.FASTER, resolveNovelPageTurnSpeedSliderValue(4))

        assertEquals(0, novelPageTurnIntensitySliderIndex(NovelPageTurnIntensity.SOFTER))
        assertEquals(NovelPageTurnIntensity.SOFTER, resolveNovelPageTurnIntensitySliderValue(0))
        assertEquals(4, novelPageTurnIntensitySliderIndex(NovelPageTurnIntensity.STRONGER))
        assertEquals(NovelPageTurnIntensity.STRONGER, resolveNovelPageTurnIntensitySliderValue(4))

        assertEquals(0, novelPageTurnShadowIntensitySliderIndex(NovelPageTurnShadowIntensity.SOFTER))
        assertEquals(
            NovelPageTurnShadowIntensity.SOFTER,
            resolveNovelPageTurnShadowIntensitySliderValue(0),
        )
        assertEquals(4, novelPageTurnShadowIntensitySliderIndex(NovelPageTurnShadowIntensity.STRONGER))
        assertEquals(
            NovelPageTurnShadowIntensity.STRONGER,
            resolveNovelPageTurnShadowIntensitySliderValue(4),
        )

        assertEquals(0, novelPageTurnActivationZoneSliderIndex(NovelPageTurnActivationZone.NARROWER))
        assertEquals(
            NovelPageTurnActivationZone.NARROWER,
            resolveNovelPageTurnActivationZoneSliderValue(0),
        )
        assertEquals(4, novelPageTurnActivationZoneSliderIndex(NovelPageTurnActivationZone.WIDER))
        assertEquals(
            NovelPageTurnActivationZone.WIDER,
            resolveNovelPageTurnActivationZoneSliderValue(4),
        )
    }

    @Test
    fun `page turn tuning summary text formats expanded labels`() {
        assertEquals(
            "Slower speed • Stronger intensity • Softer shadow",
            resolveNovelPageTurnTuningSummaryText(
                format = "%1\$s speed • %2\$s intensity • %3\$s shadow",
                speedLabel = "Slower",
                intensityLabel = "Stronger",
                shadowLabel = "Softer",
            ),
        )
    }

    @Test
    fun `page turn tuning summary text includes activation zone label`() {
        assertEquals(
            "Slower speed | Stronger intensity | Softer shadow | Wider zone",
            resolveNovelPageTurnTuningSummaryText(
                format = "%1\$s speed | %2\$s intensity | %3\$s shadow | %4\$s zone",
                speedLabel = "Slower",
                intensityLabel = "Stronger",
                shadowLabel = "Softer",
                activationZoneLabel = "Wider",
            ),
        )
    }

    @Test
    fun `page turn preset responds to the new slider extremes`() {
        val softerPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.SLOWER,
            intensity = NovelPageTurnIntensity.SOFTER,
            shadowIntensity = NovelPageTurnShadowIntensity.SOFTER,
        )
        val strongerPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.FASTER,
            intensity = NovelPageTurnIntensity.STRONGER,
            shadowIntensity = NovelPageTurnShadowIntensity.STRONGER,
        )

        assertEquals(NovelPageTransitionStyle.BOOK, softerPreset.style)
        assertEquals(NovelPageTransitionStyle.BOOK, strongerPreset.style)
        assertTrue(softerPreset.animationDurationMillis > strongerPreset.animationDurationMillis)
        assertTrue(softerPreset.curlAmount < strongerPreset.curlAmount)
        assertTrue(softerPreset.shadowAlpha < strongerPreset.shadowAlpha)
        assertTrue(softerPreset.backPageAlpha < strongerPreset.backPageAlpha)
    }

    @Test
    fun `page turn speed presets use the updated slow and slower durations`() {
        val slowerBookPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.SLOWER,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )
        val slowBookPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.SLOW,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )
        val slowerCurlPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.SLOWER,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )
        val slowCurlPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.SLOW,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )

        assertEquals(800, slowerBookPreset.animationDurationMillis)
        assertEquals(700, slowBookPreset.animationDurationMillis)
        assertEquals(740, slowerCurlPreset.animationDurationMillis)
        assertEquals(640, slowCurlPreset.animationDurationMillis)
    }

    @Test
    fun `page turn softer and low intensity use the updated stronger offsets`() {
        val softerPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.SOFTER,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )
        val lowPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.LOW,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )

        assertEquals(0.40f, softerPreset.curlAmount, 0.0001f)
        assertEquals(0.16f, softerPreset.backPageAlpha, 0.0001f)
        assertEquals(0.56f, lowPreset.curlAmount, 0.0001f)
        assertEquals(0.24f, lowPreset.backPageAlpha, 0.0001f)
    }

    @Test
    fun `page turn tuning values modify preset output without changing selected style`() {
        val basePreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
        )
        val tunedPreset = resolveNovelPageTurnPreset(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.FAST,
            intensity = NovelPageTurnIntensity.HIGH,
            shadowIntensity = NovelPageTurnShadowIntensity.LOW,
        )

        assertEquals(NovelPageTransitionStyle.CURL, basePreset.style)
        assertEquals(NovelPageTransitionStyle.CURL, tunedPreset.style)
        assertTrue(tunedPreset.animationDurationMillis < basePreset.animationDurationMillis)
        assertTrue(tunedPreset.curlAmount > basePreset.curlAmount)
        assertTrue(tunedPreset.shadowAlpha < basePreset.shadowAlpha)
    }

    @Test
    fun `page reader content layout keeps text padding separate from full page surface`() {
        val layout = resolveNovelPageReaderContentLayout(
            contentPadding = 20.dp,
            statusBarTopPadding = 12.dp,
            horizontalMargin = 18,
        )

        assertEquals(32.dp, layout.textPadding.calculateTopPadding())
        assertEquals(20.dp, layout.textPadding.calculateBottomPadding())
        assertEquals(18.dp, layout.textPadding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(18.dp, layout.textPadding.calculateRightPadding(LayoutDirection.Ltr))
    }

    @Test
    fun `page reader reserves bottom book inset`() {
        assertEquals(
            30.dp,
            resolveNovelPageReaderBookBottomInset(
                density = Density(1f),
                fontSize = 20,
                lineHeight = 1.2f,
            ),
        )
    }

    @Test
    @OptIn(ExperimentalPageCurlApi::class)
    fun `page turn renderer config maps book and curl to tuned drag profiles`() {
        val narrowConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.NARROWER,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )
        val wideConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.WIDER,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )
        val curlConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.WIDER,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )

        assertEquals(NovelPageTurnDragMode.START_END, narrowConfig.dragMode)
        assertEquals(
            PageCurlConfig.DragInteraction.PointerBehavior.PageEdge,
            narrowConfig.dragPointerBehavior,
        )
        assertEquals(
            PageCurlConfig.DragInteraction.PointerBehavior.PageEdge,
            wideConfig.dragPointerBehavior,
        )
        assertEquals(NovelPageTurnDragMode.START_END, curlConfig.dragMode)
        assertEquals(0.20f, narrowConfig.dragActivationEdgeFraction, 0.0001f)
        assertEquals(0.40f, wideConfig.dragActivationEdgeFraction, 0.0001f)
        assertEquals(0.40f, curlConfig.dragActivationEdgeFraction, 0.0001f)
        assertTrue(narrowConfig.dragActivationEdgeFraction < wideConfig.dragActivationEdgeFraction)
        assertTrue(narrowConfig.dragTargetReachFraction < wideConfig.dragTargetReachFraction)
        assertTrue(wideConfig.dragTargetReachFraction < curlConfig.dragTargetReachFraction)
    }

    @Test
    fun `page turn renderer activation zone scales to explicit edge widths for book and curl`() {
        val narrowBookConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.NARROWER,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )
        val normalBookConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.NORMAL,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )
        val widerBookConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.WIDER,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )
        val narrowCurlConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.NARROWER,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )
        val normalCurlConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.NORMAL,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )
        val wideCurlConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.WIDER,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )

        assertEquals(0.20f, narrowBookConfig.dragActivationEdgeFraction, 0.0001f)
        assertEquals(0.30f, normalBookConfig.dragActivationEdgeFraction, 0.0001f)
        assertEquals(0.40f, widerBookConfig.dragActivationEdgeFraction, 0.0001f)
        assertEquals(0.20f, narrowCurlConfig.dragActivationEdgeFraction, 0.0001f)
        assertEquals(0.30f, normalCurlConfig.dragActivationEdgeFraction, 0.0001f)
        assertEquals(0.40f, wideCurlConfig.dragActivationEdgeFraction, 0.0001f)
        assertEquals(NovelPageTurnDragMode.START_END, widerBookConfig.dragMode)
        assertEquals(NovelPageTurnDragMode.START_END, narrowCurlConfig.dragMode)
        assertEquals(NovelPageTurnDragMode.START_END, wideCurlConfig.dragMode)
        assertTrue(narrowBookConfig.dragTargetReachFraction > narrowBookConfig.dragActivationEdgeFraction)
        assertTrue(widerBookConfig.dragTargetReachFraction > widerBookConfig.dragActivationEdgeFraction)
        assertTrue(narrowCurlConfig.dragActivationEdgeFraction < wideCurlConfig.dragActivationEdgeFraction)
        assertTrue(narrowCurlConfig.dragTargetReachFraction < wideCurlConfig.dragTargetReachFraction)
    }

    @Test
    fun `page turn renderer config keeps back page color parchment tinted instead of pure white`() {
        val config = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.WIDE,
            textBackground = Color.White,
            canMoveBackward = true,
            canMoveForward = true,
        )

        assertNotEquals(Color.White, config.backPageColor)
        assertTrue(config.backPageColor.luminance() < Color.White.luminance())
    }

    @Test
    fun `page turn renderer config keeps back page color visibly warmer than white`() {
        val config = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.WIDE,
            textBackground = Color.White,
            canMoveBackward = true,
            canMoveForward = true,
        )

        assertTrue(
            config.backPageColor.luminance() < 0.90f,
            "expected parchment-like back face, got ${config.backPageColor}",
        )
    }

    @Test
    fun `page turn renderer config disables unavailable turn directions without losing center tap`() {
        val config = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.CURL,
            speed = NovelPageTurnSpeed.FAST,
            intensity = NovelPageTurnIntensity.HIGH,
            shadowIntensity = NovelPageTurnShadowIntensity.LOW,
            activationZone = NovelPageTurnActivationZone.WIDE,
            textBackground = Color(0xFF171717),
            canMoveBackward = false,
            canMoveForward = true,
        )

        assertFalse(config.dragBackwardEnabled)
        assertTrue(config.dragForwardEnabled)
        assertFalse(config.tapBackwardEnabled)
        assertTrue(config.tapForwardEnabled)
        assertTrue(config.tapCustomEnabled)
        assertTrue(config.centerTapWidthFraction >= 0.2f)
    }

    @Test
    fun `page turn custom tap action keeps center tap when tap to scroll is disabled`() {
        assertEquals(
            PageTurnCustomTapAction.TOGGLE_UI,
            resolvePageTurnCustomTapAction(
                tapXFraction = 0.5f,
                currentPage = 0,
                pageCount = 3,
                centerTapWidthFraction = 0.2f,
                hasPreviousChapter = true,
                hasNextChapter = true,
                tapToScrollEnabled = false,
            ),
        )
    }

    @Test
    fun `page turn custom tap action ignores edge chapter navigation when tap to scroll is disabled`() {
        assertEquals(
            PageTurnCustomTapAction.NONE,
            resolvePageTurnCustomTapAction(
                tapXFraction = 0.05f,
                currentPage = 0,
                pageCount = 3,
                centerTapWidthFraction = 0.2f,
                hasPreviousChapter = true,
                hasNextChapter = true,
                tapToScrollEnabled = false,
            ),
        )
    }

    @Test
    fun `page turn custom tap action moves to previous page from the left edge`() {
        assertEquals(
            PageTurnCustomTapAction.MOVE_PREVIOUS_PAGE,
            resolvePageTurnCustomTapAction(
                tapXFraction = 0.05f,
                currentPage = 1,
                pageCount = 3,
                centerTapWidthFraction = 0.2f,
                hasPreviousChapter = true,
                hasNextChapter = true,
                tapToScrollEnabled = true,
            ),
        )
    }

    @Test
    fun `page turn custom tap action moves to next page from the right edge`() {
        assertEquals(
            PageTurnCustomTapAction.MOVE_NEXT_PAGE,
            resolvePageTurnCustomTapAction(
                tapXFraction = 0.95f,
                currentPage = 1,
                pageCount = 3,
                centerTapWidthFraction = 0.2f,
                hasPreviousChapter = true,
                hasNextChapter = true,
                tapToScrollEnabled = true,
            ),
        )
    }

    @Test
    fun `page turn custom tap action opens previous chapter only at first page edge`() {
        assertEquals(
            PageTurnCustomTapAction.OPEN_PREVIOUS_CHAPTER,
            resolvePageTurnCustomTapAction(
                tapXFraction = 0.05f,
                currentPage = 0,
                pageCount = 3,
                centerTapWidthFraction = 0.2f,
                hasPreviousChapter = true,
                hasNextChapter = true,
                tapToScrollEnabled = true,
            ),
        )
    }

    @Test
    fun `page turn custom tap action opens next chapter only at last page edge`() {
        assertEquals(
            PageTurnCustomTapAction.OPEN_NEXT_CHAPTER,
            resolvePageTurnCustomTapAction(
                tapXFraction = 0.95f,
                currentPage = 2,
                pageCount = 3,
                centerTapWidthFraction = 0.2f,
                hasPreviousChapter = true,
                hasNextChapter = true,
                tapToScrollEnabled = true,
            ),
        )
    }

    @Test
    @OptIn(ExperimentalPageCurlApi::class)
    fun `page turn renderer keeps backward tap zones wide enough for left side taps`() {
        val bookConfig = resolveNovelPageTurnRendererConfig(
            style = NovelPageTransitionStyle.BOOK,
            speed = NovelPageTurnSpeed.NORMAL,
            intensity = NovelPageTurnIntensity.MEDIUM,
            shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
            activationZone = NovelPageTurnActivationZone.WIDE,
            textBackground = Color(0xFFF8F2E7),
            canMoveBackward = true,
            canMoveForward = true,
        )
        val bookTapInteraction = createPageTurnTapInteraction(bookConfig)
        val curlTapInteraction = createPageTurnTapInteraction(
            resolveNovelPageTurnRendererConfig(
                style = NovelPageTransitionStyle.CURL,
                speed = NovelPageTurnSpeed.NORMAL,
                intensity = NovelPageTurnIntensity.MEDIUM,
                shadowIntensity = NovelPageTurnShadowIntensity.MEDIUM,
                activationZone = NovelPageTurnActivationZone.WIDE,
                textBackground = Color(0xFFF8F2E7),
                canMoveBackward = true,
                canMoveForward = true,
            ),
        )

        assertEquals(
            0.4f,
            bookTapInteraction.backward.target.right,
            0.0001f,
        )
        assertEquals(
            0.4f,
            curlTapInteraction.backward.target.right,
            0.0001f,
        )
    }

    @Test
    fun `page turn tuning controls only show for book and curl in page mode`() {
        assertFalse(
            shouldShowPageTurnTuningControls(
                pageReaderEnabled = false,
                style = NovelPageTransitionStyle.BOOK,
            ),
        )
        assertFalse(
            shouldShowPageTurnTuningControls(
                pageReaderEnabled = true,
                style = NovelPageTransitionStyle.SLIDE,
            ),
        )
        assertTrue(
            shouldShowPageTurnTuningControls(
                pageReaderEnabled = true,
                style = NovelPageTransitionStyle.BOOK,
            ),
        )
        assertTrue(
            shouldShowPageTurnTuningControls(
                pageReaderEnabled = true,
                style = NovelPageTransitionStyle.CURL,
            ),
        )
    }

    @Test
    fun `rich page content contract stays stable across compose and page turn transition routes`() {
        val richBlockTexts = listOf(
            buildRichPageReaderBlockText(
                block = NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Chapter 12")),
                    textAlign = NovelRichBlockTextAlign.CENTER,
                ),
            ),
        )

        val normalizedPages = normalizePageReaderContentPages(
            useRichPageReader = true,
            plainPages = emptyList(),
            richPages = listOf(
                listOf(
                    RichPageSlice.Text(
                        blockIndex = 0,
                        range = TextPageRange(start = 0, endExclusive = "Chapter 12".length),
                    ),
                ),
            ),
            plainTextBlocks = emptyList(),
            richBlockTexts = richBlockTexts,
            paragraphSpacingPx = 12,
            forceParagraphIndent = false,
            chapterTitle = "Chapter 12",
        )

        val slideRoute = resolvePageReaderRendererRoute(
            usePageReader = true,
            activeStyle = resolveActivePageTransitionStyle(
                requestedStyle = NovelPageTransitionStyle.SLIDE,
                pageTurnRendererSupported = true,
            ),
        )
        val bookRoute = resolvePageReaderRendererRoute(
            usePageReader = true,
            activeStyle = resolveActivePageTransitionStyle(
                requestedStyle = NovelPageTransitionStyle.BOOK,
                pageTurnRendererSupported = true,
            ),
        )

        assertEquals(NovelPageReaderRendererRoute.COMPOSE_PAGER, slideRoute)
        assertEquals(NovelPageReaderRendererRoute.PAGE_TURN_RENDERER, bookRoute)
        assertEquals(1, normalizedPages.size)
        val richBlock = normalizedPages.single().blocks.single() as NovelPageContentBlock.Rich
        assertTrue(richBlock.isChapterTitle)
        assertEquals(NovelRichBlockTextAlign.CENTER, richBlock.sourceTextAlign)
        assertEquals(null, richBlock.firstLineIndentEm)
    }

    @Test
    fun `rich paged renderer eligibility survives book and curl style fallback decisions`() {
        val richEligible = shouldUseRichNativePageRenderer(
            richNativeRendererExperimentalEnabled = true,
            pageReaderEnabled = true,
            bionicReadingEnabled = false,
            richContentBlocks = listOf(
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment("Styled")),
                ),
            ),
            richContentUnsupportedFeaturesDetected = false,
        )

        val bookStyle = resolveActivePageTransitionStyle(
            requestedStyle = NovelPageTransitionStyle.BOOK,
            pageTurnRendererSupported = true,
        )
        val curlFallbackStyle = resolveActivePageTransitionStyle(
            requestedStyle = NovelPageTransitionStyle.CURL,
            pageTurnRendererSupported = false,
        )

        assertTrue(richEligible)
        assertEquals(NovelPageTransitionStyle.BOOK, bookStyle)
        assertEquals(NovelPageTransitionStyle.SLIDE, curlFallbackStyle)
    }

    @Test
    fun `instant transition keeps decorative transforms neutral`() {
        val spec = resolveComposePagerTransitionSpec(
            style = NovelPageTransitionStyle.INSTANT,
            pageOffset = 0.65f,
        )

        assertEquals(1f, spec.alpha)
        assertEquals(1f, spec.scale)
        assertEquals(0f, spec.translationXFraction)
        assertTrue(spec.cancelPagerMotion)
        assertTrue(spec.hideOffscreenPages)
    }

    @Test
    fun `slide transition preserves standard pager motion`() {
        val spec = resolveComposePagerTransitionSpec(
            style = NovelPageTransitionStyle.SLIDE,
            pageOffset = 0.65f,
        )

        assertEquals(1f, spec.alpha)
        assertEquals(1f, spec.scale)
        assertEquals(0f, spec.translationXFraction)
        assertFalse(spec.cancelPagerMotion)
    }

    @Test
    fun `depth transition applies bounded scale alpha and translation`() {
        val spec = resolveComposePagerTransitionSpec(
            style = NovelPageTransitionStyle.DEPTH,
            pageOffset = 0.65f,
        )

        assertTrue(spec.alpha in 0.65f..1f)
        assertTrue(spec.scale in 0.92f..1f)
        assertTrue(abs(spec.translationXFraction) > 0f)
        assertTrue(abs(spec.translationXFraction) <= 0.12f)
        assertFalse(spec.cancelPagerMotion)
    }

    @Test
    fun `renderer tuning changes keep quick reader settings dialog open`() {
        assertFalse(
            shouldDismissReaderSettingsDialogAfterFamilyChange(
                NovelReaderSettingsFamily.RENDERER_TUNING,
            ),
        )
        assertFalse(
            shouldDismissReaderSettingsDialogAfterFamilyChange(
                NovelReaderSettingsFamily.LIVE_TEXT_STYLING,
            ),
        )
    }

    @Test
    fun `rich paged renderer is allowed for text only rich chapters`() {
        assertTrue(
            shouldUseRichNativePageRenderer(
                richNativeRendererExperimentalEnabled = true,
                pageReaderEnabled = true,
                bionicReadingEnabled = false,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(
                            NovelRichTextSegment(
                                text = "Styled",
                                style = NovelRichTextStyle(bold = true),
                            ),
                        ),
                    ),
                    NovelRichContentBlock.BlockQuote(
                        segments = listOf(NovelRichTextSegment("Quoted")),
                        textAlign = NovelRichBlockTextAlign.JUSTIFY,
                    ),
                ),
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `rich paged renderer allows image only and mixed rich chapters but still blocks unsupported modes`() {
        assertTrue(
            shouldUseRichNativePageRenderer(
                richNativeRendererExperimentalEnabled = true,
                pageReaderEnabled = true,
                bionicReadingEnabled = false,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Image(url = "https://example.org/image.jpg"),
                ),
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
        assertTrue(
            shouldUseRichNativePageRenderer(
                richNativeRendererExperimentalEnabled = true,
                pageReaderEnabled = true,
                bionicReadingEnabled = false,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(NovelRichTextSegment("Styled")),
                    ),
                    NovelRichContentBlock.Image(url = "https://example.org/image.jpg"),
                ),
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
        assertFalse(
            shouldUseRichNativePageRenderer(
                richNativeRendererExperimentalEnabled = true,
                pageReaderEnabled = true,
                bionicReadingEnabled = false,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(NovelRichTextSegment("Styled")),
                    ),
                ),
                richContentUnsupportedFeaturesDetected = true,
            ),
        )
        assertFalse(
            shouldUseRichNativePageRenderer(
                richNativeRendererExperimentalEnabled = true,
                pageReaderEnabled = true,
                bionicReadingEnabled = true,
                richContentBlocks = listOf(
                    NovelRichContentBlock.Paragraph(
                        segments = listOf(NovelRichTextSegment("Styled")),
                    ),
                ),
                richContentUnsupportedFeaturesDetected = false,
            ),
        )
    }

    @Test
    fun `chapter swipe controls are disabled while page mode is active`() {
        assertFalse(
            areChapterSwipeControlsEnabled(
                swipeGesturesEnabled = true,
                pageReaderEnabled = true,
            ),
        )
    }

    @Test
    fun `chapter swipe controls require swipe gestures and scroll mode`() {
        assertTrue(
            areChapterSwipeControlsEnabled(
                swipeGesturesEnabled = true,
                pageReaderEnabled = false,
            ),
        )
        assertFalse(
            areChapterSwipeControlsEnabled(
                swipeGesturesEnabled = false,
                pageReaderEnabled = false,
            ),
        )
    }

    @Test
    fun `settings surface strategy exposes ownership summaries`() {
        val strategy = resolveNovelReaderSettingsSurfaceStrategy()

        assertTrue(NovelReaderSettingsFamily.SOURCE_ALIGNMENT_POLICY in strategy.globalOnlyFamilies)
        assertTrue(NovelReaderSettingsFamily.CHAPTER_CACHE_POLICY in strategy.globalOnlyFamilies)
        assertTrue(NovelReaderSettingsFamily.LIVE_TEXT_STYLING in strategy.quickDialogOnlyFamilies)
        assertTrue(NovelReaderSettingsFamily.RENDERER_TUNING in strategy.quickDialogOnlyFamilies)
    }

    @Test
    fun `native text align uses source alignment with global fallback when preserve is enabled`() {
        assertTrue(
            resolveNativeTextAlign(
                globalTextAlign = ReaderTextAlign.JUSTIFY,
                preserveSourceTextAlignInNative = true,
                sourceTextAlign = NovelRichBlockTextAlign.CENTER,
            ) == TextAlign.Center,
        )
        assertTrue(
            resolveNativeTextAlign(
                globalTextAlign = ReaderTextAlign.JUSTIFY,
                preserveSourceTextAlignInNative = true,
                sourceTextAlign = null,
            ) == TextAlign.Justify,
        )
    }

    @Test
    fun `native text align uses global alignment when preserve is disabled`() {
        assertTrue(
            resolveNativeTextAlign(
                globalTextAlign = ReaderTextAlign.JUSTIFY,
                preserveSourceTextAlignInNative = false,
                sourceTextAlign = NovelRichBlockTextAlign.LEFT,
            ) == TextAlign.Justify,
        )
        assertTrue(
            resolveNativeTextAlign(
                globalTextAlign = ReaderTextAlign.SOURCE,
                preserveSourceTextAlignInNative = false,
                sourceTextAlign = NovelRichBlockTextAlign.LEFT,
            ) == null,
        )
    }

    @Test
    fun `native first-line indent can be forced for every paragraph`() {
        assertTrue(
            resolveNativeFirstLineIndentEm(
                forceParagraphIndent = false,
                sourceFirstLineIndentEm = 2f,
            ) == 2f,
        )
        assertTrue(
            resolveNativeFirstLineIndentEm(
                forceParagraphIndent = true,
                sourceFirstLineIndentEm = 2f,
            ) == 2f,
        )
        assertTrue(
            resolveNativeFirstLineIndentEm(
                forceParagraphIndent = true,
                sourceFirstLineIndentEm = null,
            ) == 2f,
        )
    }

    @Test
    fun `page reader layout align follows global alignment with source fallback`() {
        assertTrue(
            resolvePageReaderLayoutTextAlign(
                globalTextAlign = ReaderTextAlign.JUSTIFY,
                preserveSourceTextAlignInNative = true,
            ) == ReaderTextAlign.JUSTIFY,
        )
        assertTrue(
            resolvePageReaderLayoutTextAlign(
                globalTextAlign = ReaderTextAlign.RIGHT,
                preserveSourceTextAlignInNative = false,
            ) == ReaderTextAlign.RIGHT,
        )
        assertTrue(
            resolvePageReaderLayoutTextAlign(
                globalTextAlign = ReaderTextAlign.SOURCE,
                preserveSourceTextAlignInNative = false,
            ) == ReaderTextAlign.LEFT,
        )
    }

    @Test
    fun `page reader render align follows global alignment with source fallback`() {
        assertTrue(
            resolvePageReaderRenderTextAlign(
                globalTextAlign = ReaderTextAlign.CENTER,
            ) == TextAlign.Center,
        )
        assertTrue(
            resolvePageReaderRenderTextAlign(
                globalTextAlign = ReaderTextAlign.RIGHT,
            ) == TextAlign.End,
        )
        assertTrue(
            resolvePageReaderRenderTextAlign(
                globalTextAlign = ReaderTextAlign.SOURCE,
            ) == TextAlign.Start,
        )
    }

    @Test
    fun `page reader progress restore scales to current page count after repagination`() {
        assertEquals(
            8,
            resolveInitialPageReaderPage(
                savedPageReaderProgress = PageReaderProgress(index = 4, totalItems = 5),
                legacyLastSavedIndex = 0,
                pageCount = 9,
            ),
        )
    }

    @Test
    fun `page reader progress restore clamps safely when new page count shrinks`() {
        assertEquals(
            2,
            resolveInitialPageReaderPage(
                savedPageReaderProgress = PageReaderProgress(index = 9, totalItems = 10),
                legacyLastSavedIndex = 0,
                pageCount = 3,
            ),
        )
    }

    @Test
    fun `page reader backward chapter handoff starts from last page`() {
        assertEquals(
            6,
            resolveInitialPageReaderPage(
                savedPageReaderProgress = PageReaderProgress(index = 0, totalItems = 1),
                legacyLastSavedIndex = 0,
                pageCount = 7,
                chapterHandoffTarget = NovelReaderPageReaderHandoffTarget.END,
            ),
        )
    }

    @Test
    fun `page reader forward chapter handoff starts from first page`() {
        assertEquals(
            0,
            resolveInitialPageReaderPage(
                savedPageReaderProgress = PageReaderProgress(index = 5, totalItems = 6),
                legacyLastSavedIndex = 5,
                pageCount = 7,
                chapterHandoffTarget = NovelReaderPageReaderHandoffTarget.START,
            ),
        )
    }

    @Test
    fun `page reader progress also normalizes native list restore when reopening in scroll mode`() {
        assertEquals(
            8,
            resolveInitialNativeReaderIndex(
                nativeLastSavedIndex = 0,
                savedPageReaderProgress = PageReaderProgress(index = 4, totalItems = 5),
                itemCount = 9,
            ),
        )
    }

    @Test
    fun `page reader progress roundtrip stays distinct from native and web progress`() {
        assertEquals(
            PageReaderProgress(index = 7, totalItems = 10),
            decodePageReaderProgress(
                encodePageReaderProgress(index = 7, totalItems = 10),
            ),
        )

        assertNull(decodePageReaderProgress(encodeNativeScrollProgress(index = 7, offsetPx = 420)))
        assertNull(decodePageReaderProgress(encodeWebScrollProgressPercent(42)))
    }

    @Test
    fun `reader webview javascript follows plugin request flag`() {
        assertFalse(shouldEnableJavaScriptInReaderWebView(pluginRequestsJavaScript = false))
        assertTrue(shouldEnableJavaScriptInReaderWebView(pluginRequestsJavaScript = true))
    }

    @Test
    fun `webview css keeps forced paragraph indent when explicit text alignment is set`() {
        val css = buildWebReaderCssText(
            fontFaceCss = "",
            paddingTop = 0,
            paddingBottom = 0,
            paddingHorizontal = 16,
            fontSizePx = 16,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingPx = 12,
            textAlignCss = "justify",
            firstLineIndentCss = "2em",
            textColorHex = "#111111",
            backgroundHex = "#FFFFFF",
            appearanceMode = NovelReaderAppearanceMode.THEME,
            backgroundTexture = NovelReaderBackgroundTexture.PAPER_GRAIN,
            oledEdgeGradient = false,
            backgroundImageUrl = null,
            fontFamilyName = null,
            customCss = "",
            textShadowCss = null,
            forceBoldText = false,
            forceItalicText = false,
        )

        assertTrue(css.contains("--an-reader-align: justify;"))
        assertTrue(css.contains("--an-reader-first-line-indent: 2em;"))
        assertTrue(css.contains("text-align: var(--an-reader-align) !important;"))
        assertTrue(css.contains("text-indent: var(--an-reader-first-line-indent) !important;"))
    }

    @Test
    fun `auto shadow color uses light shadow on dark background`() {
        val shadow = resolveAutoReaderShadowColor(
            customShadowColor = null,
            textColor = Color(0xFFEDEDED),
            backgroundColor = Color(0xFF121212),
        )

        assertTrue(shadow.luminance() > 0.5f)
    }

    @Test
    fun `auto shadow color uses dark shadow on light background`() {
        val shadow = resolveAutoReaderShadowColor(
            customShadowColor = null,
            textColor = Color(0xFF1A1A1A),
            backgroundColor = Color(0xFFFFFFFF),
        )

        assertTrue(shadow.luminance() < 0.5f)
    }

    @Test
    fun `webview css includes text-shadow when enabled`() {
        val textShadowCss = resolveWebReaderTextShadowCss(
            textShadowEnabled = true,
            textShadowColor = "",
            textShadowBlur = 4f,
            textShadowX = 0f,
            textShadowY = 1f,
            textColor = Color(0xFFEDEDED),
            backgroundColor = Color(0xFF121212),
        )
        val css = buildWebReaderCssText(
            fontFaceCss = "",
            paddingTop = 0,
            paddingBottom = 0,
            paddingHorizontal = 16,
            fontSizePx = 16,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingPx = 12,
            textAlignCss = null,
            firstLineIndentCss = null,
            textColorHex = "#EDEDED",
            backgroundHex = "#121212",
            appearanceMode = NovelReaderAppearanceMode.THEME,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            oledEdgeGradient = false,
            backgroundImageUrl = null,
            fontFamilyName = null,
            customCss = "",
            textShadowCss = textShadowCss,
            forceBoldText = false,
            forceItalicText = false,
        )

        assertTrue(textShadowCss != null)
        assertTrue(css.contains("--an-reader-text-shadow:"))
        assertTrue(css.contains("text-shadow: var(--an-reader-text-shadow) !important;"))
    }

    @Test
    fun `webview css omits text-shadow when disabled`() {
        val textShadowCss = resolveWebReaderTextShadowCss(
            textShadowEnabled = false,
            textShadowColor = "",
            textShadowBlur = 4f,
            textShadowX = 0f,
            textShadowY = 1f,
            textColor = Color(0xFFEDEDED),
            backgroundColor = Color(0xFF121212),
        )
        val css = buildWebReaderCssText(
            fontFaceCss = "",
            paddingTop = 0,
            paddingBottom = 0,
            paddingHorizontal = 16,
            fontSizePx = 16,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingPx = 12,
            textAlignCss = null,
            firstLineIndentCss = null,
            textColorHex = "#EDEDED",
            backgroundHex = "#121212",
            appearanceMode = NovelReaderAppearanceMode.THEME,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            oledEdgeGradient = false,
            backgroundImageUrl = null,
            fontFamilyName = null,
            customCss = "",
            textShadowCss = textShadowCss,
            forceBoldText = false,
            forceItalicText = false,
        )

        assertTrue(textShadowCss == null)
        assertFalse(css.contains("--an-reader-text-shadow:"))
        assertFalse(css.contains("text-shadow: var(--an-reader-text-shadow) !important;"))
    }

    @Test
    fun `custom imported font css uses local reader font url`() {
        val css = buildNovelReaderFontFaceCss(
            NovelReaderFontOption(
                id = "user:My Font.ttf",
                label = "My Font",
                source = NovelReaderFontSource.USER_IMPORTED,
                filePath = "/tmp/My Font.ttf",
            ),
        )

        assertTrue(css.contains("font-family: 'user:My Font.ttf';"))
        assertTrue(css.contains("https://reader-font.local/user/My%20Font.ttf"))
    }

    @Test
    fun `local asset font css uses android asset path`() {
        val css = buildNovelReaderFontFaceCss(
            NovelReaderFontOption(
                id = "local:VeronaGothic.ttf",
                label = "Verona Gothic",
                assetFileName = "VeronaGothic.ttf",
                assetPath = "local/fonts/VeronaGothic.ttf",
                source = NovelReaderFontSource.LOCAL_PRIVATE,
            ),
        )

        assertTrue(css.contains("file:///android_asset/local/fonts/VeronaGothic.ttf"))
    }

    @Test
    fun `webview css applies forced bold and italic to body`() {
        val css = buildWebReaderCssText(
            fontFaceCss = "",
            paddingTop = 0,
            paddingBottom = 0,
            paddingHorizontal = 16,
            fontSizePx = 16,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingPx = 12,
            textAlignCss = null,
            firstLineIndentCss = null,
            textColorHex = "#111111",
            backgroundHex = "#FFFFFF",
            appearanceMode = NovelReaderAppearanceMode.THEME,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            oledEdgeGradient = false,
            backgroundImageUrl = null,
            fontFamilyName = null,
            customCss = "",
            textShadowCss = null,
            forceBoldText = true,
            forceItalicText = true,
        )

        assertTrue(css.contains("--an-reader-font-weight: 700;"))
        assertTrue(css.contains("--an-reader-font-style: italic;"))
        assertTrue(css.contains("font-weight: var(--an-reader-font-weight) !important;"))
        assertTrue(css.contains("font-style: var(--an-reader-font-style) !important;"))
    }

    @Test
    fun `forced typeface style resolves combined style`() {
        assertEquals(android.graphics.Typeface.NORMAL, resolveForcedReaderTypefaceStyle(false, false))
        assertEquals(android.graphics.Typeface.BOLD, resolveForcedReaderTypefaceStyle(true, false))
        assertEquals(android.graphics.Typeface.ITALIC, resolveForcedReaderTypefaceStyle(false, true))
        assertEquals(android.graphics.Typeface.BOLD_ITALIC, resolveForcedReaderTypefaceStyle(true, true))
    }

    @Test
    fun `webview css uses explicit image layer in background mode`() {
        val css = buildWebReaderCssText(
            fontFaceCss = "",
            paddingTop = 0,
            paddingBottom = 0,
            paddingHorizontal = 16,
            fontSizePx = 16,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingPx = 12,
            textAlignCss = null,
            firstLineIndentCss = null,
            textColorHex = "#EDEDED",
            backgroundHex = "#121212",
            appearanceMode = NovelReaderAppearanceMode.BACKGROUND,
            backgroundTexture = NovelReaderBackgroundTexture.PAPER_GRAIN,
            oledEdgeGradient = true,
            backgroundImageUrl = "https://reader-background.local/preset/night_velvet",
            fontFamilyName = null,
            customCss = "",
            textShadowCss = null,
            forceBoldText = false,
            forceItalicText = false,
        )

        assertTrue(css.contains("url('https://reader-background.local/preset/night_velvet')"))
        assertFalse(css.contains("texture_paper.webp"))
    }

    @Test
    fun `webview css fingerprint changes when background identity changes`() {
        val first = buildWebReaderCssFingerprint(
            chapterId = 1L,
            paddingTop = 0,
            paddingBottom = 0,
            paddingHorizontal = 16,
            fontSizePx = 16,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingPx = 12,
            textAlignCss = null,
            firstLineIndentCss = null,
            textColorHex = "#111111",
            backgroundHex = "#FFFFFF",
            appearanceMode = NovelReaderAppearanceMode.BACKGROUND,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            oledEdgeGradient = false,
            backgroundImageIdentity = "preset:linen_paper",
            fontFamilyName = null,
            customCss = "",
            textShadowCss = null,
            forceBoldText = false,
            forceItalicText = false,
        )
        val second = buildWebReaderCssFingerprint(
            chapterId = 1L,
            paddingTop = 0,
            paddingBottom = 0,
            paddingHorizontal = 16,
            fontSizePx = 16,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingPx = 12,
            textAlignCss = null,
            firstLineIndentCss = null,
            textColorHex = "#111111",
            backgroundHex = "#FFFFFF",
            appearanceMode = NovelReaderAppearanceMode.BACKGROUND,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            oledEdgeGradient = false,
            backgroundImageIdentity = "preset:night_velvet",
            fontFamilyName = null,
            customCss = "",
            textShadowCss = null,
            forceBoldText = false,
            forceItalicText = false,
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `page edge shadow color is not pure black on dark background`() {
        val color = resolvePageEdgeShadowColor(
            pageEdgeShadowAlpha = 0.25f,
            backgroundColor = Color(0xFF121212),
        )

        assertTrue(color.luminance() > 0f)
    }

    @Test
    fun `reader color parser supports android argb format`() {
        val parsed = parseReaderColorForTest("#66000000")
        assertTrue(parsed != null)
        assertTrue(parsed!!.alpha > 0f)
    }

    @Test
    fun `reader color parser supports css rgba format`() {
        val parsed = parseReaderColorForTest("#00000066")
        assertTrue(parsed != null)
        assertTrue(parsed!!.alpha > 0f)
    }

    @Test
    fun `initial webview html injects reader and bootstrap styles into head`() {
        val html = "<html><head><title>t</title></head><body><p>Hello</p></body></html>"

        val result = buildInitialWebReaderHtml(
            rawHtml = html,
            readerCss = "body { color: red; }",
        )

        assertTrue(result.contains("__an_reader_style__"))
        assertTrue(result.contains("__an_reader_bootstrap_style__"))
        assertTrue(result.indexOf("__an_reader_style__") < result.indexOf("</head>"))
        assertTrue(result.contains("<p>Hello</p>"))
    }

    @Test
    fun `initial webview html escapes closing style sequences in css`() {
        val html = "<html><head></head><body></body></html>"

        val result = buildInitialWebReaderHtml(
            rawHtml = html,
            readerCss = "body { color: red; } </style><script>alert(1)</script>",
        )

        assertFalse(result.contains("</style><script>alert(1)</script>"))
        assertTrue(result.contains("<script>alert(1)</script>"))
    }

    @Test
    fun `early webview reveal is enabled for image-heavy html`() {
        val html = buildString {
            append("<html><body>")
            repeat(6) { index ->
                append("<img src=\"https://example.com/$index.jpg\" />")
            }
            append("</body></html>")
        }

        assertTrue(shouldUseEarlyWebViewReveal(rawHtml = html))
    }

    @Test
    fun `early webview reveal is enabled for hexnovels plugin images`() {
        val html = """
            <html><body>
            <img src="novelimg://hexnovels?ref=chapter%2Fimg-1" />
            </body></html>
        """.trimIndent()

        assertTrue(shouldUseEarlyWebViewReveal(rawHtml = html))
    }

    @Test
    fun `early webview reveal stays disabled for plain text html`() {
        val html = "<html><body><p>Chapter text only</p></body></html>"

        assertFalse(shouldUseEarlyWebViewReveal(rawHtml = html))
    }

    @Test
    fun `reader exit restores captured system bars state when available`() {
        val captured = ReaderSystemBarsState(
            isLightStatusBars = false,
            isLightNavigationBars = false,
            systemBarsBehavior = 7,
        )
        val current = ReaderSystemBarsState(
            isLightStatusBars = true,
            isLightNavigationBars = true,
            systemBarsBehavior = 3,
        )

        val restored = resolveReaderExitSystemBarsState(
            captured = captured,
            current = current,
        )

        assertTrue(restored == captured)
    }

    @Test
    fun `reader exit falls back to current system bars state when no snapshot was captured`() {
        val current = ReaderSystemBarsState(
            isLightStatusBars = true,
            isLightNavigationBars = false,
            systemBarsBehavior = 5,
        )

        val restored = resolveReaderExitSystemBarsState(
            captured = null,
            current = current,
        )

        assertTrue(restored == current)
    }

    @Test
    fun `reader active system bars force light icons in fullscreen immersive mode`() {
        val base = ReaderSystemBarsState(
            isLightStatusBars = true,
            isLightNavigationBars = true,
            systemBarsBehavior = 0,
        )

        val resolved = resolveActiveReaderSystemBarsState(
            showReaderUi = false,
            fullScreenMode = true,
            base = base,
        )

        assertFalse(resolved.isLightStatusBars)
        assertFalse(resolved.isLightNavigationBars)
        assertTrue(
            resolved.systemBarsBehavior ==
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }

    @Test
    fun `reader active system bars preserve base icon appearance when ui is visible`() {
        val base = ReaderSystemBarsState(
            isLightStatusBars = true,
            isLightNavigationBars = false,
            systemBarsBehavior = 9,
        )

        val resolved = resolveActiveReaderSystemBarsState(
            showReaderUi = true,
            fullScreenMode = true,
            base = base,
        )

        assertTrue(resolved.isLightStatusBars)
        assertFalse(resolved.isLightNavigationBars)
        assertTrue(
            resolved.systemBarsBehavior ==
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }

    @Test
    fun `reader active system bars preserve base icon appearance when not fullscreen`() {
        val base = ReaderSystemBarsState(
            isLightStatusBars = false,
            isLightNavigationBars = false,
            systemBarsBehavior = 9,
        )

        val resolved = resolveActiveReaderSystemBarsState(
            showReaderUi = false,
            fullScreenMode = false,
            base = base,
        )

        assertFalse(resolved.isLightStatusBars)
        assertFalse(resolved.isLightNavigationBars)
        assertTrue(
            resolved.systemBarsBehavior ==
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }

    @Test
    fun `rich segments are converted to annotated string styles and links`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(
                    text = "Bold",
                    style = NovelRichTextStyle(bold = true),
                ),
                NovelRichTextSegment(
                    text = " and ",
                ),
                NovelRichTextSegment(
                    text = "link",
                    style = NovelRichTextStyle(italic = true, underline = true),
                    linkUrl = "https://example.org",
                ),
            ),
        )

        assertTrue(annotated.text == "Bold and link")
        assertTrue(annotated.getStringAnnotations(tag = "URL", start = 0, end = annotated.length).size == 1)
        assertTrue(
            annotated.getStringAnnotations(tag = "URL", start = 0, end = annotated.length)
                .single().item == "https://example.org",
        )
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 4 })
        assertTrue(
            annotated.spanStyles.any {
                it.item.fontStyle == FontStyle.Italic &&
                    it.item.textDecoration == TextDecoration.Underline &&
                    it.start == 9 &&
                    it.end == 13
            },
        )
    }

    @Test
    fun `rich segments parse css text and background colors`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(
                    text = "C",
                    style = NovelRichTextStyle(
                        colorCss = "#112233",
                        backgroundColorCss = "#445566",
                    ),
                ),
            ),
        )

        val span = annotated.spanStyles.single()
        assertTrue(span.item.color == Color(0xFF112233))
        assertTrue(span.item.background == Color(0xFF445566))
    }

    @Test
    fun `page reader spannable builder preserves android spans and indent`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(
                    text = "Bold",
                    style = NovelRichTextStyle(
                        bold = true,
                        italic = true,
                        underline = true,
                        strikeThrough = true,
                        colorCss = "#112233",
                        backgroundColorCss = "#445566",
                    ),
                    linkUrl = "https://example.org",
                ),
            ),
        )

        val specs = buildNovelPageReaderSpanSpecs(
            text = annotated,
            firstLineIndentPx = 24,
        )

        val spanSpec = specs.single {
            it.start == 0 &&
                it.end == annotated.length &&
                it.leadingMarginPx == null
        }
        assertTrue(spanSpec.bold)
        assertTrue(spanSpec.italic)
        assertTrue(spanSpec.underline)
        assertTrue(spanSpec.strikeThrough)
        assertTrue(spanSpec.foregroundColor == Color(0xFF112233))
        assertTrue(spanSpec.backgroundColor == Color(0xFF445566))
        assertTrue(specs.single { it.leadingMarginPx != null }.leadingMarginPx == 24)
    }

    @Test
    fun `rich link helper resolves url annotation at char offset`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(text = "Hello "),
                NovelRichTextSegment(text = "link", linkUrl = "https://example.org"),
            ),
        )

        assertTrue(resolveNovelRichLinkAtCharOffset(annotated, 0) == null)
        assertTrue(resolveNovelRichLinkAtCharOffset(annotated, 6) == "https://example.org")
        assertTrue(resolveNovelRichLinkAtCharOffset(annotated, 9) == "https://example.org")
    }

    @Test
    fun `reader rich link resolver supports relative urls`() {
        assertTrue(
            resolveNovelReaderLinkUrl(
                rawUrl = "/chapter-2",
                chapterWebUrl = "https://example.org/novel/chapter-1",
                novelUrl = "https://example.org/novel",
            ) == "https://example.org/chapter-2",
        )
        assertTrue(
            resolveNovelReaderLinkUrl(
                rawUrl = "chapter-2",
                chapterWebUrl = "https://example.org/novel/chapter-1",
                novelUrl = "https://example.org/novel",
            ) == "https://example.org/novel/chapter-2",
        )
    }

    @Test
    fun `page range paginator matches page text paginator output`() {
        val text = (1..120).joinToString(" ") { "word$it" }

        val pages = paginateTextIntoPages(
            text = text,
            widthPx = 240,
            heightPx = 120,
            textSizePx = 32f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.LEFT,
        )
        val pageRanges = paginateTextIntoPageRanges(
            text = text,
            widthPx = 240,
            heightPx = 120,
            textSizePx = 32f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.LEFT,
        )

        val pagesFromRanges = pageRanges.map { range ->
            text.substring(range.start, range.endExclusive).trim()
        }
        assertTrue(pagesFromRanges == pages)
    }

    @Test
    fun `annotated page paginator preserves url annotations`() {
        val annotated = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(text = "Alpha "),
                NovelRichTextSegment(text = "link", linkUrl = "https://example.org"),
                NovelRichTextSegment(text = " omega ".repeat(80)),
            ),
        )

        val pages = paginateAnnotatedTextIntoPages(
            text = annotated,
            widthPx = 240,
            heightPx = 120,
            textSizePx = 32f,
            lineHeightMultiplier = 1.2f,
            typeface = null,
            textAlign = eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.LEFT,
        )

        assertTrue(pages.isNotEmpty())
        val hasUrlAnnotation = pages.any { page ->
            page.getStringAnnotations(tag = "URL", start = 0, end = page.length).isNotEmpty()
        }
        assertTrue(hasUrlAnnotation)
    }

    @Test
    fun `rich page block builder preserves first-line indent metadata`() {
        val block = buildRichPageReaderBlockText(
            block = NovelRichContentBlock.Paragraph(
                segments = listOf(NovelRichTextSegment(text = "Indented paragraph")),
                firstLineIndentEm = 2f,
            ),
        )

        assertEquals("Indented paragraph", block.text.text)
        assertEquals(2f, block.firstLineIndentEm)
    }

    @Test
    fun `rich page block builder preserves source text alignment metadata`() {
        val block = buildRichPageReaderBlockText(
            block = NovelRichContentBlock.BlockQuote(
                segments = listOf(NovelRichTextSegment(text = "Centered quote")),
                textAlign = NovelRichBlockTextAlign.CENTER,
            ),
        )

        assertEquals("Centered quote", block.text.text)
        assertEquals(NovelRichBlockTextAlign.CENTER, block.sourceTextAlign)
    }

    @Test
    fun `page reader base text style applies forced bold italic and shadow`() {
        val style = resolvePageReaderBaseTextStyle(
            baseStyle = TextStyle.Default,
            color = Color.Black,
            backgroundColor = Color.White,
            fontSize = 22,
            lineHeight = 1.4f,
            fontFamily = null,
            textAlign = TextAlign.Center,
            forceBoldText = true,
            forceItalicText = true,
            textShadow = true,
            textShadowColor = "",
            textShadowBlur = 3f,
            textShadowX = 2f,
            textShadowY = 1f,
        )

        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(FontStyle.Italic, style.fontStyle)
        assertEquals(TextAlign.Center, style.textAlign)
        assertEquals(3f, style.shadow?.blurRadius)
        assertEquals(Offset(2f, 1f), style.shadow?.offset)
        assertEquals(PlatformTextStyle(includeFontPadding = false), style.platformStyle)
    }

    @Test
    fun `rich page builder uses slider-based paragraph separator`() {
        val chapter = buildRichPageReaderChapterAnnotatedText(
            listOf(
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "First paragraph")),
                ),
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Second paragraph")),
                ),
            ),
            paragraphSpacingDp = 24,
        )

        assertTrue(chapter.text == "First paragraph\n\nSecond paragraph")
    }

    @Test
    fun `webview css uses requested paragraph spacing value`() {
        val css = buildWebReaderCssText(
            fontFaceCss = "",
            paddingTop = 0,
            paddingBottom = 0,
            paddingHorizontal = 16,
            fontSizePx = 16,
            lineHeightMultiplier = 1.6f,
            paragraphSpacingPx = 18,
            textAlignCss = null,
            firstLineIndentCss = null,
            textColorHex = "#111111",
            backgroundHex = "#FFFFFF",
            appearanceMode = NovelReaderAppearanceMode.THEME,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            oledEdgeGradient = false,
            backgroundImageUrl = null,
            fontFamilyName = null,
            customCss = "",
            textShadowCss = null,
            forceBoldText = false,
            forceItalicText = false,
        )

        assertTrue(css.contains("margin-bottom: 18px !important;"))
    }

    @Test
    fun `webview bionic script does not override selected font family`() {
        val script = buildWebReaderBionicJavascript(enabled = true)

        assertTrue(script.contains("an-reader-bionic"))
        assertFalse(script.contains("font-family"))
    }

    @Test
    fun `webview bionic script is empty when disabled`() {
        assertTrue(buildWebReaderBionicJavascript(enabled = false).isBlank())
    }
}
