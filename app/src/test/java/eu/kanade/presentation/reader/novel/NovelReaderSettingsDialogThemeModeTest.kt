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

    @Test
    fun `deleting selected custom background resolves to another custom selection`() {
        val result = resolveCustomBackgroundDeletion(
            selectedId = "custom-2",
            deletedId = "custom-2",
            remainingCustomIds = listOf("custom-1"),
            fallbackPresetId = NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID,
        )

        assertEquals("custom-1", result.nextCustomId)
        assertTrue(result.keepCustomSource)
    }

    @Test
    fun `deleting last custom background falls back to preset mode`() {
        val result = resolveCustomBackgroundDeletion(
            selectedId = "custom-1",
            deletedId = "custom-1",
            remainingCustomIds = emptyList(),
            fallbackPresetId = NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID,
        )

        assertEquals("", result.nextCustomId)
        assertFalse(result.keepCustomSource)
    }

    @Test
    fun `replacing selected custom background keeps selected id`() {
        val nextSelectedId = resolveCustomBackgroundReplacement(
            selectedId = "custom-2",
            replacedId = "custom-2",
        )

        assertEquals("custom-2", nextSelectedId)
    }
}
