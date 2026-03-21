package eu.kanade.presentation.reader.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NovelReaderSettingsDialogSliderStateTest {

    @Test
    fun `sync keeps draft while committed value is unchanged`() {
        val state = syncLnReaderSliderDraft(
            committedValue = 18f,
            previousCommittedValue = 18f,
            currentDraftValue = 22f,
        )

        assertEquals(18f, state.committedValue)
        assertEquals(22f, state.draftValue)
    }

    @Test
    fun `sync resets draft when committed value changes upstream`() {
        val state = syncLnReaderSliderDraft(
            committedValue = 20f,
            previousCommittedValue = 18f,
            currentDraftValue = 22f,
        )

        assertEquals(20f, state.committedValue)
        assertEquals(20f, state.draftValue)
    }

    @Test
    fun `commit returns null when draft matches committed value`() {
        assertNull(resolveLnReaderSliderCommitValue(committedValue = 18f, draftValue = 18f))
    }

    @Test
    fun `commit returns draft when slider changed`() {
        assertEquals(22f, resolveLnReaderSliderCommitValue(committedValue = 18f, draftValue = 22f))
    }
}
