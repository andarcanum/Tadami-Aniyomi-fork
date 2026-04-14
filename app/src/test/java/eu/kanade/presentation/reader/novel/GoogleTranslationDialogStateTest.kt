package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleTranslationDialogStateTest {

    @Test
    fun `translation phase enum has expected values`() {
        assertEquals(2, TranslationPhase.entries.size)
        assertTrue(TranslationPhase.entries.contains(TranslationPhase.TRANSLATING))
        assertTrue(TranslationPhase.entries.contains(TranslationPhase.IDLE))
    }

    @Test
    fun `sync keeps local toggle draft while upstream value has not changed`() {
        val state = syncGoogleTranslationToggleDraft(
            committedValue = false,
            previousCommittedValue = false,
            currentDraftValue = true,
        )

        assertEquals(false, state.committedValue)
        assertEquals(true, state.draftValue)
    }

    @Test
    fun `sync resets local toggle draft when upstream value changes`() {
        val state = syncGoogleTranslationToggleDraft(
            committedValue = true,
            previousCommittedValue = false,
            currentDraftValue = false,
        )

        assertEquals(true, state.committedValue)
        assertEquals(true, state.draftValue)
    }
}
