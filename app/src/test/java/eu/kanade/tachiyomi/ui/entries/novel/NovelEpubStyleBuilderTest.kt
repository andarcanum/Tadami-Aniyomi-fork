package eu.kanade.tachiyomi.ui.entries.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel

class NovelEpubStyleBuilderTest {

    @Test
    fun `buildStylesheet returns null when no style options enabled`() {
        val settings = defaultSettings()

        NovelEpubStyleBuilder.buildStylesheet(
            settings = settings,
            sourceId = 123L,
            applyReaderTheme = false,
            includeCustomCss = false,
            linkColor = "#FF00AA",
        ).shouldBeNull()
    }

    @Test
    fun `buildStylesheet includes reader theme and custom css`() {
        val settings = defaultSettings(
            customCSS = "#sourceId-123 { color: red; }",
            fontFamily = "Noto Sans",
        )

        val stylesheet = NovelEpubStyleBuilder.buildStylesheet(
            settings = settings,
            sourceId = 123L,
            applyReaderTheme = true,
            includeCustomCss = true,
            linkColor = "#33AAFF",
        )

        stylesheet.shouldContain("font-size: 18px")
        stylesheet.shouldContain("line-height: 1.7")
        stylesheet.shouldContain("text-align: justify")
        stylesheet.shouldContain("background-color: #101010")
        stylesheet.shouldContain("color: #EFEFEF")
        stylesheet.shouldContain("font-family: \"Noto Sans\"")
        stylesheet.shouldContain("a {")
        stylesheet.shouldContain("#33AAFF")
        stylesheet.shouldContain("body { color: red; }")
    }

    @Test
    fun `buildJavaScript returns null when custom js disabled`() {
        val settings = defaultSettings(customJS = "window.test = true;")
        val novel = Novel.create().copy(id = 77L, source = 123L, title = "Novel")

        NovelEpubStyleBuilder.buildJavaScript(
            settings = settings,
            novel = novel,
            includeCustomJs = false,
        ).shouldBeNull()
    }

    @Test
    fun `buildJavaScript includes metadata and custom js when enabled`() {
        val settings = defaultSettings(customJS = "window.test = true;")
        val novel = Novel.create().copy(id = 77L, source = 123L, title = "A\"B")

        val js = NovelEpubStyleBuilder.buildJavaScript(
            settings = settings,
            novel = novel,
            includeCustomJs = true,
        )

        js.shouldContain("let novelName = \"A\\\"B\";")
        js.shouldContain("let sourceId = 123;")
        js.shouldContain("let novelId = 77;")
        js.shouldContain("window.test = true;")
    }

    @Test
    fun `resolveThemeColors uses explicit colors over theme defaults`() {
        val settings = defaultSettings(
            theme = NovelReaderTheme.LIGHT,
            backgroundColor = "#202020",
            textColor = "#CDCDCD",
        )

        val colors = NovelEpubStyleBuilder.resolveThemeColors(settings)
        colors.background shouldBe "#202020"
        colors.text shouldBe "#CDCDCD"
    }

    private fun defaultSettings(
        fontSize: Int = 18,
        lineHeight: Float = 1.7f,
        margin: Int = 20,
        textAlign: TextAlign = TextAlign.JUSTIFY,
        fontFamily: String = "",
        forceBoldText: Boolean = false,
        forceItalicText: Boolean = false,
        textShadow: Boolean = false,
        textShadowColor: String = "",
        textShadowBlur: Float = 4f,
        textShadowX: Float = 0f,
        textShadowY: Float = 0f,
        pageEdgeShadow: Boolean = false,
        pageEdgeShadowAlpha: Float = 0.25f,
        theme: NovelReaderTheme = NovelReaderTheme.DARK,
        backgroundColor: String = "#101010",
        textColor: String = "#EFEFEF",
        customCSS: String = "",
        customJS: String = "",
    ): NovelReaderSettings {
        return NovelReaderSettings(
            fontSize = fontSize,
            lineHeight = lineHeight,
            margin = margin,
            textAlign = textAlign,
            paragraphSpacing = 12,
            forceParagraphIndent = false,
            preserveSourceTextAlignInNative = true,
            fontFamily = fontFamily,
            forceBoldText = forceBoldText,
            forceItalicText = forceItalicText,
            textShadow = textShadow,
            textShadowColor = textShadowColor,
            textShadowBlur = textShadowBlur,
            textShadowX = textShadowX,
            textShadowY = textShadowY,
            pageEdgeShadow = pageEdgeShadow,
            pageEdgeShadowAlpha = pageEdgeShadowAlpha,
            theme = theme,
            backgroundColor = backgroundColor,
            textColor = textColor,
            backgroundTexture = NovelReaderBackgroundTexture.PAPER_GRAIN,
            nativeTextureStrengthPercent = 50,
            appearanceMode = NovelReaderAppearanceMode.THEME,
            backgroundSource = NovelReaderBackgroundSource.PRESET,
            backgroundPresetId = "linen_paper",
            customBackgroundPath = "",
            oledEdgeGradient = true,
            customThemes = listOf(
                NovelReaderColorTheme(backgroundColor = "#101010", textColor = "#EFEFEF"),
            ),
            useVolumeButtons = false,
            swipeGestures = false,
            pageReader = false,
            preferWebViewRenderer = true,
            richNativeRendererExperimental = false,
            verticalSeekbar = true,
            swipeToNextChapter = false,
            swipeToPrevChapter = false,
            tapToScroll = false,
            autoScroll = false,
            autoScrollInterval = 10,
            autoScrollOffset = 0,
            prefetchNextChapter = false,
            fullScreenMode = true,
            keepScreenOn = false,
            showScrollPercentage = true,
            showBatteryAndTime = false,
            showKindleInfoBlock = true,
            showTimeToEnd = true,
            showWordCount = true,
            bionicReading = false,
            customCSS = customCSS,
            customJS = customJS,
        )
    }
}
