package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class DiscoveryProgressTrackerTest {

    @Test
    fun `initial state is not running and empty`() {
        DiscoveryProgressTracker.reset()
        val current = DiscoveryProgressTracker.progress.value
        current.isRunning shouldBe false
        current.mediaType shouldBe null
        current.stageText shouldBe ""
        current.current shouldBe 0
        current.total shouldBe 0
    }

    @Test
    fun `update sets progress state correctly`() {
        DiscoveryProgressTracker.update(
            isRunning = true,
            mediaType = DiscoveryMediaType.NOVEL,
            stageText = "Опрос источников (1/3)...",
            current = 1,
            total = 3,
        )

        val current = DiscoveryProgressTracker.progress.value
        current.isRunning shouldBe true
        current.mediaType shouldBe DiscoveryMediaType.NOVEL
        current.stageText shouldBe "Опрос источников (1/3)..."
        current.current shouldBe 1
        current.total shouldBe 3
    }

    @Test
    fun `reset clears running progress`() {
        DiscoveryProgressTracker.update(
            isRunning = true,
            mediaType = DiscoveryMediaType.MANGA,
            stageText = "Анализ рекомендаций...",
            current = 2,
            total = 5,
        )

        DiscoveryProgressTracker.reset()

        val current = DiscoveryProgressTracker.progress.value
        current.isRunning shouldBe false
        current.mediaType shouldBe null
        current.stageText shouldBe ""
        current.current shouldBe 0
        current.total shouldBe 0
    }
}
