package eu.kanade.domain.entries.anime.model

import eu.kanade.domain.items.episode.model.applyFilters
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class AnimeFilterPolicyTest {

    @Test
    fun `downloadedFilter reflects the stored episode flag`() {
        anime(downloadedFilterRaw = Anime.EPISODE_SHOW_DOWNLOADED).downloadedFilter shouldBe TriState.ENABLED_IS
        anime(downloadedFilterRaw = Anime.EPISODE_SHOW_NOT_DOWNLOADED).downloadedFilter shouldBe TriState.ENABLED_NOT
        anime(downloadedFilterRaw = 0L).downloadedFilter shouldBe TriState.DISABLED
    }

    @Test
    fun `seasonDownloadedFilter reflects the stored season flag`() {
        anime(seasonDownloadedFilterRaw = Anime.SEASON_SHOW_DOWNLOADED).seasonDownloadedFilter shouldBe
            TriState.ENABLED_IS
        anime(seasonDownloadedFilterRaw = Anime.SEASON_SHOW_NOT_DOWNLOADED).seasonDownloadedFilter shouldBe
            TriState.ENABLED_NOT
        anime(seasonDownloadedFilterRaw = 0L).seasonDownloadedFilter shouldBe TriState.DISABLED
    }

    @Test
    fun `effective downloaded helpers force downloaded-only mode when requested`() {
        val anime = anime()

        anime.effectiveDownloadedFilter(downloadedOnly = true) shouldBe TriState.ENABLED_IS
        anime.effectiveDownloadedFilter(downloadedOnly = false) shouldBe TriState.DISABLED
        anime.effectiveSeasonDownloadedFilter(downloadedOnly = true) shouldBe TriState.ENABLED_IS
        anime.effectiveSeasonDownloadedFilter(downloadedOnly = false) shouldBe TriState.DISABLED
    }

    @Test
    fun `episodesFiltered and seasonsFiltered treat downloaded-only mode as an active filter`() {
        val anime = anime()

        anime.episodesFiltered(downloadedOnly = true) shouldBe true
        anime.episodesFiltered(downloadedOnly = false) shouldBe false
        anime.seasonsFiltered(downloadedOnly = true) shouldBe true
        anime.seasonsFiltered(downloadedOnly = false) shouldBe false
    }

    @Test
    fun `custom cover helpers require explicit caches`() {
        Anime::hasCustomCover.parameters.last().isOptional shouldBe false
        Anime::hasCustomBackground.parameters.last().isOptional shouldBe false
    }

    @Test
    fun `episode filter entry point keeps only downloaded items when downloaded-only mode is enabled`() {
        val filtered = listOf(
            episodeItem(id = 1L, downloaded = true),
            episodeItem(id = 2L, downloaded = false),
        ).applyFilters(anime(), downloadedOnly = true)
            .map { it.episode.id }
            .toList()

        filtered.shouldContainExactly(1L)
    }

    private fun anime(
        downloadedFilterRaw: Long = 0L,
        seasonDownloadedFilterRaw: Long = 0L,
    ): Anime {
        return Anime.create().copy(
            id = 1L,
            title = "Anime",
            source = 1L,
            episodeFlags = downloadedFilterRaw,
            seasonFlags = seasonDownloadedFilterRaw,
        )
    }

    private fun episodeItem(id: Long, downloaded: Boolean): EpisodeList.Item {
        return EpisodeList.Item(
            episode = Episode.create().copy(
                id = id,
                name = "Episode $id",
                episodeNumber = id.toDouble(),
            ),
            downloadState = if (downloaded) AnimeDownload.State.DOWNLOADED else AnimeDownload.State.NOT_DOWNLOADED,
            downloadProgress = 0,
        )
    }
}
