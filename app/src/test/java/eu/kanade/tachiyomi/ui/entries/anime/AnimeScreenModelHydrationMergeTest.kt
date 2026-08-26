package eu.kanade.tachiyomi.ui.entries.anime

import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.episode.model.Episode

/**
 * Regression tests for the hydration race: the deferred hydrate job must merge download-state
 * fields by episode id instead of overwriting the whole list with a stale snapshot.
 */
class AnimeScreenModelHydrationMergeTest {

    @Test
    fun `hydration preserves fresher episode rows and selection`() {
        val staleEpisode = episode(id = 1L)
        val fresherEpisode = staleEpisode.copy(seen = true) // DB emitted a newer row after hydration started
        val current = listOf(
            EpisodeList.Item(fresherEpisode, AnimeDownload.State.NOT_DOWNLOADED, 0, selected = true),
        )
        val hydrated = listOf(
            EpisodeList.Item(staleEpisode, AnimeDownload.State.DOWNLOADED, 100),
        )

        val merged = mergeHydrationById(current, hydrated)

        merged shouldBe listOf(
            EpisodeList.Item(fresherEpisode, AnimeDownload.State.DOWNLOADED, 100, selected = true),
        )
    }

    @Test
    fun `items missing from hydration snapshot are kept as-is`() {
        val current = listOf(
            EpisodeList.Item(episode(id = 1L), AnimeDownload.State.DOWNLOADED, 50, selected = true),
        )
        val hydrated = emptyList<EpisodeList.Item>()

        val merged = mergeHydrationById(current, hydrated)

        merged shouldBe current
    }

    @Test
    fun `unchanged download state keeps same item instance`() {
        val item = EpisodeList.Item(episode(id = 1L), AnimeDownload.State.NOT_DOWNLOADED, 0)
        val hydrated = listOf(EpisodeList.Item(item.episode, AnimeDownload.State.NOT_DOWNLOADED, 0))

        val merged = mergeHydrationById(listOf(item), hydrated)

        merged[0] shouldBe item
    }

    @Test
    fun `mapEpisodesPreservingDownloadState reuses existing items and download states without disk checks`() {
        val episode1 = episode(id = 1L)
        val episode2 = episode(id = 2L)
        val episode3 = episode(id = 3L) // New episode

        val existingItem1 = EpisodeList.Item(episode1, AnimeDownload.State.DOWNLOADED, 100, selected = true)
        val existingItem2 = EpisodeList.Item(episode2, AnimeDownload.State.DOWNLOADING, 45, selected = false)
        val currentItems = listOf(existingItem1, existingItem2)

        var diskCheckCount = 0
        val mapped = mapEpisodesPreservingDownloadState(
            currentItems = currentItems,
            newEpisodes = listOf(episode1, episode2, episode3),
            anime = tachiyomi.domain.entries.anime.model.Anime.create().copy(id = 10L, source = 1L),
            selectedIds = setOf(1L),
            isEpisodeDownloaded = {
                diskCheckCount++
                it.id == 3L
            },
            getActiveDownload = { null },
        )

        // Episode 1 kept same instance (episode + selection unchanged)
        mapped[0] shouldBe existingItem1
        // Episode 2 preserved its active download state without disk check
        mapped[1].downloadState shouldBe AnimeDownload.State.DOWNLOADING
        mapped[1].downloadProgress shouldBe 45
        // Episode 3 resolved download state via callback (only 1 disk check)
        mapped[2].downloadState shouldBe AnimeDownload.State.DOWNLOADED
        diskCheckCount shouldBe 1
    }

    private fun episode(id: Long): Episode = Episode.create().copy(
        id = id,
        sourceOrder = id,
    )
}
