package eu.kanade.tachiyomi.ui.series.manga

import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.manga.model.MangaHistory
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.interactor.AddMangasToSeries
import tachiyomi.domain.series.manga.interactor.DeleteMangaSeries
import tachiyomi.domain.series.manga.interactor.GetMangaSeriesWithEntries
import tachiyomi.domain.series.manga.interactor.RemoveMangaFromSeries
import tachiyomi.domain.series.manga.interactor.ReorderSeriesEntries
import tachiyomi.domain.series.manga.interactor.UpdateMangaSeries
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.LibraryMangaSeriesWithEntryIds
import tachiyomi.domain.series.manga.model.MangaSeries
import java.util.Date
import kotlin.test.assertEquals

class MangaSeriesScreenModelReorderTest {

    private val seriesId = 42L
    private val testDispatcher = StandardTestDispatcher()
    private val activeScreenModels = mutableListOf<MangaSeriesScreenModel>()
    private val getMangaSeriesWithEntries: GetMangaSeriesWithEntries = mockk()
    private lateinit var seriesFlow: MutableStateFlow<LibraryMangaSeriesWithEntryIds?>
    private val mangaHistoryRepository: MangaHistoryRepository = mockk()
    private val updateMangaSeries: UpdateMangaSeries = mockk(relaxed = true)
    private val deleteMangaSeries: DeleteMangaSeries = mockk(relaxed = true)
    private val addMangasToSeries: AddMangasToSeries = mockk(relaxed = true)
    private val removeMangaFromSeries: RemoveMangaFromSeries = mockk(relaxed = true)
    private val reorderSeriesEntries: ReorderSeriesEntries = mockk()
    private val getChaptersByMangaId: GetChaptersByMangaId = mockk()
    private val getVisibleMangaCategories: GetVisibleMangaCategories = mockk()
    private val setMangaCategories: SetMangaCategories = mockk(relaxed = true)
    private val seriesCoverCache: SeriesCoverCache = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val wrapper = LibraryMangaSeriesWithEntryIds(
            series = LibraryMangaSeries(
                series = MangaSeries(
                    id = seriesId,
                    title = "Series",
                    description = null,
                    categoryId = 0L,
                    sortOrder = 0L,
                    dateAdded = 0L,
                    coverLastModified = 0L,
                ),
                entries = listOf(
                    libraryManga(id = 10L, title = "One"),
                    libraryManga(id = 20L, title = "Two"),
                    libraryManga(id = 30L, title = "Three"),
                ),
            ),
            entryIds = mapOf(
                10L to 110L,
                20L to 120L,
                30L to 130L,
            ),
        )

