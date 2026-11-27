package eu.kanade.tachiyomi.ui.series.manga

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import eu.kanade.presentation.series.manga.MangaSeriesReadingTarget
import eu.kanade.presentation.series.manga.resolveMangaSeriesReadingTarget
import eu.kanade.tachiyomi.data.cache.SeriesCoverCache
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.manga.model.MangaHistory
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.interactor.GetMangaSeriesWithEntries
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.LibraryMangaSeriesWithEntryIds
import tachiyomi.domain.series.manga.model.MangaSeries
import java.io.File
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class MangaSeriesScanlatorSelectionTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `series start never targets an excluded scanlator chapter`() = runTest(dispatcher) {
        val (_, target) = selectChapter(excludedHistory = false)

        target?.chapter?.id shouldBe 102L
        target?.chapter?.scanlator shouldBe "Allowed"
    }

    @Test
    fun `series continue ignores excluded history and selects an allowed chapter`() = runTest(dispatcher) {
        val (state, target) = selectChapter(excludedHistory = true)

        state.resumeChapterId shouldBe 101L
        target?.chapter?.id shouldBe 102L
        target?.chapter?.scanlator shouldBe "Allowed"
    }

    private suspend fun TestScope.selectChapter(
        excludedHistory: Boolean,
    ): Pair<MangaSeriesScreenModel.State, MangaSeriesReadingTarget?> {
        val seriesId = 42L
        val excluded = Chapter.create().copy(
            id = 101L,
            mangaId = 10L,
            sourceOrder = 2L,
            chapterNumber = -1.0,
            url = "/test/excluded",
            name = "Excluded chapter",
            scanlator = "Excluded",
            read = excludedHistory,
        )
        val allowed = Chapter.create().copy(
            id = 102L,
            mangaId = 10L,
            sourceOrder = 1L,
            chapterNumber = -1.0,
            url = "/test/allowed",
            name = "Allowed chapter",
            scanlator = "Allowed",
        )
        val repository = mockk<ChapterRepository>()
        // The external repository boundary models the existing SQL filter contract. The
        // GetChapters interactor, screen model and launch-target resolver are all real.
        coEvery { repository.getChapterByMangaId(10L, false) } returns listOf(excluded, allowed)
        coEvery { repository.getChapterByMangaId(10L, true) } returns listOf(allowed)
        val entry = LibraryManga(
            manga = Manga.create().copy(id = 10L, source = 1L, title = "Entry", url = "/test/entry"),
            category = 0L,
            totalChapters = 1L,
            readCount = 0L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = if (excludedHistory) 200L else 0L,
        )
        val series = LibraryMangaSeries(
            series = MangaSeries(
                id = seriesId,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(entry),
        )
        val wrapper = LibraryMangaSeriesWithEntryIds(series, mapOf(10L to 110L))
        val getSeries = mockk<GetMangaSeriesWithEntries>()
        every { getSeries.subscribe(seriesId) } returns MutableStateFlow(wrapper)
        val categories = mockk<GetVisibleMangaCategories>()
        every { categories.subscribe() } returns flowOf(emptyList())
        val history = mockk<MangaHistoryRepository>()
        coEvery { history.getHistoryByMangaId(10L) } returns if (excludedHistory) {
            listOf(MangaHistory.create().copy(chapterId = 101L, readAt = Date(200L)))
        } else {
            emptyList()
        }
        val cache = mockk<SeriesCoverCache>()
        val coverFile = mockk<File>()
        every { coverFile.exists() } returns false
        every { cache.getMangaSeriesCoverFile(seriesId) } returns coverFile

        val holderKey = "series-scanlator-test-${UUID.randomUUID()}"
        val lastKey = storeLastKeyFlow()
        val previousKey = lastKey.value
        // Constructor stateIn calls must borrow only this disposable holder, never a prior model.
        lastKey.value = holderKey
        var collector: Job? = null
        try {
            val model = ScreenModelStore.getOrPut(holderKey, tag = null) {
                MangaSeriesScreenModel(
                    seriesId = seriesId, getMangaSeriesWithEntries = getSeries,
                    updateMangaSeries = mockk(relaxed = true), deleteMangaSeries = mockk(relaxed = true),
                    addMangasToSeries = mockk(relaxed = true), removeMangaFromSeries = mockk(relaxed = true),
                    reorderSeriesEntries = mockk(relaxed = true),
                    getChaptersByMangaId = GetChaptersByMangaId(repository),
                    getVisibleMangaCategories = categories, setMangaCategories = mockk(relaxed = true),
                    seriesCoverCache = cache, mangaHistoryRepository = history,
                )
            }
            collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
            runCurrent()
            val state = model.state.value
            state.chapters.size shouldBe 1
            val currentSeries = checkNotNull(state.series)
            val target = resolveMangaSeriesReadingTarget(currentSeries, state.chapters, state.resumeChapterId)
            return state to target
        } finally {
            collector?.cancelAndJoin()
            ScreenModelStore.onDisposeNavigator(holderKey)
            lastKey.value = previousKey
            runCurrent()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun storeLastKeyFlow(): MutableStateFlow<String?> {
        val field = ScreenModelStore::class.java.getDeclaredField("lastScreenModelKey")
        field.isAccessible = true
        return field.get(ScreenModelStore) as MutableStateFlow<String?>
    }
}
