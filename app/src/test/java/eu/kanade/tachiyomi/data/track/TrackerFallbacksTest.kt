package eu.kanade.tachiyomi.data.track

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnime
import eu.kanade.tachiyomi.data.track.anilist.dto.ALManga
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaff
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudios
import eu.kanade.tachiyomi.data.track.anilist.toApiScore
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMCollectionResponse
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.simkl.Simkl
import eu.kanade.tachiyomi.data.track.simkl.toSimklStatus
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack as DbAnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack as DbMangaTrack
import eu.kanade.tachiyomi.data.track.anilist.toApiStatus as anilistAnimeToApiStatus
import eu.kanade.tachiyomi.data.track.anilist.toApiStatus as anilistMangaToApiStatus
import eu.kanade.tachiyomi.data.track.bangumi.toApiStatus as bangumiAnimeToApiStatus
import eu.kanade.tachiyomi.data.track.bangumi.toApiStatus as bangumiMangaToApiStatus
import eu.kanade.tachiyomi.data.track.shikimori.toShikimoriStatus as shikimoriAnimeToApiStatus
import eu.kanade.tachiyomi.data.track.shikimori.toShikimoriStatus as shikimoriMangaToApiStatus
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus as shikimoriToTrackStatus
import eu.kanade.tachiyomi.data.track.simkl.toTrackStatus as simklToTrackStatus
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class TrackerFallbacksTest {

    @Test
    fun `Kavita searchManga returns empty fallback instead of throwing`() = runTest {
        Kavita(TrackerManager.KAVITA).searchManga("query").shouldBeEmpty()
    }

    @Test
    fun `Suwayomi searchManga returns empty fallback instead of throwing`() = runTest {
        Suwayomi(9L).searchManga("query").shouldBeEmpty()
    }

    @Test
    fun `AniList status mappers use planning fallback for unknown status`() {
        DbMangaTrack.create(TrackerManager.ANILIST).apply { status = Long.MIN_VALUE }
            .anilistMangaToApiStatus() shouldBe "PLANNING"

        DbAnimeTrack.create(TrackerManager.ANILIST).apply { status = Long.MIN_VALUE }
            .anilistAnimeToApiStatus() shouldBe "PLANNING"
    }

    @Test
    fun `AniList score mapper uses point-10 fallback for unknown score type`() {
        val prefs = runCatching { Injekt.get<TrackPreferences>() }
            .getOrElse {
                TrackPreferences(InMemoryPreferenceStore()).also {
                    Injekt.addSingleton(fullType<TrackPreferences>(), it)
                }
            }
        prefs.anilistScoreType().set("UNKNOWN_SCORE_TYPE")

        DomainMangaTrack(
            id = 1L,
            mangaId = 1L,
            trackerId = TrackerManager.ANILIST,
            remoteId = 1L,
            libraryId = null,
            title = "Test",
            lastChapterRead = 0.0,
            totalChapters = 0L,
            status = 0L,
            score = 85.0,
            remoteUrl = "",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        ).toApiScore() shouldBe "8"
    }

    @Test
    fun `AniList user DTOs use planning fallback for unknown statuses`() {
        val anime = ALAnime(
            remoteId = 1L,
            title = "Anime",
            imageUrl = "",
            description = null,
            format = "",
            publishingStatus = "",
            startDateFuzzy = 0L,
            totalEpisodes = 0L,
            averageScore = 0,
            studios = ALStudios(edges = emptyList()),
        )
        val manga = ALManga(
            remoteId = 1L,
            title = "Manga",
            imageUrl = "",
            description = null,
            format = "",
            publishingStatus = "",
            startDateFuzzy = 0L,
            totalChapters = 0L,
            averageScore = 0,
            staff = ALStaff(edges = emptyList()),
        )

        eu.kanade.tachiyomi.data.track.anilist.dto.ALUserAnime(
            libraryId = 1L,
            listStatus = "UNKNOWN",
            scoreRaw = 0,
            episodesSeen = 0,
            startDateFuzzy = 0L,
            completedDateFuzzy = 0L,
            anime = anime,
            private = false,
        ).toTrack().status shouldBe Anilist.PLAN_TO_WATCH

        eu.kanade.tachiyomi.data.track.anilist.dto.ALUserManga(
            libraryId = 1L,
            listStatus = "UNKNOWN",
            scoreRaw = 0,
            chaptersRead = 0,
            startDateFuzzy = 0L,
            completedDateFuzzy = 0L,
            manga = manga,
            private = false,
        ).toTrack().status shouldBe Anilist.PLAN_TO_READ
    }

    @Test
    fun `Bangumi status mappers use planning fallback for unknown status`() {
        DbMangaTrack.create(5L).apply { status = Long.MIN_VALUE }
            .bangumiMangaToApiStatus() shouldBe 1

        DbAnimeTrack.create(5L).apply { status = Long.MIN_VALUE }
            .bangumiAnimeToApiStatus() shouldBe 1

        BGMCollectionResponse(rate = null, type = 99).getStatus() shouldBe Bangumi.PLAN_TO_READ
        BGMCollectionResponse(rate = null, type = null).getStatus() shouldBe Bangumi.PLAN_TO_READ
    }

    @Test
    fun `Shikimori status mappers use planned fallback for unknown status`() {
        DbMangaTrack.create(4L).apply { status = Long.MIN_VALUE }
            .shikimoriMangaToApiStatus() shouldBe "planned"

        DbAnimeTrack.create(4L).apply { status = Long.MIN_VALUE }
            .shikimoriAnimeToApiStatus() shouldBe "planned"

        shikimoriToTrackStatus("unknown") shouldBe Shikimori.PLAN_TO_READ
    }

    @Test
    fun `Simkl status mappers use plan-to-watch fallback for unknown status`() {
        DbAnimeTrack.create(TrackerManager.SIMKL).apply { status = Long.MIN_VALUE }
            .toSimklStatus() shouldBe "plantowatch"

        simklToTrackStatus("unknown") shouldBe Simkl.PLAN_TO_WATCH
    }
}
