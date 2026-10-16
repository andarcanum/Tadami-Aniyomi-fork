package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class DiscoveryProgressTrackerTest {

    @After
    fun tearDown() = DiscoveryProgressTracker.reset()

    @Test
    fun `updating anime preserves novel progress`() {
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.NOVEL, stageText = "Novel", current = 2)
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.ANIME, stageText = "Anime", current = 1)

        DiscoveryProgressTracker.progress(DiscoveryMediaType.NOVEL).value.stageText shouldBe "Novel"
        DiscoveryProgressTracker.progress(DiscoveryMediaType.NOVEL).value.current shouldBe 2
        DiscoveryProgressTracker.progress(DiscoveryMediaType.ANIME).value.stageText shouldBe "Anime"
    }

    @Test
    fun `reset anime preserves novel progress`() {
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.NOVEL, stageText = "Novel")
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.ANIME, stageText = "Anime")

        DiscoveryProgressTracker.reset(DiscoveryMediaType.ANIME)

        DiscoveryProgressTracker.progress(DiscoveryMediaType.ANIME).value shouldBe DiscoveryProgress()
        DiscoveryProgressTracker.progress(DiscoveryMediaType.NOVEL).value.stageText shouldBe "Novel"
    }

    @Test
    fun `absent media state is idle`() {
        DiscoveryProgressTracker.reset()
        DiscoveryProgressTracker.progress(DiscoveryMediaType.MANGA).value shouldBe DiscoveryProgress()
    }

    @Test
    fun `initial state is not running and empty`() {
        DiscoveryProgressTracker.reset()
        val current = DiscoveryProgressTracker.progress(DiscoveryMediaType.NOVEL).value
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

        val current = DiscoveryProgressTracker.progress(DiscoveryMediaType.NOVEL).value
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

        val current = DiscoveryProgressTracker.progress(DiscoveryMediaType.MANGA).value
        current.isRunning shouldBe false
        current.mediaType shouldBe null
        current.stageText shouldBe ""
        current.current shouldBe 0
        current.total shouldBe 0
    }
}
