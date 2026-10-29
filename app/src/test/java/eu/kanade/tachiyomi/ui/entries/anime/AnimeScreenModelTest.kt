package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.Lifecycle
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerUtils
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.AnimeRatingFetcher
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.metadata.FetchEntryMetadataFromTracker
import eu.kanade.domain.items.episode.interactor.FetchEpisodePreviews
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.metadata.interactor.GetAnimeMetadata
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

class AnimeScreenModelTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupInjektBindings() {
            Dispatchers.setMain(Dispatchers.Unconfined)
            runCatching { Injekt.get<AnimeSourceManager>() }
                .getOrElse {
                    val animeSourceManager = mockk<AnimeSourceManager>(relaxed = true)
                    every { animeSourceManager.getOrStub(any()) } returns StubAnimeSource(1L, "Source", "en")
                    every { animeSourceManager.get(any()) } returns StubAnimeSource(1L, "Source", "en")
                    Injekt.addSingleton(fullType<AnimeSourceManager>(), animeSourceManager)
                }
        }

        @JvmStatic
        @AfterAll
        fun tearDownMainDispatcher() {
            Dispatchers.resetMain()
        }
    }

    private val activeScreenModels = mutableListOf<AnimeScreenModel>()

    private fun trackScreenModel(model: AnimeScreenModel): AnimeScreenModel {
        activeScreenModels += model
        return model
    }

    @BeforeEach
    fun setUp() {
        AnimeScreenModel.clearStateCacheForTest()
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { runCatching { it.onDispose() } }
        activeScreenModels.clear()
    }

    private suspend fun awaitSuccessState(screenModel: AnimeScreenModel): AnimeScreenModel.State.Success {
        var success: AnimeScreenModel.State.Success? = null
        withTimeout(2_000) {
            while (true) {
                val current = screenModel.state.value
                if (current is AnimeScreenModel.State.Success && current.episodes.isNotEmpty()) {
                    success = current
                    break
                }
                yield()
            }
        }
        return success!!
    }

    private fun createTestScreenModel(
        context: Context = mockk(relaxed = true),
        lifecycle: Lifecycle = mockk(relaxed = true),
        animeId: Long = 1L,
        isFromSource: Boolean = false,
        basePreferences: BasePreferences = mockk(relaxed = true) {
            every { downloadedOnly() } returns mockk {
                every { get() } returns false
                every { changes() } returns emptyFlow()
            }
        },
        uiPreferences: UiPreferences = mockk(relaxed = true) {
            every { metadataSource() } returns mockk { every { get() } returns MetadataSource.NONE }
        },
        downloadPreferences: DownloadPreferences = mockk(relaxed = true) {
            every { removeAfterMarkedAsRead() } returns mockk { every { get() } returns false }
            every { removeBookmarkedChapters() } returns mockk { every { get() } returns false }
            every { removeExcludeCategories() } returns mockk { every { get() } returns emptySet() }
            every { useExternalDownloader() } returns mockk { every { get() } returns false }
        },
        libraryPreferences: LibraryPreferences = mockk(relaxed = true) {
            every { swipeEpisodeEndAction() } returns
                mockk { every { get() } returns LibraryPreferences.EpisodeSwipeAction.Disabled }
            every { swipeEpisodeStartAction() } returns
                mockk { every { get() } returns LibraryPreferences.EpisodeSwipeAction.Disabled }
            every { autoUpdateItemRestrictions() } returns mockk { every { get() } returns emptySet() }
        },
        trackPreferences: TrackPreferences = mockk(relaxed = true) {
            every { autoUpdateTrackOnMarkRead() } returns mockk { every { get() } returns AutoTrackState.ALWAYS }
            every { showNextEpisodeAiringTime() } returns mockk { every { get() } returns false }
            every { trackOnAddingToLibrary() } returns mockk { every { get() } returns false }
        },
        playerPreferences: PlayerPreferences = mockk(relaxed = true) {
            every { alwaysUseExternalPlayer() } returns mockk { every { get() } returns false }
            every { alwaysAskOnEpisodeClick() } returns mockk { every { get() } returns false }
        },
        gesturePreferences: GesturePreferences = mockk(relaxed = true),
        trackerManager: TrackerManager = mockk(relaxed = true) {
            every { trackers } returns emptyList()
        },
        trackEpisode: eu.kanade.domain.track.anime.interactor.TrackEpisode = mockk(relaxed = true),
        sourceManager: AnimeSourceManager = mockk(relaxed = true) {
            every { getOrStub(any()) } returns StubAnimeSource(1L, "Source", "en")
        },
        downloadManager: AnimeDownloadManager = mockk(relaxed = true),
        downloadCache: AnimeDownloadCache = mockk(relaxed = true) {
            every { changes } returns MutableSharedFlow<Unit>()
        },
        getAnimeAndEpisodesAndSeasons: GetAnimeWithEpisodesAndSeasons = mockk(relaxed = true) {
            coEvery { subscribe(any()) } returns emptyFlow()
            coEvery { awaitAnime(any()) } returns Anime.create().copy(id = 1L, source = 1L, initialized = true)
            coEvery { awaitEpisodes(any()) } returns emptyList()
            coEvery { awaitSeasons(any()) } returns emptyList()
        },
        getDuplicateLibraryAnime: GetDuplicateLibraryAnime = mockk(relaxed = true),
        setAnimeEpisodeFlags: SetAnimeEpisodeFlags = mockk(relaxed = true),
        setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags = mockk(relaxed = true),
        setAnimeSeasonFlags: SetAnimeSeasonFlags = mockk(relaxed = true),
        setAnimeDefaultSeasonFlags: SetAnimeDefaultSeasonFlags = mockk(relaxed = true),
        setSeenStatus: SetSeenStatus = mockk(relaxed = true),
        updateEpisode: UpdateEpisode = mockk(relaxed = true),
        updateAnime: UpdateAnime = mockk(relaxed = true),
        animeRatingFetcher: AnimeRatingFetcher = mockk(relaxed = true),
        syncEpisodesWithSource: SyncEpisodesWithSource = mockk(relaxed = true),
        syncSeasonsWithSource: SyncSeasonsWithSource = mockk(relaxed = true),
        fetchEpisodePreviews: FetchEpisodePreviews = mockk(relaxed = true),
        getCategories: GetAnimeCategories = mockk(relaxed = true),
        getTracks: GetAnimeTracks = mockk(relaxed = true),
        addTracks: AddAnimeTracks = mockk(relaxed = true),
        setAnimeCategories: SetAnimeCategories = mockk(relaxed = true),
        animeRepository: AnimeRepository = mockk(relaxed = true),
        getEpisodesByAnimeId: GetEpisodesByAnimeId = mockk(relaxed = true),
        filterEpisodesForDownload: FilterEpisodesForDownload = mockk(relaxed = true),
        setAnimeViewerFlags: SetAnimeViewerFlags = mockk(relaxed = true),
        preferenceStore: tachiyomi.core.common.preference.PreferenceStore = mockk(relaxed = true),
        getAnimeMetadata: GetAnimeMetadata = mockk(relaxed = true) {
            coEvery { getCached(any()) } returns null
        },
        fetchEntryMetadataFromTracker: FetchEntryMetadataFromTracker = mockk(relaxed = true),
        suggestionCoordinator: SuggestionCoordinator = mockk(relaxed = true),
        sourcePreferences: SourcePreferences = mockk(relaxed = true) {
            every { entrySuggestionsEnabled() } returns mockk { every { get() } returns false }
        },
        torrentPreferences: TorrentPreferences = mockk(relaxed = true) {
            every { torrServerEnable() } returns mockk { every { get() } returns false }
        },
        torrentServerUtils: TorrentServerUtils = mockk(relaxed = true),
        snackbarHostState: SnackbarHostState = SnackbarHostState(),
    ): AnimeScreenModel {
        return trackScreenModel(
            AnimeScreenModel(
                context = context,
                lifecycle = lifecycle,
                animeId = animeId,
                isFromSource = isFromSource,
                basePreferences = basePreferences,
                uiPreferences = uiPreferences,
                downloadPreferences = downloadPreferences,
                libraryPreferences = libraryPreferences,
                trackPreferences = trackPreferences,
                playerPreferences = playerPreferences,
                gesturePreferences = gesturePreferences,
                trackerManager = trackerManager,
                trackEpisode = trackEpisode,
                sourceManager = sourceManager,
                downloadManager = downloadManager,
                downloadCache = downloadCache,
                getAnimeAndEpisodesAndSeasons = getAnimeAndEpisodesAndSeasons,
                getDuplicateLibraryAnime = getDuplicateLibraryAnime,
                setAnimeEpisodeFlags = setAnimeEpisodeFlags,
                setAnimeDefaultEpisodeFlags = setAnimeDefaultEpisodeFlags,
                setAnimeSeasonFlags = setAnimeSeasonFlags,
                setAnimeDefaultSeasonFlags = setAnimeDefaultSeasonFlags,
                setSeenStatus = setSeenStatus,
                updateEpisode = updateEpisode,
                updateAnime = updateAnime,
                animeRatingFetcher = animeRatingFetcher,
                syncEpisodesWithSource = syncEpisodesWithSource,
                syncSeasonsWithSource = syncSeasonsWithSource,
                fetchEpisodePreviews = fetchEpisodePreviews,
                getCategories = getCategories,
                getTracks = getTracks,
                addTracks = addTracks,
                setAnimeCategories = setAnimeCategories,
                animeRepository = animeRepository,
                getEpisodesByAnimeId = getEpisodesByAnimeId,
                filterEpisodesForDownload = filterEpisodesForDownload,
                setAnimeViewerFlags = setAnimeViewerFlags,
                preferenceStore = preferenceStore,
                getAnimeMetadata = getAnimeMetadata,
                fetchEntryMetadataFromTracker = fetchEntryMetadataFromTracker,
                suggestionCoordinator = suggestionCoordinator,
                sourcePreferences = sourcePreferences,
                torrentPreferences = torrentPreferences,
                torrentServerUtils = torrentServerUtils,
                snackbarHostState = snackbarHostState,
            ),
        )
    }

    @Test
    fun `deleteEpisodes reports progress and clears it when the batch completes`() = runTest {
        val anime = Anime.create().copy(id = 1L, title = "Anime 1", source = 1L)
        val episode1 = Episode.create().copy(id = 201L, animeId = 1L, name = "Ep 1")
        val episode2 = Episode.create().copy(id = 202L, animeId = 1L, name = "Ep 2")
        val item1 = EpisodeList.Item(
            episode = episode1,
            downloadState = AnimeDownload.State.DOWNLOADED,
            downloadProgress = 100,
            selected = false,
        )
        val item2 = EpisodeList.Item(
            episode = episode2,
            downloadState = AnimeDownload.State.DOWNLOADED,
            downloadProgress = 100,
            selected = false,
        )
        val source = StubAnimeSource(1L, "Source", "en")

        val state = AnimeScreenModel.State.Success(
            anime = anime,
            source = source,
            isFromSource = false,
            episodes = listOf(item1, item2),
            seasons = emptyList(),
        )
        AnimeScreenModel.cacheStateForTest(state)

        val downloadManager = mockk<AnimeDownloadManager>(relaxed = true) {
            every { isEpisodeDownloaded(any(), any(), any(), any(), any()) } returns true
            every { getDownloadCount(any<Anime>()) } returns 2
        }
        var capturedProgressDuringDelete: AnimeScreenModel.State.Success.EpisodeDeletionProgress? = null

        var screenModelRef: AnimeScreenModel? = null
        coEvery {
            downloadManager.deleteEpisodes(any(), any(), any(), any())
        } coAnswers {
            val progressCallback = it.invocation.args[3] as? ((Int, Int) -> Unit)
            progressCallback?.invoke(1, 2)
            val successState = screenModelRef?.state?.value as? AnimeScreenModel.State.Success
            capturedProgressDuringDelete = successState?.deletionProgress
            progressCallback?.invoke(2, 2)
            2
        }

        val getAnimeAndEpisodesAndSeasons = mockk<GetAnimeWithEpisodesAndSeasons>(relaxed = true) {
            coEvery { subscribe(any()) } returns emptyFlow()
            coEvery { awaitAnime(any()) } returns anime
            coEvery { awaitEpisodes(any()) } returns listOf(episode1, episode2)
            coEvery { awaitSeasons(any()) } returns emptyList()
        }

        val screenModel = createTestScreenModel(
            animeId = 1L,
            downloadManager = downloadManager,
            getAnimeAndEpisodesAndSeasons = getAnimeAndEpisodesAndSeasons,
        )
        screenModelRef = screenModel
        awaitSuccessState(screenModel)

        screenModel.deleteEpisodes(listOf(episode1, episode2))

        coVerify(timeout = 2_000) {
            downloadManager.deleteEpisodes(listOf(episode1, episode2), any(), any(), any())
        }

        withTimeout(2_000) {
            while ((screenModel.state.value as? AnimeScreenModel.State.Success)?.deletionProgress != null) {
                yield()
            }
        }

        val finalState = screenModel.state.value as AnimeScreenModel.State.Success
        finalState.deletionProgress.shouldBeNull()
        capturedProgressDuringDelete shouldBe AnimeScreenModel.State.Success.EpisodeDeletionProgress(1, 2)
    }

    @Test
    fun `offer cleanup snackbar appears once when everything is watched and downloaded`() = runTest {
        val anime = Anime.create().copy(id = 1L, title = "Anime 1", source = 1L, initialized = true)
        val episode1 = Episode.create().copy(id = 201L, animeId = 1L, name = "Ep 1", seen = true)
        val item1 = EpisodeList.Item(
            episode = episode1,
            downloadState = AnimeDownload.State.DOWNLOADED,
            downloadProgress = 100,
            selected = false,
        )
        val source = StubAnimeSource(1L, "Source", "en")

        val state = AnimeScreenModel.State.Success(
            anime = anime,
            source = source,
            isFromSource = false,
            episodes = listOf(item1),
            seasons = emptyList(),
        )
        AnimeScreenModel.cacheStateForTest(state)

        val snackbarHostState = mockk<SnackbarHostState>(relaxed = true)
        val setSeenStatus = mockk<SetSeenStatus>(relaxed = true)
        val getAnimeAndEpisodesAndSeasons = mockk<GetAnimeWithEpisodesAndSeasons>(relaxed = true) {
            coEvery { subscribe(any()) } returns emptyFlow()
            coEvery { awaitAnime(any()) } returns anime
            coEvery { awaitEpisodes(any()) } returns listOf(episode1)
            coEvery { awaitSeasons(any()) } returns emptyList()
        }
        val downloadManager = mockk<AnimeDownloadManager>(relaxed = true) {
            every { isEpisodeDownloaded(any(), any(), any(), any(), any()) } returns true
            every { getDownloadCount(any<Anime>()) } returns 1
        }
        val screenModel = createTestScreenModel(
            animeId = 1L,
            setSeenStatus = setSeenStatus,
            snackbarHostState = snackbarHostState,
            getAnimeAndEpisodesAndSeasons = getAnimeAndEpisodesAndSeasons,
            downloadManager = downloadManager,
        )
        awaitSuccessState(screenModel)

        screenModel.markEpisodesSeen(listOf(episode1), seen = true)
        coVerify(timeout = 2_000, atLeast = 1) {
            snackbarHostState.showSnackbar(
                message = any(),
                actionLabel = any(),
                withDismissAction = any(),
                duration = any(),
            )
        }

        // Repeat trigger does not enqueue another snackbar
        screenModel.markEpisodesSeen(listOf(episode1), seen = true)
        coVerify(timeout = 2_000, exactly = 1) {
            snackbarHostState.showSnackbar(
                message = any(),
                actionLabel = any(),
                withDismissAction = any(),
                duration = any(),
            )
        }
    }

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
