package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlaybackFallbackSelectorTest {

    private fun readyHoster(
        name: String,
        videos: List<Video>,
        states: List<Video.State> = List(videos.size) { Video.State.QUEUE },
    ) = HosterState.Ready(
        name = name,
        videoList = videos,
        videoState = states,
    )

    private fun video(title: String, url: String = "https://cdn.example/$title.mp4", preferred: Boolean = false) =
        Video(
            videoUrl = url,
            videoTitle = title,
            preferred = preferred,
            initialized = true,
        )

    @Test
    fun `prefers another viable video in the same hoster first`() {
        val hosters = listOf(
            readyHoster(
                name = "Kodik",
                videos = listOf(video("1080p"), video("720p")),
                states = listOf(Video.State.ERROR, Video.State.QUEUE),
            ),
            readyHoster(
                name = "CDN",
                videos = listOf(video("1080p", preferred = true)),
            ),
        )

        PlaybackFallbackSelector.selectNext(
            hosterStates = hosters,
            failedIndex = 0 to 0,
            attemptedIndices = emptySet(),
        ) shouldBe (0 to 1)
    }

    @Test
    fun `falls back to preferred video in another hoster when current hoster is exhausted`() {
        val hosters = listOf(
            readyHoster(
                name = "Kodik",
                videos = listOf(video("1080p")),
                states = listOf(Video.State.ERROR),
            ),
            readyHoster(
                name = "MirrorA",
                videos = listOf(video("720p")),
            ),
            readyHoster(
                name = "MirrorB",
                videos = listOf(video("1080p", preferred = true)),
            ),
        )

        PlaybackFallbackSelector.selectNext(
            hosterStates = hosters,
            failedIndex = 0 to 0,
            attemptedIndices = setOf(0 to 0),
        ) shouldBe (2 to 0)
    }

    @Test
    fun `stops falling back once max auto-fallback attempts are reached`() {
        val hosters = listOf(
            readyHoster(
                name = "MirrorA",
                videos = listOf(video("1080p"), video("720p"), video("480p"), video("360p")),
            ),
        )

        PlaybackFallbackSelector.selectNext(
            hosterStates = hosters,
            failedIndex = 0 to 2,
            attemptedIndices = setOf(0 to 0, 0 to 1, 0 to 2),
            maxAttempts = 3,
        ) shouldBe null
    }

    @Test
    fun `skips initialized videos with empty url and error state videos`() {
        val hosters = listOf(
            readyHoster(
                name = "MirrorA",
                videos = listOf(
                    video("broken-empty", url = ""),
                    video("broken-error"),
                    video("working-480p"),
                ),
                states = listOf(Video.State.QUEUE, Video.State.ERROR, Video.State.READY),
            ),
        )

        PlaybackFallbackSelector.selectNext(
            hosterStates = hosters,
            failedIndex = 0 to 1,
            attemptedIndices = emptySet(),
        ) shouldBe (0 to 2)
    }
}
