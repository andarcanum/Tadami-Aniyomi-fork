package eu.kanade.domain.track.anime.interactor

import eu.kanade.domain.track.service.ResolveTrackProgressSync
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.AnimeTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack
import kotlin.test.assertEquals

class SyncEpisodeProgressWithTrackTest {

    @Test
    fun `non enhanced tracker still applies pull alignment when enabled`() = runTest {
        val animeId = 42L
        val getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>()
        val updateEpisode = mockk<UpdateEpisode>()
        val insertTrack = mockk<InsertAnimeTrack>(relaxed = true)
        val tracker = mockk<AnimeTracker>(relaxed = true)
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()
        val episodeUpdatesSlot = slot<List<EpisodeUpdate>>()

        coEvery { getEpisodesByAnimeId.await(animeId) } returns listOf(
            episode(id = 1, animeId = animeId, episodeNumber = 1.0, seen = false),
            episode(id = 2, animeId = animeId, episodeNumber = 2.0, seen = false),
            episode(id = 3, animeId = animeId, episodeNumber = 3.0, seen = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns true
        coEvery { updateEpisode.awaitAll(capture(episodeUpdatesSlot)) } returns Unit

        val interactor = SyncEpisodeProgressWithTrack(
            updateEpisode = updateEpisode,
            insertTrack = insertTrack,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
        )

        interactor.await(
            animeId = animeId,
            remoteTrack = track(animeId = animeId, lastEpisodeSeen = 2.0),
            service = tracker,
        )

        assertEquals(listOf(1L, 2L), episodeUpdatesSlot.captured.map { it.id })
        coVerify(exactly = 1) { updateEpisode.awaitAll(any()) }
        coVerify(exactly = 0) { tracker.update(any(), any()) }
    }

    @Test
    fun `toggle off skips pull alignment`() = runTest {
        val animeId = 42L
        val getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>()
        val updateEpisode = mockk<UpdateEpisode>(relaxed = true)
        val insertTrack = mockk<InsertAnimeTrack>(relaxed = true)
        val tracker = mockk<AnimeTracker>(relaxed = true)
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()

        coEvery { getEpisodesByAnimeId.await(animeId) } returns listOf(
            episode(id = 1, animeId = animeId, episodeNumber = 1.0, seen = false),
            episode(id = 2, animeId = animeId, episodeNumber = 2.0, seen = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns false

        val interactor = SyncEpisodeProgressWithTrack(
            updateEpisode = updateEpisode,
            insertTrack = insertTrack,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
        )

        interactor.await(
            animeId = animeId,
            remoteTrack = track(animeId = animeId, lastEpisodeSeen = 2.0),
            service = tracker,
        )

        coVerify(exactly = 0) { updateEpisode.awaitAll(any()) }
        coVerify(exactly = 0) { tracker.update(any(), any()) }
    }

    private fun episode(
        id: Long,
        animeId: Long,
        episodeNumber: Double,
        seen: Boolean,
    ): Episode {
        return Episode.create().copy(
            id = id,
            animeId = animeId,
            episodeNumber = episodeNumber,
            seen = seen,
            name = "Episode $episodeNumber",
            url = "/$episodeNumber",
        )
    }

    private fun track(
        animeId: Long,
        lastEpisodeSeen: Double,
    ): AnimeTrack {
        return AnimeTrack(
            id = 1L,
            animeId = animeId,
            trackerId = 2L,
            remoteId = 3L,
            libraryId = null,
            title = "Test",
            lastEpisodeSeen = lastEpisodeSeen,
            totalEpisodes = 10L,
            status = 1L,
            score = 0.0,
            remoteUrl = "",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        )
    }
}
