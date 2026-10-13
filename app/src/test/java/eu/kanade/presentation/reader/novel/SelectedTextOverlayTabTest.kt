package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectedTextOverlayTabTest {

    private fun createSelection(action: SelectedTextAction?): NovelSelectedTextSelection {
        return NovelSelectedTextSelection(
            sessionId = 1L,
            renderer = NovelSelectedTextRenderer.PAGE_READER,
            text = "example",
            anchor = NovelSelectedTextAnchor(0, 0, 10, 10),
            triggerAction = action,
        )
    }

    @Test
    fun `translate action opens translation tab when translation is enabled`() {
        val selection = createSelection(SelectedTextAction.TRANSLATION)
        val tab = resolveSelectedTextOverlayInitialTab(
            selection = selection,
            showTranslation = true,
            showDictionary = true,
        )
        assertEquals(SelectedTextOverlayTab.TRANSLATION, tab)
    }

    @Test
    fun `dictionary action opens dictionary tab when dictionary is enabled`() {
        val selection = createSelection(SelectedTextAction.DICTIONARY)
        val tab = resolveSelectedTextOverlayInitialTab(
            selection = selection,
            showTranslation = true,
            showDictionary = true,
        )
        assertEquals(SelectedTextOverlayTab.DICTIONARY, tab)
    }

    @Test
    fun `translate action falls back to dictionary if translation is disabled`() {
        val selection = createSelection(SelectedTextAction.TRANSLATION)
        val tab = resolveSelectedTextOverlayInitialTab(
            selection = selection,
            showTranslation = false,
            showDictionary = true,
        )
        assertEquals(SelectedTextOverlayTab.DICTIONARY, tab)
    }

    @Test
    fun `dictionary action falls back to translation if dictionary is disabled`() {
        val selection = createSelection(SelectedTextAction.DICTIONARY)
        val tab = resolveSelectedTextOverlayInitialTab(
            selection = selection,
            showTranslation = true,
            showDictionary = false,
        )
        assertEquals(SelectedTextOverlayTab.TRANSLATION, tab)
    }

    @Test
    fun `selection without trigger action defaults to dictionary when both enabled`() {
        val selection = createSelection(null)
        val tab = resolveSelectedTextOverlayInitialTab(
            selection = selection,
            showTranslation = true,
            showDictionary = true,
        )
        assertEquals(SelectedTextOverlayTab.DICTIONARY, tab)
    }
}
