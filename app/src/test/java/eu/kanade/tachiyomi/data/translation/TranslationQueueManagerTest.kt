package eu.kanade.tachiyomi.data.translation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TranslationQueueManagerTest {
    @Test
    fun `pending chapter is not inserted again`() {
        shouldSkipTranslationQueueInsert(
            existingStatus = TranslationStatus.PENDING,
            activeChapterId = null,
            chapterId = 1L,
        ) shouldBe true
    }

    @Test
    fun `active in progress chapter is not inserted again`() {
        shouldSkipTranslationQueueInsert(
            existingStatus = TranslationStatus.IN_PROGRESS,
            activeChapterId = 1L,
            chapterId = 1L,
        ) shouldBe true
    }

    @Test
    fun `stale in progress chapter can be inserted again`() {
        shouldSkipTranslationQueueInsert(
            existingStatus = TranslationStatus.IN_PROGRESS,
            activeChapterId = null,
            chapterId = 1L,
        ) shouldBe false
    }

    @Test
    fun `completed chapter can be inserted again`() {
        shouldSkipTranslationQueueInsert(
            existingStatus = TranslationStatus.COMPLETED,
            activeChapterId = null,
            chapterId = 1L,
        ) shouldBe false
    }
}
