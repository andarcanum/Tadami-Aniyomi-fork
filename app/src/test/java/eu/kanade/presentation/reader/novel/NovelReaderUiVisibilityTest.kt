package eu.kanade.presentation.reader.novel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import androidx.core.view.WindowInsetsControllerCompat
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

class NovelReaderUiVisibilityTest {

    @Test
    fun `background preset catalog exposes five unique built-ins`() {
        assertTrue(novelReaderBackgroundPresets.size == 5)
        assertTrue(novelReaderBackgroundPresets.map { it.id }.toSet().size == 5)
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

        assertTrue(cards.size == 7)
        assertTrue(cards.take(5).all { it.isBuiltIn })
        assertTrue(cards.drop(5).all { !it.isBuiltIn })
        assertTrue(cards.drop(5).map { it.id } == listOf("custom-1", "custom-2"))
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
                textBlocksCount = 10,
            ),
        )

        assertFalse(
            shouldShowVerticalSeekbar(
                showReaderUi = false,
                verticalSeekbarEnabled = true,
                showWebView = false,
                textBlocksCount = 10,
            ),
        )

        assertFalse(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = true,
                textBlocksCount = 1,
            ),
        )
        assertTrue(
            shouldShowVerticalSeekbar(
                showReaderUi = true,
                verticalSeekbarEnabled = true,
                showWebView = true,
                textBlocksCount = 10,
            ),
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
    fun `fullscreen reader restores system bars on dispose`() {
        assertTrue(shouldRestoreSystemBarsOnDispose(fullScreenMode = true))
        assertTrue(shouldRestoreSystemBarsOnDispose(fullScreenMode = false))
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
        ) {
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
        ) { chapterText ->
            paginatedInput = chapterText
            listOf(chapterText)
        }

        assertTrue(paginatedInput == "First paragraph\n\nSecond paragraph")
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
        assertFalse(pageModeState.richNativeEnabled)
        assertTrue(pageModeState.richNativeReason == RendererSettingDisableReason.PAGE_MODE)

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
    fun `settings surface strategy exposes ownership summaries`() {
        val strategy = resolveNovelReaderSettingsSurfaceStrategy()

        assertTrue(NovelReaderSettingsFamily.SOURCE_ALIGNMENT_POLICY in strategy.globalOnlyFamilies)
        assertTrue(NovelReaderSettingsFamily.CHAPTER_CACHE_POLICY in strategy.globalOnlyFamilies)
        assertTrue(NovelReaderSettingsFamily.LIVE_TEXT_STYLING in strategy.quickDialogOnlyFamilies)
        assertTrue(NovelReaderSettingsFamily.RENDERER_TUNING in strategy.quickDialogOnlyFamilies)
    }

    @Test
    fun `native text align uses source alignment when preserve is enabled`() {
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
            ) == null,
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
    fun `page reader layout align avoids forced justify when preserve is enabled`() {
        assertTrue(
            resolvePageReaderLayoutTextAlign(
                globalTextAlign = ReaderTextAlign.JUSTIFY,
                preserveSourceTextAlignInNative = true,
            ) == ReaderTextAlign.LEFT,
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
            defaultLightStatusBars = true,
        )

        assertFalse(resolved.isLightStatusBars)
        assertFalse(resolved.isLightNavigationBars)
        assertTrue(
            resolved.systemBarsBehavior ==
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }

    @Test
    fun `reader active system bars keep base icon appearance when ui is visible`() {
        val base = ReaderSystemBarsState(
            isLightStatusBars = true,
            isLightNavigationBars = false,
            systemBarsBehavior = 9,
        )

        val resolved = resolveActiveReaderSystemBarsState(
            showReaderUi = true,
            fullScreenMode = true,
            base = base,
            defaultLightStatusBars = false,
        )

        assertFalse(resolved.isLightStatusBars)
        assertFalse(resolved.isLightNavigationBars)
        assertTrue(
            resolved.systemBarsBehavior ==
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }

    @Test
    fun `reader active system bars keep base icon appearance when not fullscreen`() {
        val base = ReaderSystemBarsState(
            isLightStatusBars = false,
            isLightNavigationBars = false,
            systemBarsBehavior = 9,
        )

        val resolved = resolveActiveReaderSystemBarsState(
            showReaderUi = false,
            fullScreenMode = false,
            base = base,
            defaultLightStatusBars = true,
        )

        assertTrue(resolved.isLightStatusBars)
        assertTrue(resolved.isLightNavigationBars)
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
    fun `rich page builder preserves first-line indent as paragraph style`() {
        val chapter = buildRichPageReaderChapterAnnotatedText(
            listOf(
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "Indented paragraph")),
                    firstLineIndentEm = 2f,
                ),
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment(text = "No indent paragraph")),
                    firstLineIndentEm = null,
                ),
            ),
            paragraphSpacingDp = 12,
        )

        val hasIndent = chapter.paragraphStyles.any { range ->
            range.item.textIndent == TextIndent(firstLine = 2.em)
        }
        assertTrue(hasIndent)
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
