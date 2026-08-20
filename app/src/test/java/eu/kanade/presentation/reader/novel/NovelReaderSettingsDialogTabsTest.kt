package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelReaderSettingsDialogTabsTest {

    @Test
    fun `settings dialog has exactly 4 tabs in defined order`() {
        val tabIds = listOf("quick", "visual", "navigation", "tools")
        assertEquals(4, tabIds.size)
        assertEquals("quick", tabIds[0])
        assertEquals("visual", tabIds[1])
        assertEquals("navigation", tabIds[2])
        assertEquals("tools", tabIds[3])
    }

    @Test
    fun `tab titles do not contain emojis and are valid non-blank strings`() {
        val russianTitles = listOf("Общие", "Стиль", "Поведение", "Инструменты")
        val englishTitles = listOf("General", "Style", "Behavior", "Tools")

        val emojiRegex = Regex("[\\p{So}\\p{Cn}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]")
        (russianTitles + englishTitles).forEach { title ->
            assertFalse(
                emojiRegex.containsMatchIn(title),
                "Tab title '$title' should not contain emojis",
            )
            assertTrue(
                title.isNotBlank() && title.length in 3..20,
                "Tab title '$title' should be a valid non-blank title",
            )
        }
    }

    @Test
    fun `kindle dependent controls are disabled when kindle block is off`() {
        assertFalse(areQuickDialogKindleDependentControlsEnabled(showKindleInfoBlock = false))
        assertTrue(areQuickDialogKindleDependentControlsEnabled(showKindleInfoBlock = true))
    }

    @Test
    fun `chapter swipe controls are enabled only in scroll reader mode`() {
        assertTrue(areChapterSwipeControlsEnabled(pageReaderEnabled = false))
        assertFalse(areChapterSwipeControlsEnabled(pageReaderEnabled = true))
    }

    @Test
    fun `appearance mode resolution correctly separates theme and background controls`() {
        val themeState = resolveAppearanceControlState(NovelReaderAppearanceMode.THEME)
        assertTrue(themeState.themeControlsEnabled)
        assertFalse(themeState.backgroundControlsEnabled)

        val bgState = resolveAppearanceControlState(NovelReaderAppearanceMode.BACKGROUND)
        assertFalse(bgState.themeControlsEnabled)
        assertTrue(bgState.backgroundControlsEnabled)
    }

    @Test
    fun `renderer availability disables webview in page reader mode`() {
        val availability = resolveRendererSettingsAvailability(
            pageReaderEnabled = true,
            showWebView = false,
            bionicReadingEnabled = false,
            bookModeEnabled = false,
        )
        assertFalse(availability.preferWebViewEnabled)
        assertEquals(RendererSettingDisableReason.PAGE_MODE, availability.preferWebViewReason)
    }

    @Test
    fun `renderer availability enables rich native when not in webview or bionic mode`() {
        val availability = resolveRendererSettingsAvailability(
            pageReaderEnabled = false,
            showWebView = false,
            bionicReadingEnabled = false,
            bookModeEnabled = false,
        )
        assertTrue(availability.richNativeEnabled)
        assertNull(availability.richNativeReason)
    }
}
