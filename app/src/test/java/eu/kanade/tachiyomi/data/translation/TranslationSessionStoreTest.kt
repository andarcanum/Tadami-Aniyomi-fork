package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.service.TranslationFragmentPlanner

class TranslationSessionStoreTest {
    private fun run(generation: Long = 1) = TranslationRun(
        id = 1, chapterId = 1, novelId = 1, batchToken = "", batchOrder = 0, profileSnapshotJson = "{}",
        status = TranslationRunStatus.RUNNING,
        progress = 0,
        errorMessage = null,
        createdAt = 0,
        updatedAt = 0,
        retryCount = 0,
        generation = generation, profileFingerprint = "profile", sessionOnly = true,
    )
    private fun entry() = GeminiTranslationCacheEntry(
        1,
        mapOf(0 to "Result"),
        model = "model",
        sourceLang = "English",
        targetLang = "Russian",
        promptMode = GeminiPromptMode.CLASSIC,
        sourceSegmentCount = 1,
        sourceFingerprint = "source",
        generationFingerprint = "generation",
    )

    @Test
    fun `session checkpoints are generation separated and full result is actually delivered`() {
        val store = TranslationSessionStore()
        val old = run()
        store.checkpoint(old, TranslationFragmentPlanner.plan(listOf("Source")))
        store.fragments(run(2)) shouldBe null
        store.publish(old, entry())
        store.result(1) shouldBe entry()
        store.clearChapter(1)
        store.result(1) shouldBe null
        store.fragments(old) shouldBe null
    }

    @Test
    fun `incomplete session result cannot become a completed artifact`() {
        val store = TranslationSessionStore()
        runCatching { store.publish(run(), entry().copy(sourceSegmentCount = 2)) }.isFailure shouldBe true
        store.result(1) shouldBe null
    }

    @Test
    fun `session memory budget is enforced before accepting an unbounded checkpoint`() {
        val store = TranslationSessionStore(8)
        runCatching {
            store.checkpoint(run(), TranslationFragmentPlanner.plan(listOf("Long source")))
        }.isFailure shouldBe true
        store.fragments(run()) shouldBe null
    }

    @Test
    fun `more than eight active session checkpoints are retained under byte budget`() {
        val store = TranslationSessionStore()
        val runs = (1L..12L).map { id ->
            run(generation = id).copy(id = id, chapterId = id)
        }
        runs.forEach { currentRun ->
            store.checkpoint(currentRun, TranslationFragmentPlanner.plan(listOf("Source ${currentRun.id}")))
        }

        runs.forEach { currentRun ->
            (store.fragments(currentRun) != null) shouldBe true
        }
    }

    @Test
    fun `byte pressure evicts completed results first and rejects oversize active run`() {
        val store = TranslationSessionStore(byteLimit = 36L)
        val completedRun = run(generation = 1L).copy(id = 1L, chapterId = 1L)
        val activeRunA = run(generation = 1L).copy(id = 2L, chapterId = 2L)
        val activeRunB = run(generation = 1L).copy(id = 3L, chapterId = 3L)
        val extraActiveRun = run(generation = 1L).copy(id = 4L, chapterId = 4L)

        store.checkpoint(completedRun, TranslationFragmentPlanner.plan(listOf("Source"))) // 12B
        store.publish(completedRun, entry()) // 24B total
        store.checkpoint(activeRunA, TranslationFragmentPlanner.plan(listOf("123456"))) // +12B = 36B
        store.checkpoint(activeRunB, TranslationFragmentPlanner.plan(listOf("ABCDEFGH"))) // evicts completedRun -> 28B

        store.result(1L) shouldBe null
        (store.fragments(activeRunA) != null) shouldBe true
        (store.fragments(activeRunB) != null) shouldBe true

        runCatching {
            store.checkpoint(extraActiveRun, TranslationFragmentPlanner.plan(listOf("ABCDEFGHIJKLM"))) // +26B > 36B
        }.isFailure shouldBe true
        store.fragments(extraActiveRun) shouldBe null
        (store.fragments(activeRunA) != null) shouldBe true
        (store.fragments(activeRunB) != null) shouldBe true
    }
}
