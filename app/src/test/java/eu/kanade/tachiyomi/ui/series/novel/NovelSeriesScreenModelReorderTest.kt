package eu.kanade.tachiyomi.ui.series.novel

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
import tachiyomi.domain.book.novel.interactor.GetNovelBookState
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.interactor.AddNovelsToSeries
import tachiyomi.domain.series.novel.interactor.DeleteNovelSeries
import tachiyomi.domain.series.novel.interactor.GetNovelSeriesWithEntries
import tachiyomi.domain.series.novel.interactor.RemoveNovelFromSeries
import tachiyomi.domain.series.novel.interactor.ReorderSeriesEntries
import tachiyomi.domain.series.novel.interactor.UpdateNovelSeries
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.LibraryNovelSeriesWithEntryIds
import tachiyomi.domain.series.novel.model.NovelSeries
import java.util.Date
import kotlin.test.assertEquals

class NovelSeriesScreenModelReorderTest {

    private val seriesId = 42L
    private val testDispatcher = StandardTestDispatcher()
    private val activeScreenModels = mutableListOf<NovelSeriesScreenModel>()
    private val getNovelSeriesWithEntries: GetNovelSeriesWithEntries = mockk()
    private lateinit var seriesFlow: MutableStateFlow<LibraryNovelSeriesWithEntryIds?>
    private val novelHistoryRepository: NovelHistoryRepository = mockk()
    private val updateNovelSeries: UpdateNovelSeries = mockk(relaxed = true)
    private val deleteNovelSeries: DeleteNovelSeries = mockk(relaxed = true)
    private val addNovelsToSeries: AddNovelsToSeries = mockk(relaxed = true)
    private val removeNovelFromSeries: RemoveNovelFromSeries = mockk(relaxed = true)
    private val reorderSeriesEntries: ReorderSeriesEntries = mockk()
    private val getNovelChapters: GetNovelChapters = mockk()
    private val getNovelBookState: GetNovelBookState = mockk(relaxed = true)
    private val getNovelCategories: GetNovelCategories = mockk()
    private val setNovelCategories: SetNovelCategories = mockk(relaxed = true)
    private val seriesCoverCache: SeriesCoverCache = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val wrapper = LibraryNovelSeriesWithEntryIds(
            series = LibraryNovelSeries(
                series = NovelSeries(
                    id = seriesId,
                    title = "Series",
                    description = null,
                    categoryId = 0L,
                    sortOrder = 0L,
                    dateAdded = 0L,
                    coverLastModified = 0L,
                ),
                entries = listOf(
                    libraryNovel(id = 10L, title = "One"),
                    libraryNovel(id = 20L, title = "Two"),
                    libraryNovel(id = 30L, title = "Three"),
                ),
            ),
            entryIds = mapOf(
                10L to 110L,
                20L to 120L,
                30L to 130L,
            ),
        )

        seriesFlow = MutableStateFlow(wrapper)
        every { getNovelSeriesWithEntries.subscribe(seriesId) } returns seriesFlow
        coEvery { novelHistoryRepository.getHistoryByNovelId(any()) } returns emptyList()
        every { getNovelCategories.subscribe() } returns flowOf(emptyList())
        coEvery { getNovelChapters.await(any()) } returns emptyList()
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
                    it.map { entry -> entry.novelId } == listOf(30L, 10L) &&
                        it.map { entry -> entry.position } == listOf(0, 1) &&
                        it.all { entry -> entry.seriesId == seriesId }
                },
            )
        }
    }

    @Test
    fun `resume follows latest history and updates when another volume is read`() = runTest(testDispatcher) {
        val wrapper = requireNotNull(seriesFlow.value)
        val entries = wrapper.series.entries.map {
            it.copy(lastRead = if (it.id == 20L) 200L else 100L, readCount = if (it.id == 20L) 0L else 1L)
        }
        seriesFlow.value = wrapper.copy(series = wrapper.series.copy(entries = entries))
        coEvery { novelHistoryRepository.getHistoryByNovelId(20L) } returns listOf(
            NovelHistory.create().copy(chapterId = 12L, readAt = Date(150L)),
            NovelHistory.create().copy(chapterId = 11L, readAt = Date(200L)),
            NovelHistory.create().copy(chapterId = 13L, readAt = null),
        )
        coEvery { novelHistoryRepository.getHistoryByNovelId(10L) } returns listOf(
            NovelHistory.create().copy(chapterId = 1L, readAt = Date(300L)),
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

    private fun createScreenModel(): NovelSeriesScreenModel {
        return NovelSeriesScreenModel(
            seriesId = seriesId,
            getNovelSeriesWithEntries = getNovelSeriesWithEntries,
            updateNovelSeries = updateNovelSeries,
            deleteNovelSeries = deleteNovelSeries,
            addNovelsToSeries = addNovelsToSeries,
            removeNovelFromSeries = removeNovelFromSeries,
            reorderSeriesEntries = reorderSeriesEntries,
            getNovelChapters = getNovelChapters,
            getNovelBookState = getNovelBookState,
            getNovelCategories = getNovelCategories,
            setNovelCategories = setNovelCategories,
            seriesCoverCache = seriesCoverCache,
            novelHistoryRepository = novelHistoryRepository,
        ).also(activeScreenModels::add)
    }

    private fun libraryNovel(
        id: Long,
        title: String,
    ): LibraryNovel {
        return LibraryNovel(
            novel = Novel.create().copy(
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
}