        seriesFlow = MutableStateFlow(wrapper)
        every { getMangaSeriesWithEntries.subscribe(seriesId) } returns seriesFlow
        coEvery { mangaHistoryRepository.getHistoryByMangaId(any()) } returns emptyList()
        every { getVisibleMangaCategories.subscribe() } returns flowOf(emptyList())
        coEvery { getChaptersByMangaId.await(any()) } returns emptyList()
        coJustRun { reorderSeriesEntries.await(any()) }
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        activeScreenModels.clear()
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `reorderEntries commits mapped order once per drop`() = runTest(testDispatcher) {
        val screenModel = createScreenModel()

        // WhileSubscribed stateIn needs an active collector to drive the upstream flow
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            screenModel.state.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.reorderEntries(listOf(30L, 999L, 10L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            reorderSeriesEntries.await(
                match {
                    it.map { entry -> entry.mangaId } == listOf(30L, 10L) &&
                        it.map { entry -> entry.position } == listOf(0, 1) &&
                        it.all { entry -> entry.seriesId == seriesId }
                },
            )
        }
    }

    @Test
    fun `fetches chapters in ascending order for all chapters view`() = runTest(testDispatcher) {
        coEvery { getChaptersByMangaId.await(10L) } returns listOf(
            chapter(id = 3L, sourceOrder = 3L, chapterNumber = 3.0),
            chapter(id = 1L, sourceOrder = 1L, chapterNumber = 1.0),
            chapter(id = 2L, sourceOrder = 2L, chapterNumber = 2.0),
        )

        val screenModel = createScreenModel()

        // WhileSubscribed stateIn needs an active collector to drive the upstream flow
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            screenModel.state.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val chapters = screenModel.state.value.chapters.first().second
        assert(chapters.map { it.id } == listOf(1L, 2L, 3L))
    }

    @Test
    fun `chapter number dominates source order in all chapters view`() = runTest(testDispatcher) {
        coEvery { getChaptersByMangaId.await(10L) } returns listOf(
            chapter(id = 30L, sourceOrder = 1L, chapterNumber = 3.0),
            chapter(id = 10L, sourceOrder = 3L, chapterNumber = 1.0),
            chapter(id = 20L, sourceOrder = 2L, chapterNumber = 2.0),
        )

        val screenModel = createScreenModel()

        // WhileSubscribed stateIn needs an active collector to drive the upstream flow
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            screenModel.state.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val chapters = screenModel.state.value.chapters.first().second
        assert(chapters.map { it.id } == listOf(10L, 20L, 30L))
    }

    @Test
    fun `resume follows latest history and updates when another volume is read`() = runTest(testDispatcher) {
        val wrapper = requireNotNull(seriesFlow.value)
        val entries = wrapper.series.entries.map {
            it.copy(lastRead = if (it.id == 20L) 200L else 100L, readCount = if (it.id == 20L) 0L else 1L)
        }
        seriesFlow.value = wrapper.copy(series = wrapper.series.copy(entries = entries))
        coEvery { mangaHistoryRepository.getHistoryByMangaId(20L) } returns listOf(
            MangaHistory.create().copy(chapterId = 12L, readAt = Date(150L)),
            MangaHistory.create().copy(chapterId = 11L, readAt = Date(200L)),
            MangaHistory.create().copy(chapterId = 13L, readAt = null),
        )
        coEvery { mangaHistoryRepository.getHistoryByMangaId(10L) } returns listOf(
            MangaHistory.create().copy(chapterId = 1L, readAt = Date(300L)),
        )
        val screenModel = createScreenModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { screenModel.state.collect() }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(11L, screenModel.state.value.resumeChapterId)

        seriesFlow.value = wrapper.copy(
            series = wrapper.series.copy(entries = entries.map { if (it.id == 10L) it.copy(lastRead = 300L) else it }),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1L, screenModel.state.value.resumeChapterId)

        seriesFlow.value = wrapper
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, screenModel.state.value.resumeChapterId)
    }

    private fun createScreenModel(): MangaSeriesScreenModel {
        return MangaSeriesScreenModel(
            seriesId = seriesId,
            getMangaSeriesWithEntries = getMangaSeriesWithEntries,
            updateMangaSeries = updateMangaSeries,
            deleteMangaSeries = deleteMangaSeries,
            addMangasToSeries = addMangasToSeries,
            removeMangaFromSeries = removeMangaFromSeries,
            reorderSeriesEntries = reorderSeriesEntries,
            getChaptersByMangaId = getChaptersByMangaId,
            getVisibleMangaCategories = getVisibleMangaCategories,
            setMangaCategories = setMangaCategories,
            seriesCoverCache = seriesCoverCache,
            mangaHistoryRepository = mangaHistoryRepository,
        ).also(activeScreenModels::add)
    }

    private fun libraryManga(
        id: Long,
        title: String,
    ): LibraryManga {
        return LibraryManga(
            manga = Manga.create().copy(
                id = id,
                title = title,
                source = 1L,
                url = "https://example.com/$id",
            ),
            category = 0L,
            totalChapters = 10L,
            readCount = 0L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }

    private fun chapter(
        id: Long,
        sourceOrder: Long,
        chapterNumber: Double,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = 10L,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = 0L,
            sourceOrder = sourceOrder,
            url = "https://example.com/chapter/$id",
            name = "Chapter $id",
            dateUpload = 0L,
            chapterNumber = chapterNumber,
            scanlator = null,
            lastModifiedAt = 0L,
            version = 1L,
        )
    }
}
