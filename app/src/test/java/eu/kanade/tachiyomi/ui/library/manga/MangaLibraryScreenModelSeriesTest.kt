package eu.kanade.tachiyomi.ui.library.manga

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.manga.interactor.AddMangasToSeries
import tachiyomi.domain.series.manga.interactor.CreateMangaSeries
import tachiyomi.domain.series.manga.interactor.GetLibraryMangaSeries
import tachiyomi.domain.series.manga.interactor.GetMangaIdsInAnySeries
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.model.MangaTrack
import kotlin.collections.emptyList

class MangaLibraryScreenModelSeriesTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var getLibraryManga: GetLibraryManga
    private lateinit var getLibraryMangaSeries: GetLibraryMangaSeries
    private lateinit var getMangaIdsInAnySeries: GetMangaIdsInAnySeries
    private lateinit var getCategories: GetVisibleMangaCategories
    private lateinit var getTracksPerManga: GetTracksPerManga
    private lateinit var sourceManager: MangaSourceManager
    private lateinit var downloadCache: MangaDownloadCache
    private lateinit var trackerManager: TrackerManager
    private lateinit var createMangaSeries: CreateMangaSeries
    private lateinit var addMangasToSeries: AddMangasToSeries
    private lateinit var mangaFlow: MutableStateFlow<List<LibraryManga>>
    private lateinit var seriesFlow: MutableStateFlow<List<LibraryMangaSeries>>
    private lateinit var seriesIdsFlow: MutableStateFlow<Set<Long>>
    private lateinit var categoriesFlow: MutableStateFlow<List<Category>>
    private lateinit var tracksFlow: MutableStateFlow<Map<Long, List<MangaTrack>>>
    private lateinit var downloadCacheChanges: MutableSharedFlow<Unit>
    private lateinit var basePreferences: BasePreferences
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var activeScreenModels: MutableList<MangaLibraryScreenModel>

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        activeScreenModels = mutableListOf()

        mangaFlow = MutableStateFlow(emptyList())
        seriesFlow = MutableStateFlow(emptyList())
        seriesIdsFlow = MutableStateFlow(emptySet())

        getLibraryManga = mockk()
        getLibraryMangaSeries = mockk()
        getMangaIdsInAnySeries = mockk()
        getCategories = mockk()
        getTracksPerManga = mockk()
        sourceManager = mockk(relaxed = true)
        downloadCache = mockk()
        trackerManager = mockk()
        createMangaSeries = mockk(relaxed = true)
        addMangasToSeries = mockk(relaxed = true)
        categoriesFlow = MutableStateFlow(listOf(category()))
        tracksFlow = MutableStateFlow(emptyMap())
        downloadCacheChanges = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
        every { getLibraryManga.subscribe() } returns mangaFlow
        every { getLibraryMangaSeries.subscribe() } returns seriesFlow
        every { getMangaIdsInAnySeries.subscribe() } returns seriesIdsFlow
        every { getCategories.subscribe() } returns categoriesFlow
        every { getTracksPerManga.subscribe() } returns tracksFlow
        every { downloadCache.changes } returns downloadCacheChanges
        every { trackerManager.loggedInTrackersFlow() } returns MutableStateFlow(emptyList<BaseTracker>())

        val preferenceStore = FakePreferenceStore()
        basePreferences = BasePreferences(
            context = mockk<Context>(relaxed = true),
            preferenceStore = preferenceStore,
        )
        libraryPreferences = LibraryPreferences(preferenceStore)
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `groups manga series into series items`() = runTest(testDispatcher) {
        val single = libraryManga(id = 1L, title = "Single Manga")
        val seriesManga = libraryManga(id = 2L, title = "Series Volume")
        val series = librarySeries(
            id = 7L,
            title = "Series",
            manga = seriesManga,
        )
        mangaFlow.value = listOf(single, seriesManga)
        seriesFlow.value = listOf(series)
        seriesIdsFlow.value = setOf(seriesManga.id)

        val screenModel = MangaLibraryScreenModel(
            getLibraryManga = getLibraryManga,
            getLibraryMangaSeries = getLibraryMangaSeries,
            getMangaIdsInAnySeries = getMangaIdsInAnySeries,
            getCategories = getCategories,
            getTracksPerManga = getTracksPerManga,
            getNextChapters = mockk(relaxed = true),
            getChaptersByMangaId = mockk(relaxed = true),
            setReadStatus = mockk(relaxed = true),
            updateManga = mockk(relaxed = true),
            setMangaCategories = mockk(relaxed = true),
            createMangaSeries = createMangaSeries,
            addMangasToSeries = addMangasToSeries,
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = mockk(relaxed = true),
            downloadCache = downloadCache,
            trackerManager = trackerManager,
        )
        activeScreenModels += screenModel

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.library.values.single().shouldContainExactly(
            MangaLibraryItem.Single(single, downloadCountValue = 0, sourceManager = sourceManager),
            MangaLibraryItem.Series(series, sourceManager = sourceManager),
        )
        screenModel.state.value.library.values.single()[1].id shouldBe -series.id
        screenModel.state.value.library.values.single()[1].title shouldBe "Series"
    }

    @Test
    fun `keeps manga series visible when unread filter is enabled`() = runTest(testDispatcher) {
        val first = libraryManga(id = 1L, title = "Volume 1", readCount = 10L, totalChapters = 10L)
        val second = libraryManga(id = 2L, title = "Volume 2", readCount = 0L, totalChapters = 10L)
        val series = librarySeries(
            id = 8L,
            title = "Series",
            entries = listOf(first, second),
        )
        mangaFlow.value = listOf(first, second)
        seriesFlow.value = listOf(series)
        seriesIdsFlow.value = setOf(first.id, second.id)

        val screenModel = MangaLibraryScreenModel(
            getLibraryManga = getLibraryManga,
            getLibraryMangaSeries = getLibraryMangaSeries,
            getMangaIdsInAnySeries = getMangaIdsInAnySeries,
            getCategories = getCategories,
            getTracksPerManga = getTracksPerManga,
            getNextChapters = mockk(relaxed = true),
            getChaptersByMangaId = mockk(relaxed = true),
            setReadStatus = mockk(relaxed = true),
            updateManga = mockk(relaxed = true),
            setMangaCategories = mockk(relaxed = true),
            createMangaSeries = createMangaSeries,
            addMangasToSeries = addMangasToSeries,
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = mockk(relaxed = true),
            downloadCache = downloadCache,
            trackerManager = trackerManager,
        )
        activeScreenModels += screenModel

        libraryPreferences.filterUnread().set(TriState.ENABLED_IS)

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.library.values.single().shouldContainExactly(
            MangaLibraryItem.Series(series, sourceManager = sourceManager),
        )
    }

    @Test
    fun `select all skips series items`() = runTest(testDispatcher) {
        val single = libraryManga(id = 1L, title = "Single Manga")
        val seriesManga = libraryManga(id = 2L, title = "Series Volume")
        val series = librarySeries(
            id = 7L,
            title = "Series",
            manga = seriesManga,
        )
        mangaFlow.value = listOf(single, seriesManga)
        seriesFlow.value = listOf(series)
        seriesIdsFlow.value = setOf(seriesManga.id)

        val screenModel = MangaLibraryScreenModel(
            getLibraryManga = getLibraryManga,
            getLibraryMangaSeries = getLibraryMangaSeries,
            getMangaIdsInAnySeries = getMangaIdsInAnySeries,
            getCategories = getCategories,
            getTracksPerManga = getTracksPerManga,
            getNextChapters = mockk(relaxed = true),
            getChaptersByMangaId = mockk(relaxed = true),
            setReadStatus = mockk(relaxed = true),
            updateManga = mockk(relaxed = true),
            setMangaCategories = mockk(relaxed = true),
            createMangaSeries = createMangaSeries,
            addMangasToSeries = addMangasToSeries,
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = mockk(relaxed = true),
            downloadCache = downloadCache,
            trackerManager = trackerManager,
        )
        activeScreenModels += screenModel

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.selectAll(0)

        screenModel.state.value.selection.map { it.id }.shouldContainExactly(single.id)
    }

    @Test
    fun `open add to series dialog shows available series`() = runTest(testDispatcher) {
        val manga = libraryManga(id = 1L, title = "Volume 1")
        val series = librarySeries(
            id = 7L,
            title = "Series",
            manga = manga,
        )
        mangaFlow.value = listOf(manga)
        seriesFlow.value = listOf(series)
        seriesIdsFlow.value = setOf(manga.id)

        val screenModel = MangaLibraryScreenModel(
            getLibraryManga = getLibraryManga,
            getLibraryMangaSeries = getLibraryMangaSeries,
            getMangaIdsInAnySeries = getMangaIdsInAnySeries,
            getCategories = getCategories,
            getTracksPerManga = getTracksPerManga,
            getNextChapters = mockk(relaxed = true),
            getChaptersByMangaId = mockk(relaxed = true),
            setReadStatus = mockk(relaxed = true),
            updateManga = mockk(relaxed = true),
            setMangaCategories = mockk(relaxed = true),
            createMangaSeries = createMangaSeries,
            addMangasToSeries = addMangasToSeries,
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = mockk(relaxed = true),
            downloadCache = downloadCache,
            trackerManager = trackerManager,
        )
        activeScreenModels += screenModel

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.openAddToSeries()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.dialog shouldBe MangaLibraryScreenModel.Dialog.AddToSeries(
            listOf(series.series),
        )
    }

    @Test
    fun `skips empty series rows in library flow`() = runTest(testDispatcher) {
        val single = libraryManga(id = 1L, title = "Single Manga")
        val emptySeries = librarySeries(
            id = 7L,
            title = "Empty Series",
            entries = emptyList(),
        )
        mangaFlow.value = listOf(single)
        seriesFlow.value = listOf(emptySeries)
        seriesIdsFlow.value = emptySet()

        val screenModel = MangaLibraryScreenModel(
            getLibraryManga = getLibraryManga,
            getLibraryMangaSeries = getLibraryMangaSeries,
            getMangaIdsInAnySeries = getMangaIdsInAnySeries,
            getCategories = getCategories,
            getTracksPerManga = getTracksPerManga,
            getNextChapters = mockk(relaxed = true),
            getChaptersByMangaId = mockk(relaxed = true),
            setReadStatus = mockk(relaxed = true),
            updateManga = mockk(relaxed = true),
            setMangaCategories = mockk(relaxed = true),
            createMangaSeries = createMangaSeries,
            addMangasToSeries = addMangasToSeries,
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = mockk(relaxed = true),
            downloadCache = downloadCache,
            trackerManager = trackerManager,
        )
        activeScreenModels += screenModel

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.library.values.single().shouldContainExactly(
            MangaLibraryItem.Single(single, downloadCountValue = 0, sourceManager = sourceManager),
        )
    }

    private fun libraryManga(
        id: Long,
        title: String,
        readCount: Long = 1L,
        totalChapters: Long = 10L,
    ): LibraryManga {
        return LibraryManga(
            manga = Manga.create().copy(
                id = id,
                title = title,
                url = "https://example.com/$id",
                source = 1L,
                favorite = true,
            ),
            category = 0L,
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }

    private fun librarySeries(
        id: Long,
        title: String,
        manga: LibraryManga? = null,
        entries: List<LibraryManga>? = null,
    ): LibraryMangaSeries {
        val actualEntries = entries ?: listOfNotNull(manga)
        return LibraryMangaSeries(
            series = MangaSeries(
                id = id,
                title = title,
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = actualEntries,
        )
    }

    private fun category(): Category {
        return Category(
            id = 0L,
            name = "Default",
            order = 0,
            flags = 0,
            hidden = false,
            hiddenFromHomeHub = false,
        )
    }

    private class FakePreferenceStore : PreferenceStore {
        private val strings = mutableMapOf<String, Preference<String>>()
        private val longs = mutableMapOf<String, Preference<Long>>()
        private val ints = mutableMapOf<String, Preference<Int>>()
        private val floats = mutableMapOf<String, Preference<Float>>()
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val stringSets = mutableMapOf<String, Preference<Set<String>>>()
        private val objects = mutableMapOf<String, Preference<Any>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            strings.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            longs.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            ints.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            floats.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(key, defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(key, defaultValue) }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return objects.getOrPut(key) { FakePreference(key, defaultValue as Any) } as Preference<T>
        }

        override fun getAll(): Map<String, *> {
            return emptyMap<String, Any>()
        }
    }

    private class FakePreference<T>(
        private val preferenceKey: String,
        defaultValue: T,
    ) : Preference<T> {
        private val state = MutableStateFlow(defaultValue)

        override fun key(): String = preferenceKey
        override fun get(): T = state.value
        override fun set(value: T) {
            state.value = value
        }
        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes(): Flow<T> = state
        override fun stateIn(scope: CoroutineScope) = state
    }
}
