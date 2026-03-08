package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class PlaybackSelectionResolverTest {

    @Test
    fun `resolve selects preferred player dubbing and exact quality`() {
        val hosters = listOf(
            hoster(
                playerId = "cdn",
                playerLabel = "CDN",
                dubbingId = "anistar",
                dubbingLabel = "AniStar",
                qualities = listOf("1080p", "720p"),
            ),
            hoster(
                playerId = "kodik",
                playerLabel = "Kodik",
                dubbingId = "animevost",
                dubbingLabel = "AnimeVost",
                qualities = listOf("1080p"),
            ),
        )

        val selected = PlaybackSelectionResolver.resolve(
            hosters = hosters,
            preferences = PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.CDN,
                preferredDubbingCdn = "AniStar",
                preferredQualityCdn = "720p",
            ),
        )

        selected shouldBe PlaybackSelection.Selected(0, 1)
    }

    @Test
    fun `resolve falls back to same player when exact dubbing is missing`() {
        val hosters = listOf(
            hoster(
                playerId = "cdn",
                playerLabel = "CDN",
                dubbingId = "anilibria",
                dubbingLabel = "AniLibria",
                qualities = listOf("1080p"),
            ),
            hoster(
                playerId = "kodik",
                playerLabel = "Kodik",
                dubbingId = "animevost",
                dubbingLabel = "AnimeVost",
                qualities = listOf("1080p"),
            ),
        )

        val selected = PlaybackSelectionResolver.resolve(
            hosters = hosters,
            preferences = PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.CDN,
                preferredDubbingCdn = "AniStar",
                preferredQualityCdn = "best",
            ),
        )

        selected shouldBe PlaybackSelection.Selected(0, 0)
    }

    @Test
    fun `resolve falls back to second player when preferred player is unavailable`() {
        val hosters = listOf(
            hoster(
                playerId = "kodik",
                playerLabel = "Kodik",
                dubbingId = "animevost",
                dubbingLabel = "AnimeVost",
                qualities = listOf("720p", "480p"),
            ),
        )

        val selected = PlaybackSelectionResolver.resolve(
            hosters = hosters,
            preferences = PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.CDN,
                preferredDubbingKodik = "AnimeVost",
                preferredQualityKodik = "480p",
            ),
        )

        selected shouldBe PlaybackSelection.Selected(0, 1)
    }

    @Test
    fun `resolve returns none for legacy hosters without metadata`() {
        val hosters = listOf(
            Hoster(
                hosterName = "AniDub",
                videoList = listOf(
                    Video(videoUrl = "https://example.com/1080.m3u8", videoTitle = "1080p"),
                ),
            ),
        )

        val selected = PlaybackSelectionResolver.resolve(
            hosters = hosters,
            preferences = PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.CDN,
                preferredDubbingCdn = "AniDub",
                preferredQualityCdn = "1080p",
            ),
        )

        selected shouldBe PlaybackSelection.None
    }

    @Test
    fun `resolve selects alloha player using alloha preferences`() {
        val hosters = listOf(
            hoster(
                playerId = "cdn",
                playerLabel = "CDN",
                dubbingId = "anistar",
                dubbingLabel = "AniStar",
                qualities = listOf("1080p"),
            ),
            hoster(
                playerId = "alloha",
                playerLabel = "Alloha",
                dubbingId = "ani-lot",
                dubbingLabel = "AniLot",
                qualities = listOf("720p", "480p"),
            ),
        )

        val selected = PlaybackSelectionResolver.resolve(
            hosters = hosters,
            preferences = PlaybackSelectionPreferences(
                preferredPlayer = PlaybackPlayerPreference.ALLOHA,
                preferredDubbingAlloha = "AniLot",
                preferredQualityAlloha = "480p",
            ),
        )

        selected shouldBe PlaybackSelection.Selected(1, 1)
    }

    private fun hoster(
        playerId: String,
        playerLabel: String,
        dubbingId: String,
        dubbingLabel: String,
        qualities: List<String>,
    ): Hoster {
        return Hoster(
            hosterName = dubbingLabel,
            playerId = playerId,
            playerLabel = playerLabel,
            dubbingId = dubbingId,
            dubbingLabel = dubbingLabel,
            videoList = qualities.map { quality ->
                Video(
                    videoUrl = "https://example.com/$playerId-$dubbingId-$quality.m3u8",
                    videoTitle = quality,
                )
            },
        )
    }
}
