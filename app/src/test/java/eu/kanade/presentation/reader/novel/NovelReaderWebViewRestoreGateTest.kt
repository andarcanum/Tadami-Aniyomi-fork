package eu.kanade.presentation.reader.novel

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class NovelReaderWebViewRestoreGateTest {

    @Test
    fun `restore gate opens when restore succeeds`() {
        assertFalse(resolveWebViewRestoreGate(restoreSucceeded = true))
    }

    /**
     * Regression guard for the scroll-restore latch: a failed restore used to
     * store `!restored = true` back into `shouldRestoreWebScroll`, which kept
     * both restore sites and `shouldTrackWebViewProgress` treating the restore
     * as in-flight forever — live progress tracking and auto-scroll near-end
     * detection stayed disabled for the whole chapter session.
     *
     * A finished restore is never in flight anymore, so the stored gate value
     * must be false regardless of the outcome.
     */
    @Test
    fun `restore gate opens when restore fails so tracking resumes`() {
        assertFalse(resolveWebViewRestoreGate(restoreSucceeded = false))
    }
}
