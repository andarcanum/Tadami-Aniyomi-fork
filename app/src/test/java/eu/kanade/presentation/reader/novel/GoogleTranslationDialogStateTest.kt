package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val PREPARING_MODEL_STRING = "Preparing translation model…"

class GoogleTranslationDialogStateTest {

    @Test
    fun `translation phase enum has expected values`() {
        assertEquals(3, TranslationPhase.entries.size)
        assertTrue(TranslationPhase.entries.contains(TranslationPhase.PREPARING_MODEL))
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

    @Test
    fun `language suggestions match english aliases in latin and cyrillic`() {
        val englishByLatin = googleTranslationLanguageSuggestions("Eng")
        val englishByCyrillic = googleTranslationLanguageSuggestions("Англ")

        assertTrue(englishByLatin.any { it.canonicalName == "English" })
        assertTrue(englishByCyrillic.any { it.canonicalName == "English" })
    }

    @Test
    fun `language suggestions match russian alias`() {
        val russianSuggestions = googleTranslationLanguageSuggestions("рус")

        assertTrue(russianSuggestions.any { it.canonicalName == "Russian" })
    }

    @Test
    fun `preparing model phase string is non-empty`() {
        assertTrue(PREPARING_MODEL_STRING.isNotEmpty())
    }
}
