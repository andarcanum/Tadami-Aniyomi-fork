package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class PlayerStartupCoordinatorTest {

    @Test
    fun `handleAniSkip keeps waiting time in sync and skips when chapters are already present`() {
        runBlocking {
            var waitingSkipIntro = 0
            var aniSkipResponseCalls = 0
            var updateChaptersCalls = 0
            var setChapterCalls = 0

            val coordinator = PlayerStartupCoordinator(
                chaptersProvider = {
                    listOf(IndexedSegment(name = "Existing", start = 1f))
                },
                currentPositionProvider = { 12.5f },
                setWaitingSkipIntro = { waitingSkipIntro = it },
                aniSkipResponse = {
                    aniSkipResponseCalls++
                    listOf(TimeStamp(start = 0.0, end = 5.0, name = "Opening", type = ChapterType.Opening))
                },
                updateChapters = {
                    updateChaptersCalls++
                },
                setChapter = {
                    setChapterCalls++
                },
            )

            coordinator.handleAniSkip(
                playerDuration = 123,
                waitingSkipIntro = 27,
                introSkipEnabled = true,
                aniSkipEnabled = true,
                disableAniSkipOnChapters = true,
            )

            waitingSkipIntro shouldBe 27
            aniSkipResponseCalls shouldBe 0
            updateChaptersCalls shouldBe 0
            setChapterCalls shouldBe 0
        }
    }

    @Test
    fun `handleAniSkip applies AniSkip timestamps and repositions the current chapter`() {
        runBlocking {
            var waitingSkipIntro = 0
            var updatedChapters: List<IndexedSegment>? = null
            var selectedPosition = 0f

            val coordinator = PlayerStartupCoordinator(
                chaptersProvider = { emptyList() },
                currentPositionProvider = { 42.25f },
                setWaitingSkipIntro = { waitingSkipIntro = it },
                aniSkipResponse = { playerDuration ->
                    playerDuration shouldBe 240
                    listOf(TimeStamp(start = 0.0, end = 12.0, name = "Opening", type = ChapterType.Opening))
                },
                updateChapters = { chapters ->
                    updatedChapters = chapters
                },
                setChapter = { position ->
                    selectedPosition = position
                },
            )

            coordinator.handleAniSkip(
                playerDuration = 240,
                waitingSkipIntro = 33,
                introSkipEnabled = true,
                aniSkipEnabled = true,
                disableAniSkipOnChapters = false,
            )

            waitingSkipIntro shouldBe 33
            updatedChapters?.isNotEmpty() shouldBe true
            updatedChapters?.first()?.name shouldBe "Opening"
            selectedPosition shouldBe 42.25f
        }
    }
}
