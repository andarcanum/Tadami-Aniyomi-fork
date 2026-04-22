package eu.kanade.tachiyomi.ui.series.manga

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.entries.manga.model.Manga
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

class MangaSeriesScreenModelReorderTest {

    private val seriesId = 42L
    private val testDispatcher = StandardTestDispatcher()
    private val activeScreenModels = mutableListOf<MangaSeriesScreenModel>()
    private val getMangaSeriesWithEntries: GetMangaSeriesWithEntries = mockk()
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

        every { getMangaSeriesWithEntries.subscribe(seriesId) } returns flowOf(wrapper)
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
        val screenModel = MangaSeriesScreenModel(
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
        ).also(activeScreenModels::add)

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

        val screenModel = MangaSeriesScreenModel(
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
        ).also(activeScreenModels::add)

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

        val screenModel = MangaSeriesScreenModel(
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
        ).also(activeScreenModels::add)

        testDispatcher.scheduler.advanceUntilIdle()

        val chapters = screenModel.state.value.chapters.first().second
        assert(chapters.map { it.id } == listOf(10L, 20L, 30L))
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
