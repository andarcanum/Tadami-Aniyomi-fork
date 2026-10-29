package eu.kanade.presentation.reader.novel

import android.content.res.Resources
import android.util.DisplayMetrics
import android.webkit.ValueCallback
import android.webkit.WebView
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class NovelBookScrollSurfaceReportTest {

    @Test
    fun `parses a plain auto-scroll sync report`() {
        val report = parseBookAutoScrollSyncReport("""{"running": true, "forward": true}""")

        report?.loopRunning shouldBe true
        report?.canScrollForward shouldBe true
    }

    @Test
    fun `parses a report whose loop stopped at the document end`() {
        val report = parseBookAutoScrollSyncReport("""{"running": false, "forward": false}""")

        report?.loopRunning shouldBe false
        report?.canScrollForward shouldBe false
    }

    @Test
    fun `parses the double quoted and escaped string evaluateJavascript returns`() {
        // evaluateJavascript hands JSON back as a quoted and escaped JSON string.
        val report = parseBookAutoScrollSyncReport("\"{\\\"running\\\": true, \\\"forward\\\": false}\"")

        report?.loopRunning shouldBe true
        report?.canScrollForward shouldBe false
    }

    @Test
    fun `null and blank results mean the engine is not reachable`() {
        parseBookAutoScrollSyncReport(null) shouldBe null
        parseBookAutoScrollSyncReport("null") shouldBe null
        parseBookAutoScrollSyncReport("") shouldBe null
    }

    @Test
    fun `the sync script stops the loop when asked for zero pixels`() {
        val script = buildBookAutoScrollSyncJavascript(0f, 1f)

        script shouldBe """
            (function() {
              var engine = window.__anBookEngine;
              if (engine && typeof engine.setAutoScroll === 'function') {
                engine.setAutoScroll(0.0, 1.0);
                var running = typeof engine.autoScrollActive === 'function' ? engine.autoScrollActive() : false;
                var forward = typeof engine.canScrollForward === 'function' ? engine.canScrollForward() : true;
                return JSON.stringify({ running: running, forward: forward });
              }
              return JSON.stringify({ running: false, forward: false });
            })()
        """.trimIndent()
    }

    @Test
    fun `scroll by script targets unrounded consumed css`() {
        val script = buildBookScrollByJavascript(12.5f)

        script shouldBe """
            (function() {
              var engine = window.__anBookEngine;
              if (engine && typeof engine.scrollBy === 'function') {
                return engine.scrollBy(12.5);
              }
              return 0;
            })()
        """.trimIndent()
    }

    @Test
    fun `book auto scroll has sections left distinguishes resident edge from true end`() {
        // Temporary resident edge: more sections exist in book but not mounted yet -> waits for stitch
        resolveBookAutoScrollHasSectionsLeft(
            sectionCount = 5,
            currentSectionIndex = 1,
            renderedSectionIndices = listOf(0, 1),
            preparingSectionIndices = listOf(2),
            failedSectionIndices = emptyList(),
            isReady = true,
        ) shouldBe true

        // True end: last section is resident, no sections preparing or failed -> end reached
        resolveBookAutoScrollHasSectionsLeft(
            sectionCount = 5,
            currentSectionIndex = 4,
            renderedSectionIndices = listOf(3, 4),
            preparingSectionIndices = emptyList(),
            failedSectionIndices = emptyList(),
            isReady = true,
        ) shouldBe false

        // Failed section does not declare successful end
        resolveBookAutoScrollHasSectionsLeft(
            sectionCount = 5,
            currentSectionIndex = 1,
            renderedSectionIndices = listOf(0, 1),
            preparingSectionIndices = emptyList(),
            failedSectionIndices = listOf(2),
            isReady = true,
        ) shouldBe true

        // Unready state does not declare end
        resolveBookAutoScrollHasSectionsLeft(
            sectionCount = 0,
            currentSectionIndex = 0,
            renderedSectionIndices = emptyList(),
            preparingSectionIndices = emptyList(),
            failedSectionIndices = emptyList(),
            isReady = false,
        ) shouldBe true
    }

    @Test
    fun `view novel book scroll surface throttles steady cadence and reacts to stop and rate`() {
        val webView = mockk<WebView>(relaxed = true)
        val displayMetrics = DisplayMetrics().apply { density = 1f }
        val resources = mockk<Resources>(relaxed = true)
        every { resources.displayMetrics } returns displayMetrics
        every { webView.resources } returns resources

        val evaluatedScripts = mutableListOf<String>()
        val callbacks = mutableListOf<ValueCallback<String>?>()

        every { webView.evaluateJavascript(any(), any()) } answers {
            val script = firstArg<String>()
            val callback = secondArg<ValueCallback<String>?>()
            evaluatedScripts.add(script)
            callbacks.add(callback)
        }

        var currentTime = 1000L
        val surface = ViewNovelBookScrollSurface(
            view = webView,
            paginated = { false },
            onStep = {},
            clockMs = { currentTime },
        )

        // 1. Initial sync at rate = 100f
        surface.syncAutoScrollSpeed(100f) shouldBe true
        evaluatedScripts.size shouldBe 1
        callbacks.size shouldBe 1
        callbacks.last()?.onReceiveValue("{\"running\": true, \"forward\": true}")

        // 2. Throttling: subsequent calls with same rate within 250ms do not evaluate JS
        currentTime = 1100L
        surface.syncAutoScrollSpeed(100f) shouldBe true
        evaluatedScripts.size shouldBe 1

        currentTime = 1249L
        surface.syncAutoScrollSpeed(100f) shouldBe true
        evaluatedScripts.size shouldBe 1

        // Steady state: at +250ms, the next call triggers evaluation
        currentTime = 1250L
        surface.syncAutoScrollSpeed(100f) shouldBe true
        evaluatedScripts.size shouldBe 2
        callbacks.last()?.onReceiveValue("{\"running\": true, \"forward\": true}")

        // Verify <= 4 evaluations per second in steady state
        val evaluationsBefore = evaluatedScripts.size
        for (i in 1..60) {
            currentTime += 16L
            surface.syncAutoScrollSpeed(100f)
            callbacks.last()?.onReceiveValue("{\"running\": true, \"forward\": true}")
        }
        val evaluationsInOneSec = evaluatedScripts.size - evaluationsBefore
        (evaluationsInOneSec <= 4) shouldBe true

        // 3. Immediate rate change: changing rate triggers immediate evaluation even if < 250ms elapsed
        currentTime += 10L
        val evaluationsBeforeRateChange = evaluatedScripts.size
        surface.syncAutoScrollSpeed(200f) shouldBe true
        (evaluatedScripts.size - evaluationsBeforeRateChange) shouldBe 1
        val latePendingCallback = callbacks.last()

        // 4. Immediate stop: rate <= 0 triggers immediate stop evaluation with null callback
        surface.syncAutoScrollSpeed(0f) shouldBe true
        callbacks.last() shouldBe null

        // 5. Rejection of stale/late callbacks: latePendingCallback returning after stop is ignored
        latePendingCallback?.onReceiveValue("{\"running\": true, \"forward\": true}")
        currentTime += 10L
        val evaluationsBeforeRestart = evaluatedScripts.size
        surface.syncAutoScrollSpeed(100f) shouldBe true
        (evaluatedScripts.size - evaluationsBeforeRestart) shouldBe 1
    }
}
