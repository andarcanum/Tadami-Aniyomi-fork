package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * The WebView book/chapter engine must never leave the composition in the same frame the renderer
 * decision flips: removing a focused AndroidView makes Android re-focus the window root, and that
 * cascade re-enters Compose layout inside `applyChanges`
 * ("Cannot start a writer when another writer is pending", issuetracker 507508113). The unmount is
 * therefore split: prepare (strip focus, stay mounted) -> one frame -> commit.
 */
class NovelReaderInteropUnmountPhaseTest {

    @Test
    fun `mounted and staying mounted is a no-op`() {
        val decision = resolveInteropUnmountDecision(
            unmountRequested = false,
            unmounted = false,
            prepared = false,
        )

        decision.unmounted shouldBe false
        decision.runPrepare shouldBe false
        decision.runCancelPrepare shouldBe false
    }

    @Test
    fun `the first unmount frame prepares instead of removing`() {
        val decision = resolveInteropUnmountDecision(
            unmountRequested = true,
            unmounted = false,
            prepared = false,
        )

        decision.unmounted shouldBe false
        decision.runPrepare shouldBe true
        decision.runCancelPrepare shouldBe false
    }

    @Test
    fun `after the prepared frame the unmount commits`() {
        val decision = resolveInteropUnmountDecision(
            unmountRequested = true,
            unmounted = false,
            prepared = true,
        )

        decision.unmounted shouldBe true
        decision.runPrepare shouldBe false
        decision.runCancelPrepare shouldBe false
    }

    @Test
    fun `already unmounted stays unmounted`() {
        val decision = resolveInteropUnmountDecision(
            unmountRequested = true,
            unmounted = true,
            prepared = false,
        )

        decision.unmounted shouldBe true
        decision.runPrepare shouldBe false
        decision.runCancelPrepare shouldBe false
    }

    @Test
    fun `remounting is immediate - adding a view never re-enters layout`() {
        val decision = resolveInteropUnmountDecision(
            unmountRequested = false,
            unmounted = true,
            prepared = false,
        )

        decision.unmounted shouldBe false
        decision.runPrepare shouldBe false
        decision.runCancelPrepare shouldBe false
    }

    @Test
    fun `flipping back during the prepared frame cancels the preparation`() {
        val decision = resolveInteropUnmountDecision(
            unmountRequested = false,
            unmounted = false,
            prepared = true,
        )

        decision.unmounted shouldBe false
        decision.runPrepare shouldBe false
        decision.runCancelPrepare shouldBe true
    }
}
