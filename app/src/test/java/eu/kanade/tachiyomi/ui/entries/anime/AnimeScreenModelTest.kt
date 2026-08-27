package eu.kanade.tachiyomi.ui.entries.anime

import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class AnimeScreenModelTest {

    @Test
    fun `mergeHydrationById preserves item identity and updates download state`() {
        val episode1 = Episode.create().copy(id = 201L, animeId = 10L, name = "Episode 1")
        val episode2 = Episode.create().copy(id = 202L, animeId = 10L, name = "Episode 2")

        val currentItems = listOf(
            EpisodeList.Item(
                episode = episode1,
                downloadState = AnimeDownload.State.NOT_DOWNLOADED,
                downloadProgress = 0,
                selected = true,
            ),
            EpisodeList.Item(
                episode = episode2,
                downloadState = AnimeDownload.State.NOT_DOWNLOADED,
                downloadProgress = 0,
                selected = false,
            ),
        )

        val hydratedItems = listOf(
            EpisodeList.Item(
                episode = episode1,
                downloadState = AnimeDownload.State.DOWNLOADED,
                downloadProgress = 100,
                selected = false,
            ),
            EpisodeList.Item(
                episode = episode2,
                downloadState = AnimeDownload.State.DOWNLOADING,
                downloadProgress = 60,
                selected = false,
            ),
        )

        val merged = mergeHydrationById(currentItems, hydratedItems)

        merged.size shouldBe 2
        merged[0].id shouldBe 201L
        merged[0].downloadState shouldBe AnimeDownload.State.DOWNLOADED
        merged[0].downloadProgress shouldBe 100
        merged[0].selected shouldBe true // Selected state preserved!

        merged[1].id shouldBe 202L
        merged[1].downloadState shouldBe AnimeDownload.State.DOWNLOADING
        merged[1].downloadProgress shouldBe 60
        merged[1].selected shouldBe false
    }

    @Test
    fun `mergeHydrationById keeps existing item unmodified if download state matches`() {
        val episode1 = Episode.create().copy(id = 201L, animeId = 10L, name = "Episode 1")
        val item = EpisodeList.Item(
            episode = episode1,
            downloadState = AnimeDownload.State.DOWNLOADED,
            downloadProgress = 100,
            selected = false,
        )

        val current = listOf(item)
        val hydrated = listOf(item.copy())

        val merged = mergeHydrationById(current, hydrated)
        merged.size shouldBe 1
        (merged[0] === item) shouldBe true
    }

    @Test
    fun `shouldApplyDefaultFlags returns true only when unfavorited and SHOW_ALL`() {
        val unfavoritedShowAll = Anime.create().copy(
            id = 1L,
            favorite = false,
            episodeFlags = Anime.SHOW_ALL,
            seasonFlags = Anime.SHOW_ALL,
        )
        val favorited = Anime.create().copy(
            id = 2L,
            favorite = true,
            episodeFlags = Anime.SHOW_ALL,
            seasonFlags = Anime.SHOW_ALL,
        )

        shouldApplyDefaultEpisodeFlags(unfavoritedShowAll) shouldBe true
        shouldApplyDefaultSeasonFlags(unfavoritedShowAll) shouldBe true

        shouldApplyDefaultEpisodeFlags(favorited) shouldBe false
        shouldApplyDefaultSeasonFlags(favorited) shouldBe false
    }
}
