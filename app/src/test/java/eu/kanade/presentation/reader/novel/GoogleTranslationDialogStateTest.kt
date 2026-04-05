package eu.kanade.presentation.reader.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleTranslationDialogStateTest {

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
}
