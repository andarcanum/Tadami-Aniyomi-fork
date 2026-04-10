package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

class NovelPageTurnSnapshotRendererTest {

    @Test
    fun `snapshot key changes when page size changes`() {
        val base = sampleSnapshotKey(pageSize = IntSize(800, 1200))
        val resized = sampleSnapshotKey(pageSize = IntSize(1080, 1200))

        assertNotEquals(base, resized)
    }

    @Test
    fun `snapshot key changes when typography or texture changes`() {
        val base = sampleSnapshotKey(
            fontFamilyKey = "font-a",
            backgroundTexture = NovelReaderBackgroundTexture.PARCHMENT,
        )
        val differentFont = sampleSnapshotKey(
            fontFamilyKey = "font-b",
            backgroundTexture = NovelReaderBackgroundTexture.PARCHMENT,
        )
        val differentTexture = sampleSnapshotKey(
            fontFamilyKey = "font-a",
            backgroundTexture = NovelReaderBackgroundTexture.LINEN,
        )

        assertNotEquals(base, differentFont)
        assertNotEquals(base, differentTexture)
    }

    @Test
    fun `snapshot key changes when theme colors change`() {
        val base = sampleSnapshotKey(
            textBackground = Color(0xFFF0E2C7),
            pageSurfaceColor = Color(0xFFF0E2C7),
        )
        val differentBackground = sampleSnapshotKey(
            textBackground = Color(0xFF2B2419),
            pageSurfaceColor = Color(0xFF2B2419),
        )

        assertNotEquals(base, differentBackground)
    }

    @Test
    fun `snapshot key changes when background mode identity changes`() {
        val base = sampleSnapshotKey(
            pageSurfaceColor = Color.Transparent,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            backgroundImageIdentity = "preset:linen_paper",
            isBackgroundMode = true,
        )
        val differentIdentity = sampleSnapshotKey(
            pageSurfaceColor = Color.Transparent,
            backgroundTexture = NovelReaderBackgroundTexture.NONE,
            backgroundImageIdentity = "custom:chapter-2",
            isBackgroundMode = true,
        )

        assertNotEquals(base, differentIdentity)
    }

    @Test
    fun `snapshot cache evicts least recently used entries`() {
        val cache = NovelPageTurnSnapshotCache<String>(maxSize = 2)
        val first = sampleSnapshotKey(pageContentHash = 1)
        val second = sampleSnapshotKey(pageContentHash = 2)
        val third = sampleSnapshotKey(pageContentHash = 3)

        cache.put(first, "first")
        cache.put(second, "second")
        assertEquals("first", cache[first])

        cache.put(third, "third")

        assertEquals("first", cache[first])
        assertNull(cache[second])
        assertEquals("third", cache[third])
    }

    @Test
    fun `snapshot key changes when page content changes`() {
        val base = sampleSnapshotKey(pageContentHash = samplePage("Alpha").hashCode())
        val differentContent = sampleSnapshotKey(pageContentHash = samplePage("Beta").hashCode())

        assertNotEquals(base, differentContent)
    }

    private fun sampleSnapshotKey(
        pageSize: IntSize = IntSize(800, 1200),
        pageContentHash: Int = samplePage().hashCode(),
        fontFamilyKey: String = "font-a",
        chapterTitleFontFamilyKey: String = "title-font",
        chapterTitleTextColor: Color = Color(0xFF2A2A2A),
        textColor: Color = Color(0xFF111111),
        textBackground: Color = Color(0xFFF0E2C7),
        pageSurfaceColor: Color = Color(0xFFF0E2C7),
        backgroundTexture: NovelReaderBackgroundTexture = NovelReaderBackgroundTexture.PAPER_GRAIN,
        backgroundImageIdentity: String = "",
        isBackgroundMode: Boolean = false,
    ): NovelPageTurnSnapshotKey {
        return resolveNovelPageTurnSnapshotKey(
            style = NovelPageTransitionStyle.BOOK,
            pageIndex = 0,
            pageCount = 12,
            pageContentHash = pageContentHash,
            pageSize = pageSize,
            fontFamilyKey = fontFamilyKey,
            chapterTitleFontFamilyKey = chapterTitleFontFamilyKey,
            chapterTitleTextColor = chapterTitleTextColor,
            fontSize = 24,
            lineHeight = 1.45f,
            margin = 18,
            contentPaddingPx = 24,
            statusBarTopPaddingPx = 16,
            textAlign = ReaderTextAlign.JUSTIFY,
            textColor = textColor,
            textBackground = textBackground,
            pageSurfaceColor = pageSurfaceColor,
            isBackgroundMode = isBackgroundMode,
            backgroundImageIdentity = backgroundImageIdentity,
            backgroundTextureName = backgroundTexture.name,
            nativeTextureStrengthPercentEffective = 80,
            oledEdgeGradient = false,
            isDarkTheme = false,
            backgroundTexture = backgroundTexture,
            nativeTextureStrengthPercent = 80,
            forceBoldText = false,
            forceItalicText = false,
            textShadow = true,
            textShadowColor = "#1a1a1a",
            textShadowBlur = 2.5f,
            textShadowX = 2f,
            textShadowY = 1f,
            bionicReading = false,
        )
    }

    private fun samplePage(text: String = "Alpha"): NovelPageContentPage {
        return NovelPageContentPage(
            blocks = listOf(
                NovelPageContentBlock.Plain(
                    sourceBlockIndex = 0,
                    text = text,
                    spacingBeforePx = 0,
                    firstLineIndentEm = null,
                ),
            ),
        )
    }
}
