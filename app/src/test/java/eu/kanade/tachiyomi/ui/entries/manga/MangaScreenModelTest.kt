package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.Lifecycle
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SourceMangaRatingFetcher
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.metadata.FetchEntryMetadataFromTracker
import eu.kanade.domain.items.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.items.chapter.interactor.GetScanlatorChapterCounts
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.metadata.interactor.GetMangaMetadata
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.domain.series.manga.interactor.RemoveMangaFromSeries
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

class MangaScreenModelTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupInjektBindings() {
            Dispatchers.setMain(Dispatchers.Unconfined)
            runCatching { Injekt.get<MangaSourceManager>() }
                .getOrElse {
                    val mangaSourceManager = mockk<MangaSourceManager>(relaxed = true)
                    every { mangaSourceManager.getOrStub(any()) } returns StubMangaSource(1L, "Source", "en")
                    every { mangaSourceManager.get(any()) } returns StubMangaSource(1L, "Source", "en")
                    Injekt.addSingleton(fullType<MangaSourceManager>(), mangaSourceManager)
                }
        }

        @JvmStatic
        @AfterAll
        fun tearDownMainDispatcher() {
            Dispatchers.resetMain()
        }
    }

    private val activeScreenModels = mutableListOf<MangaScreenModel>()

    private fun trackScreenModel(model: MangaScreenModel): MangaScreenModel {
        activeScreenModels += model
        return model
    }

    @BeforeEach
    fun setUp() {
        MangaScreenModel.clearStateCacheForTest()
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { runCatching { it.onDispose() } }
        activeScreenModels.clear()
    }

    private suspend fun awaitSuccessState(screenModel: MangaScreenModel): MangaScreenModel.State.Success {
        var success: MangaScreenModel.State.Success? = null
        withTimeout(2_000) {
            while (true) {
                val current = screenModel.state.value
                if (current is MangaScreenModel.State.Success && current.chapters.isNotEmpty()) {
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
        mangaId: Long = 1L,
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
        libraryPreferences: LibraryPreferences = mockk(relaxed = true) {
            every { swipeChapterEndAction() } returns
                mockk { every { get() } returns LibraryPreferences.ChapterSwipeAction.Disabled }
            every { swipeChapterStartAction() } returns
                mockk { every { get() } returns LibraryPreferences.ChapterSwipeAction.Disabled }
            every { autoUpdateItemRestrictions() } returns mockk { every { get() } returns emptySet() }
        },
        trackPreferences: TrackPreferences = mockk(relaxed = true) {
            every { autoUpdateTrackOnMarkRead() } returns mockk { every { get() } returns AutoTrackState.ALWAYS }
            every { trackOnAddingToLibrary() } returns mockk { every { get() } returns false }
        },
        readerPreferences: ReaderPreferences = mockk(relaxed = true) {
            every { skipFiltered() } returns mockk {
                every { get() } returns false
                every { changes() } returns emptyFlow()
            }
        },
        trackerManager: TrackerManager = mockk(relaxed = true) {
            every { trackers } returns emptyList()
        },
        trackChapter: eu.kanade.domain.track.manga.interactor.TrackChapter = mockk(relaxed = true),
        downloadManager: MangaDownloadManager = mockk(relaxed = true),
        downloadCache: MangaDownloadCache = mockk(relaxed = true) {
            every { changes } returns MutableSharedFlow<Unit>()
        },
        getMangaAndChapters: GetMangaWithChapters = mockk(relaxed = true) {
            every { subscribe(any(), any()) } returns emptyFlow()
            coEvery { awaitManga(any()) } returns Manga.create().copy(id = 1L, source = 1L, initialized = true)
            coEvery { awaitChapters(any(), any()) } returns emptyList()
        },
        getDuplicateLibraryManga: GetDuplicateLibraryManga = mockk(relaxed = true),
        getAvailableScanlators: GetAvailableScanlators = mockk(relaxed = true) {
            every { subscribe(any()) } returns emptyFlow()
        },
        getScanlatorChapterCounts: GetScanlatorChapterCounts = mockk(relaxed = true) {
            coEvery { subscribe(any()) } returns emptyFlow()
        },
        getExcludedScanlators: GetExcludedScanlators = mockk(relaxed = true) {
            every { subscribe(any()) } returns emptyFlow()
        },
        setExcludedScanlators: SetExcludedScanlators = mockk(relaxed = true),
        setMangaChapterFlags: SetMangaChapterFlags = mockk(relaxed = true),
        setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = mockk(relaxed = true),
        setReadStatus: SetReadStatus = mockk(relaxed = true),
        downloadPreferences: DownloadPreferences = mockk(relaxed = true) {
            every { removeAfterMarkedAsRead() } returns mockk { every { get() } returns false }
            every { removeBookmarkedChapters() } returns mockk { every { get() } returns false }
            every { removeExcludeCategories() } returns mockk { every { get() } returns emptySet() }
        },
        updateChapter: UpdateChapter = mockk(relaxed = true),
        updateManga: UpdateManga = mockk(relaxed = true),
        syncChaptersWithSource: SyncChaptersWithSource = mockk(relaxed = true),
        getCategories: GetMangaCategories = mockk(relaxed = true),
        getTracks: GetMangaTracks = mockk(relaxed = true),
        addTracks: AddMangaTracks = mockk(relaxed = true),
        setMangaCategories: SetMangaCategories = mockk(relaxed = true),
        mangaRepository: MangaRepository = mockk(relaxed = true),
        mangaHistoryRepository: MangaHistoryRepository = mockk(relaxed = true),
        filterChaptersForDownload: FilterChaptersForDownload = mockk(relaxed = true),
        getMangaMetadata: GetMangaMetadata = mockk(relaxed = true) {
            coEvery { getCached(any()) } returns null
        },
        fetchEntryMetadataFromTracker: FetchEntryMetadataFromTracker = mockk(relaxed = true),
        sourceMangaRatingFetcher: SourceMangaRatingFetcher = mockk(relaxed = true),
        suggestionCoordinator: SuggestionCoordinator = mockk(relaxed = true),
        sourcePreferences: SourcePreferences = mockk(relaxed = true) {
            every { entrySuggestionsEnabled() } returns mockk { every { get() } returns false }
        },
        removeMangaFromSeries: RemoveMangaFromSeries = mockk(relaxed = true),
        snackbarHostState: SnackbarHostState = SnackbarHostState(),
    ): MangaScreenModel {
        return trackScreenModel(
            MangaScreenModel(
                context = context,
                lifecycle = lifecycle,
                mangaId = mangaId,
                isFromSource = isFromSource,
                basePreferences = basePreferences,
                uiPreferences = uiPreferences,
                libraryPreferences = libraryPreferences,
                trackPreferences = trackPreferences,
                readerPreferences = readerPreferences,
                trackerManager = trackerManager,
                trackChapter = trackChapter,
                downloadManager = downloadManager,
                downloadCache = downloadCache,
                getMangaAndChapters = getMangaAndChapters,
                getDuplicateLibraryManga = getDuplicateLibraryManga,
                getAvailableScanlators = getAvailableScanlators,
                getScanlatorChapterCounts = getScanlatorChapterCounts,
                getExcludedScanlators = getExcludedScanlators,
                setExcludedScanlators = setExcludedScanlators,
                setMangaChapterFlags = setMangaChapterFlags,
                setMangaDefaultChapterFlags = setMangaDefaultChapterFlags,
                setReadStatus = setReadStatus,
                downloadPreferences = downloadPreferences,
                updateChapter = updateChapter,
                updateManga = updateManga,
                syncChaptersWithSource = syncChaptersWithSource,
                getCategories = getCategories,
                getTracks = getTracks,
                addTracks = addTracks,
                setMangaCategories = setMangaCategories,
                mangaRepository = mangaRepository,
                mangaHistoryRepository = mangaHistoryRepository,
                filterChaptersForDownload = filterChaptersForDownload,
                getMangaMetadata = getMangaMetadata,
                fetchEntryMetadataFromTracker = fetchEntryMetadataFromTracker,
                sourceMangaRatingFetcher = sourceMangaRatingFetcher,
                suggestionCoordinator = suggestionCoordinator,
                sourcePreferences = sourcePreferences,
                removeMangaFromSeries = removeMangaFromSeries,
                snackbarHostState = snackbarHostState,
            ),
        )
    }

    @Test
    fun `deleteChapters reports progress and clears it when the batch completes`() = runTest {
        val manga = Manga.create().copy(id = 1L, title = "Manga 1", source = 1L)
        val chapter1 = Chapter.create().copy(id = 101L, mangaId = 1L, name = "Ch 1")
        val chapter2 = Chapter.create().copy(id = 102L, mangaId = 1L, name = "Ch 2")
        val item1 = ChapterList.Item(
            chapter = chapter1,
            downloadState = MangaDownload.State.DOWNLOADED,
            downloadProgress = 100,
            selected = false,
        )
        val item2 = ChapterList.Item(
            chapter = chapter2,
            downloadState = MangaDownload.State.DOWNLOADED,
            downloadProgress = 100,
            selected = false,
        )
        val source = StubMangaSource(1L, "Source", "en")

        val state = MangaScreenModel.State.Success(
            manga = manga,
            source = source,
            isFromSource = false,
            chapters = listOf(item1, item2),
            availableScanlators = emptySet(),
            scanlatorChapterCounts = emptyMap(),
            excludedScanlators = emptySet(),
        )
        MangaScreenModel.cacheStateForTest(state)

        val downloadManager = mockk<MangaDownloadManager>(relaxed = true) {
            every { isChapterDownloaded(any(), any(), any(), any(), any()) } returns true
            every { getDownloadCount(any<Manga>()) } returns 2
        }
        var capturedProgressDuringDelete: MangaScreenModel.State.Success.ChapterDeletionProgress? = null

        var screenModelRef: MangaScreenModel? = null
        coEvery {
            downloadManager.deleteChapters(any(), any(), any(), any())
        } coAnswers {
            val progressCallback = it.invocation.args[3] as? ((Int, Int) -> Unit)
            progressCallback?.invoke(1, 2)
            val successState = screenModelRef?.state?.value as? MangaScreenModel.State.Success
            capturedProgressDuringDelete = successState?.deletionProgress
            progressCallback?.invoke(2, 2)
            2
        }

        val getMangaAndChapters1 = mockk<GetMangaWithChapters>(relaxed = true) {
            every { subscribe(any(), any()) } returns emptyFlow()
            coEvery { awaitManga(any()) } returns manga
            coEvery { awaitChapters(any(), any()) } returns listOf(chapter1, chapter2)
        }

        val screenModel = createTestScreenModel(
            mangaId = 1L,
            downloadManager = downloadManager,
            getMangaAndChapters = getMangaAndChapters1,
        )
        screenModelRef = screenModel
        awaitSuccessState(screenModel)

        screenModel.deleteChapters(listOf(chapter1, chapter2))

        coVerify(timeout = 2_000) {
            downloadManager.deleteChapters(listOf(chapter1, chapter2), any(), any(), any())
        }

        withTimeout(2_000) {
            while ((screenModel.state.value as? MangaScreenModel.State.Success)?.deletionProgress != null) {
                yield()
            }
        }

        val finalState = screenModel.state.value as MangaScreenModel.State.Success
        finalState.deletionProgress.shouldBeNull()
        capturedProgressDuringDelete shouldBe MangaScreenModel.State.Success.ChapterDeletionProgress(1, 2)
    }

    @Test
    fun `offer cleanup snackbar appears once when everything is read and downloaded`() = runTest {
        val manga = Manga.create().copy(id = 1L, title = "Manga 1", source = 1L)
        val chapter1 = Chapter.create().copy(id = 101L, mangaId = 1L, name = "Ch 1", read = true)
        val item1 = ChapterList.Item(
            chapter = chapter1,
            downloadState = MangaDownload.State.DOWNLOADED,
            downloadProgress = 100,
            selected = false,
        )
        val source = StubMangaSource(1L, "Source", "en")

        val state = MangaScreenModel.State.Success(
            manga = manga,
            source = source,
            isFromSource = false,
            chapters = listOf(item1),
            availableScanlators = emptySet(),
            scanlatorChapterCounts = emptyMap(),
            excludedScanlators = emptySet(),
        )
        MangaScreenModel.cacheStateForTest(state)

        val snackbarHostState = mockk<SnackbarHostState>(relaxed = true)
        val setReadStatus = mockk<SetReadStatus>(relaxed = true)
        val getMangaAndChapters2 = mockk<GetMangaWithChapters>(relaxed = true) {
            every { subscribe(any(), any()) } returns emptyFlow()
            coEvery { awaitManga(any()) } returns manga
            coEvery { awaitChapters(any(), any()) } returns listOf(chapter1)
        }
        val downloadManager = mockk<MangaDownloadManager>(relaxed = true) {
            every { isChapterDownloaded(any(), any(), any(), any(), any()) } returns true
            every { getDownloadCount(any<Manga>()) } returns 1
        }
        val screenModel = createTestScreenModel(
            mangaId = 1L,
            setReadStatus = setReadStatus,
            snackbarHostState = snackbarHostState,
            getMangaAndChapters = getMangaAndChapters2,
            downloadManager = downloadManager,
        )
        awaitSuccessState(screenModel)

        screenModel.markChaptersRead(listOf(chapter1), read = true)
        coVerify(timeout = 2_000, atLeast = 1) {
            snackbarHostState.showSnackbar(
                message = any(),
                actionLabel = any(),
                withDismissAction = any(),
                duration = any(),
            )
        }

        // Repeat trigger does not enqueue another snackbar
        screenModel.markChaptersRead(listOf(chapter1), read = true)
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
        val chapter1 = Chapter.create().copy(id = 101L, mangaId = 1L, name = "Chapter 1")
        val chapter2 = Chapter.create().copy(id = 102L, mangaId = 1L, name = "Chapter 2")

        val currentItems = listOf(
            ChapterList.Item(
                chapter = chapter1,
                downloadState = MangaDownload.State.NOT_DOWNLOADED,
                downloadProgress = 0,
                selected = true,
            ),
            ChapterList.Item(
                chapter = chapter2,
                downloadState = MangaDownload.State.NOT_DOWNLOADED,
                downloadProgress = 0,
                selected = false,
            ),
        )

        val hydratedItems = listOf(
            ChapterList.Item(
                chapter = chapter1,
                downloadState = MangaDownload.State.DOWNLOADED,
                downloadProgress = 100,
                selected = false,
            ),
            ChapterList.Item(
                chapter = chapter2,
                downloadState = MangaDownload.State.DOWNLOADING,
                downloadProgress = 45,
                selected = false,
            ),
        )

        val merged = mergeHydrationById(currentItems, hydratedItems)

        merged.size shouldBe 2
        merged[0].id shouldBe 101L
        merged[0].downloadState shouldBe MangaDownload.State.DOWNLOADED
        merged[0].downloadProgress shouldBe 100
        merged[0].selected shouldBe true // Selected state from current must be preserved!

        merged[1].id shouldBe 102L
        merged[1].downloadState shouldBe MangaDownload.State.DOWNLOADING
        merged[1].downloadProgress shouldBe 45
        merged[1].selected shouldBe false
    }

    @Test
    fun `mergeHydrationById keeps existing item unmodified if download state matches`() {
        val chapter1 = Chapter.create().copy(id = 101L, mangaId = 1L, name = "Chapter 1")
        val item = ChapterList.Item(
            chapter = chapter1,
            downloadState = MangaDownload.State.DOWNLOADED,
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
    fun `shouldApplyDefaultChapterFlags returns true only when unfavorited and SHOW_ALL`() {
        val unfavoritedShowAll = Manga.create().copy(id = 1L, favorite = false, chapterFlags = Manga.SHOW_ALL)
        val favoritedShowAll = Manga.create().copy(id = 2L, favorite = true, chapterFlags = Manga.SHOW_ALL)
        val unfavoritedFiltered = Manga.create().copy(
            id = 3L,
            favorite = false,
            chapterFlags = Manga.CHAPTER_SHOW_UNREAD,
        )

        shouldApplyDefaultChapterFlags(unfavoritedShowAll) shouldBe true
        shouldApplyDefaultChapterFlags(favoritedShowAll) shouldBe false
        shouldApplyDefaultChapterFlags(unfavoritedFiltered) shouldBe false
    }
}
