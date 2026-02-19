package tachiyomi.data.track.novel

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository
import tachiyomi.domain.track.novel.model.NovelTrack

class NovelTrackRepositoryImplTest {

    @Test
    fun `maps manga tracks to novel tracks on reads`() = runBlocking {
        val fakeRepository = FakeMangaTrackRepository(
            tracksByMangaId = mapOf(
                10L to listOf(mangaTrack(id = 1L, mangaId = 10L, trackerId = 2L)),
            ),
        )
        val repository = NovelTrackRepositoryImpl(fakeRepository)

        val tracks = repository.getTracksByNovelId(10L)

        tracks shouldHaveSize 1
        tracks.first().novelId shouldBe 10L
        tracks.first().trackerId shouldBe 2L
    }

    @Test
    fun `maps novel track to manga track on insert`() = runBlocking {
        val fakeRepository = FakeMangaTrackRepository()
        val repository = NovelTrackRepositoryImpl(fakeRepository)

        repository.insertNovel(
            NovelTrack(
                id = 55L,
                novelId = 77L,
                trackerId = 5L,
                remoteId = 88L,
                libraryId = 99L,
                title = "Novel",
                lastChapterRead = 12.0,
                totalChapters = 100L,
                status = 1L,
                score = 8.5,
                remoteUrl = "https://example.org",
                startDate = 10L,
                finishDate = 20L,
                private = true,
            ),
        )

        fakeRepository.insertedTracks shouldHaveSize 1
        val inserted = fakeRepository.insertedTracks.first()
        inserted.mangaId shouldBe 77L
        inserted.trackerId shouldBe 5L
        inserted.remoteId shouldBe 88L
        inserted.title shouldBe "Novel"
    }
}

private class FakeMangaTrackRepository(
    private val tracksByMangaId: Map<Long, List<MangaTrack>> = emptyMap(),
) : MangaTrackRepository {

    val insertedTracks = mutableListOf<MangaTrack>()
    private val allTracksFlow = MutableStateFlow(emptyList<MangaTrack>())

    override suspend fun getTrackByMangaId(id: Long): MangaTrack? {
        return tracksByMangaId[id]?.firstOrNull()
    }

    override suspend fun getTracksByMangaId(mangaId: Long): List<MangaTrack> {
        return tracksByMangaId[mangaId].orEmpty()
    }

    override fun getMangaTracksAsFlow(): Flow<List<MangaTrack>> {
        return allTracksFlow
    }

    override fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<MangaTrack>> {
        return MutableStateFlow(tracksByMangaId[mangaId].orEmpty())
    }

    override suspend fun delete(mangaId: Long, trackerId: Long) {
        Unit
    }

    override suspend fun insertManga(track: MangaTrack) {
        insertedTracks += track
    }

    override suspend fun insertAllManga(tracks: List<MangaTrack>) {
        insertedTracks += tracks
    }
}

private fun mangaTrack(
    id: Long,
    mangaId: Long,
    trackerId: Long,
): MangaTrack {
    return MangaTrack(
        id = id,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = 0L,
        libraryId = null,
        title = "Track",
        lastChapterRead = 0.0,
        totalChapters = 0L,
        status = 0L,
        score = 0.0,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )
}
