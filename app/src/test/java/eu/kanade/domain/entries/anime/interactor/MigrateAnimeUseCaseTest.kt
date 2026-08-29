package eu.kanade.domain.entries.anime.interactor

import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.browse.anime.migration.AnimeMigrationFlags
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import java.util.Date

private const val EPISODES = 0b00001
private const val CATEGORIES = 0b00010

class MigrateAnimeUseCaseTest {

    @Test
    fun `migrateAnime preserves episode seen progress, fillermark, bookmark and history`() = runTest {
        val sourceManager = mockk<AnimeSourceManager>()
        val downloadManager = mockk<AnimeDownloadManager>(relaxed = true)
        val updateAnime = mockk<UpdateAnime>(relaxed = true)
        val networkToLocalAnime = mockk<NetworkToLocalAnime>()
        val getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>()
        val syncEpisodesWithSource = mockk<SyncEpisodesWithSource>()
        val updateEpisode = mockk<UpdateEpisode>(relaxed = true)
        val getCategories = mockk<GetAnimeCategories>(relaxed = true)
        val setAnimeCategories = mockk<SetAnimeCategories>(relaxed = true)
        val getTracks = mockk<GetAnimeTracks>(relaxed = true)
        val insertTrack = mockk<InsertAnimeTrack>(relaxed = true)
        val coverCache = mockk<AnimeCoverCache>(relaxed = true)
        val backgroundCache = mockk<AnimeBackgroundCache>(relaxed = true)
        val trackerManager = mockk<TrackerManager>()
        val getHistory = mockk<GetAnimeHistory>()
        val upsertHistory = mockk<UpsertAnimeHistory>(relaxed = true)
        val source = mockk<AnimeSource>()

        val oldAnime = Anime.create().copy(id = 1L, source = 10L, favorite = true, title = "Old Anime")
        val newAnime = Anime.create().copy(id = 2L, source = 20L, favorite = false, title = "New Anime")

        every { sourceManager.get(any()) } returns source
        every { trackerManager.trackers } returns emptyList()
        coEvery { networkToLocalAnime.await(newAnime) } returns newAnime
        coEvery { source.getEpisodeList(any()) } returns listOf(
            SEpisode.create().apply {
                url = "/ep1"
                name = "Episode 1"
                episode_number = 1f
            },
        )
        coEvery { syncEpisodesWithSource.await(any(), any(), any()) } returns emptyList()

        val oldEpisode = Episode.create().copy(
            id = 101L,
            animeId = oldAnime.id,
            seen = true,
            bookmark = true,
            fillermark = true,
            lastSecondSeen = 540L,
            totalSeconds = 1440L,
            dateFetch = 12345L,
            episodeNumber = 1.0,
        )
        val newEpisode = Episode.create().copy(
            id = 201L,
            animeId = newAnime.id,
            seen = false,
            bookmark = false,
            fillermark = false,
            lastSecondSeen = 0L,
            totalSeconds = 0L,
            dateFetch = 0L,
            episodeNumber = 1.0,
        )

        coEvery { getEpisodesByAnimeId.await(oldAnime.id) } returns listOf(oldEpisode)
        coEvery { getEpisodesByAnimeId.await(newAnime.id) } returns listOf(newEpisode)
        coEvery { getCategories.await(oldAnime.id) } returns emptyList()
        coEvery { getTracks.await(oldAnime.id) } returns emptyList()

        val historyDate = Date(1700000000000L)
        coEvery { getHistory.await(oldAnime.id) } returns listOf(
            AnimeHistory(id = 1L, episodeId = oldEpisode.id, seenAt = historyDate),
        )

        val episodeUpdatesSlot = slot<List<EpisodeUpdate>>()
        coEvery { updateEpisode.awaitAll(capture(episodeUpdatesSlot)) } returns Unit

        val historyUpdateSlot = slot<AnimeHistoryUpdate>()
        coEvery { upsertHistory.await(capture(historyUpdateSlot)) } returns Unit

        val useCase = MigrateAnimeUseCase(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateAnime = updateAnime,
            networkToLocalAnime = networkToLocalAnime,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            syncEpisodesWithSource = syncEpisodesWithSource,
            updateEpisode = updateEpisode,
            getCategories = getCategories,
            setAnimeCategories = setAnimeCategories,
            getTracks = getTracks,
            insertTrack = insertTrack,
            coverCache = coverCache,
            backgroundCache = backgroundCache,
            trackerManager = trackerManager,
            getHistory = getHistory,
            upsertHistory = upsertHistory,
        )

        val flags = EPISODES or CATEGORIES

        useCase.migrateAnime(
            oldAnime = oldAnime,
            newAnime = newAnime,
            replace = true,
            flags = flags,
        )

        // Verify episode updates transferred
        val capturedUpdates = episodeUpdatesSlot.captured
        assertEquals(1, capturedUpdates.size)
        val updatedEp = capturedUpdates.first()
        assertEquals(newEpisode.id, updatedEp.id)
        assertEquals(true, updatedEp.seen)
        assertEquals(true, updatedEp.bookmark)
        assertEquals(true, updatedEp.fillermark)
        assertEquals(540L, updatedEp.lastSecondSeen)
        assertEquals(1440L, updatedEp.totalSeconds)
        assertEquals(12345L, updatedEp.dateFetch)

        // Verify history transferred
        assertEquals(newEpisode.id, historyUpdateSlot.captured.episodeId)
        assertEquals(historyDate, historyUpdateSlot.captured.seenAt)

        // Verify favorite order: new favorite = true first, then old favorite = false
        coVerifyOrder {
            updateAnime.await(match { it.id == newAnime.id && it.favorite == true })
            updateAnime.awaitUpdateFavorite(oldAnime.id, favorite = false)
        }
    }
}
