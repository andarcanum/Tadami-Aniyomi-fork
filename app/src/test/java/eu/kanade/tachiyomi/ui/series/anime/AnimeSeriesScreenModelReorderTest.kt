package eu.kanade.tachiyomi.ui.series.anime

import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.anime.interactor.AddAnimesToSeries
import tachiyomi.domain.series.anime.interactor.DeleteAnimeSeries
import tachiyomi.domain.series.anime.interactor.GetAnimeSeriesWithEntries
import tachiyomi.domain.series.anime.interactor.RemoveAnimeFromSeries
import tachiyomi.domain.series.anime.interactor.ReorderSeriesEntries
import tachiyomi.domain.series.anime.interactor.UpdateAnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.series.anime.model.LibraryAnimeSeriesWithEntryIds

class AnimeSeriesScreenModelReorderTest {

    private val seriesId = 42L
    private val testDispatcher = StandardTestDispatcher()
    private val activeScreenModels = mutableListOf<AnimeSeriesScreenModel>()
    private val getAnimeSeriesWithEntries: GetAnimeSeriesWithEntries = mockk()
    private val updateAnimeSeries: UpdateAnimeSeries = mockk(relaxed = true)
    private val deleteAnimeSeries: DeleteAnimeSeries = mockk(relaxed = true)
    private val addAnimesToSeries: AddAnimesToSeries = mockk(relaxed = true)
    private val removeAnimeFromSeries: RemoveAnimeFromSeries = mockk(relaxed = true)
    private val reorderSeriesEntries: ReorderSeriesEntries = mockk()
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = mockk()
    private val getVisibleAnimeCategories: GetVisibleAnimeCategories = mockk()
    private val setAnimeCategories: SetAnimeCategories = mockk(relaxed = true)
    private val seriesCoverCache: SeriesCoverCache = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val wrapper = LibraryAnimeSeriesWithEntryIds(
            series = LibraryAnimeSeries(
                series = AnimeSeries(
                    id = seriesId,
                    title = "Series",
                    description = null,
                    categoryId = 0L,
                    sortOrder = 0L,
                    dateAdded = 0L,
                    coverLastModified = 0L,
                ),
                entries = listOf(
                    libraryAnime(id = 10L, title = "One"),
                    libraryAnime(id = 20L, title = "Two"),
                    libraryAnime(id = 30L, title = "Three"),
                ),
            ),
            entryIds = mapOf(
                10L to 110L,
                20L to 120L,
                30L to 130L,
            ),
        )

        every { getAnimeSeriesWithEntries.subscribe(seriesId) } returns flowOf(wrapper)
        every { getVisibleAnimeCategories.subscribe() } returns flowOf(emptyList())
        coEvery { getEpisodesByAnimeId.await(any()) } returns emptyList()
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
        val screenModel = AnimeSeriesScreenModel(
            seriesId = seriesId,
            getAnimeSeriesWithEntries = getAnimeSeriesWithEntries,
            updateAnimeSeries = updateAnimeSeries,
            deleteAnimeSeries = deleteAnimeSeries,
            addAnimesToSeries = addAnimesToSeries,
            removeAnimeFromSeries = removeAnimeFromSeries,
            reorderSeriesEntries = reorderSeriesEntries,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            getVisibleAnimeCategories = getVisibleAnimeCategories,
            setAnimeCategories = setAnimeCategories,
            seriesCoverCache = seriesCoverCache,
        ).also(activeScreenModels::add)

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            screenModel.state.collect()
        }

        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.reorderEntries(listOf(30L, 999L, 10L))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            reorderSeriesEntries.await(
                match {
                    it.map { entry -> entry.animeId } == listOf(30L, 10L) &&
                        it.map { entry -> entry.position } == listOf(0, 1) &&
                        it.all { entry -> entry.seriesId == seriesId }
                },
            )
        }
    }

    private fun libraryAnime(
        id: Long,
        title: String,
    ): LibraryAnime {
        return LibraryAnime(
            anime = Anime.create().copy(
                id = id,
                title = title,
                source = 1L,
                url = "https://example.com/$id",
            ),
            category = 0L,
            totalCount = 10L,
            seenCount = 0L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }
}
