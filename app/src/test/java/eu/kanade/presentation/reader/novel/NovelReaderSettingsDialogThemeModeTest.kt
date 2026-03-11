package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelReaderSettingsDialogThemeModeTest {

    @Test
    fun `theme mode selection clears explicit colors for system fallback`() {
        NovelReaderTheme.entries.forEach { mode ->
            val selection = resolveThemeModeSelection(mode)
            assertEquals(mode, selection.theme)
            assertEquals("", selection.backgroundColor)
            assertEquals("", selection.textColor)
        }
    }

    @Test
    fun `theme appearance mode enables theme controls only`() {
        val state = resolveAppearanceControlState(NovelReaderAppearanceMode.THEME)
        assertTrue(state.themeControlsEnabled)
        assertFalse(state.backgroundControlsEnabled)
    }

    @Test
    fun `background appearance mode enables background controls only`() {
        val state = resolveAppearanceControlState(NovelReaderAppearanceMode.BACKGROUND)
        assertFalse(state.themeControlsEnabled)
        assertTrue(state.backgroundControlsEnabled)
    }
}
