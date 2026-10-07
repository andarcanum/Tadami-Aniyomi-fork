package eu.kanade.tachiyomi.ui.library.manga

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.manga.interactor.GetLibraryMangaSeries
import tachiyomi.domain.series.manga.interactor.GetMangaIdsInAnySeries
import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeries
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.model.MangaTrack

class MangaLibraryScreenModelLanguageFilterTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var getLibraryManga: GetLibraryManga
    private lateinit var getLibraryMangaSeries: GetLibraryMangaSeries
    private lateinit var getMangaIdsInAnySeries: GetMangaIdsInAnySeries
    private lateinit var getCategories: GetVisibleMangaCategories
    private lateinit var getTracksPerManga: GetTracksPerManga
    private lateinit var sourceManager: MangaSourceManager
    private lateinit var downloadCache: MangaDownloadCache
    private lateinit var downloadManager: MangaDownloadManager
    private lateinit var trackerManager: TrackerManager
    private lateinit var mangaFlow: MutableStateFlow<List<LibraryManga>>
    private lateinit var seriesFlow: MutableStateFlow<List<LibraryMangaSeries>>
    private lateinit var seriesIdsFlow: MutableStateFlow<Set<Long>>
    private lateinit var categoriesFlow: MutableStateFlow<List<Category>>
    private lateinit var tracksFlow: MutableStateFlow<Map<Long, List<MangaTrack>>>
    private lateinit var downloadCacheChanges: MutableSharedFlow<Unit>
    private lateinit var basePreferences: BasePreferences
    private lateinit var libraryPreferences: LibraryPreferences
    private val activeScreenModels = mutableListOf<MangaLibraryScreenModel>()

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

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
        downloadManager = mockk(relaxed = true)
        trackerManager = mockk()
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
    fun `language filter keeps only entries from selected source languages`() = runTest(testDispatcher) {
        val english = libraryManga(id = 1L, title = "English Manga", source = 1L)
        val japanese = libraryManga(id = 2L, title = "Japanese Manga", source = 2L)
        mangaFlow.value = listOf(english, japanese)
        every { sourceManager.getOrStub(1L).lang } returns "en"
        every { sourceManager.getOrStub(2L).lang } returns "ja"

        val screenModel = trackedMangaLibraryScreenModel()

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleLanguage("ja")
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(
            MangaLibraryItem.Single(japanese, downloadCountValue = 0, sourceManager = sourceManager),
        )
        screenModel.state.value.hasActiveFilters shouldBe true
    }

    @Test
    fun `clearing language filter shows all entries again`() = runTest(testDispatcher) {
        val english = libraryManga(id = 1L, title = "English Manga", source = 1L)
        val japanese = libraryManga(id = 2L, title = "Japanese Manga", source = 2L)
        mangaFlow.value = listOf(english, japanese)
        every { sourceManager.getOrStub(1L).lang } returns "en"
        every { sourceManager.getOrStub(2L).lang } returns "ja"

        val screenModel = trackedMangaLibraryScreenModel()

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleLanguage("ja")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.clearLanguageFilter()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactlyInAnyOrder(
            MangaLibraryItem.Single(english, downloadCountValue = 0, sourceManager = sourceManager),
            MangaLibraryItem.Single(japanese, downloadCountValue = 0, sourceManager = sourceManager),
        )
        screenModel.state.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `library languages lists distinct sorted source languages`() = runTest(testDispatcher) {
        val a = libraryManga(id = 1L, title = "A", source = 1L)
        val b = libraryManga(id = 2L, title = "B", source = 2L)
        val c = libraryManga(id = 3L, title = "C", source = 1L)
        mangaFlow.value = listOf(a, b, c)
        every { sourceManager.getOrStub(1L).lang } returns "en"
        every { sourceManager.getOrStub(2L).lang } returns "ja"

        val screenModel = trackedMangaLibraryScreenModel()

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.libraryLanguages shouldBe listOf("en", "ja")
    }

    @Test
    fun `language filter applies to series items by first entry source`() = runTest(testDispatcher) {
        val seriesManga = libraryManga(id = 2L, title = "Series Volume", source = 2L)
        val series = librarySeries(
            id = 7L,
            title = "Series",
            manga = seriesManga,
        )
        val single = libraryManga(id = 1L, title = "Single Manga", source = 1L)
        mangaFlow.value = listOf(seriesManga, single)
        seriesFlow.value = listOf(series)
        seriesIdsFlow.value = setOf(seriesManga.id)
        every { sourceManager.getOrStub(1L).lang } returns "en"
        every { sourceManager.getOrStub(2L).lang } returns "ja"

        val screenModel = trackedMangaLibraryScreenModel()

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.setAllLanguages(setOf("ja"))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(
            MangaLibraryItem.Series(series, sourceManager = sourceManager),
        )
    }

    private fun trackedMangaLibraryScreenModel(): MangaLibraryScreenModel {
        return MangaLibraryScreenModel(
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
            createMangaSeries = mockk(relaxed = true),
            addMangasToSeries = mockk(relaxed = true),
            updateMangaSeries = mockk(relaxed = true),
            deleteMangaSeries = mockk(relaxed = true),
            removeMangaFromSeries = mockk(relaxed = true),
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            downloadCache = downloadCache,
            trackerManager = trackerManager,
            libraryDispatcher = testDispatcher,
        ).also(activeScreenModels::add)
    }

    private fun libraryManga(
        id: Long,
        title: String,
        source: Long = 1L,
    ): LibraryManga {
        return LibraryManga(
            manga = Manga.create().copy(
                id = id,
                title = title,
                url = "https://example.com/$id",
                source = source,
                favorite = true,
            ),
            category = 0L,
            totalChapters = 10L,
            readCount = 1L,
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
        categoryId: Long = 0L,
    ): LibraryMangaSeries {
        val actualEntries = entries ?: listOfNotNull(manga)
        return LibraryMangaSeries(
            series = MangaSeries(
                id = id,
                title = title,
                description = null,
                categoryId = categoryId,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = actualEntries,
        )
    }

    private fun category(id: Long = 0L, name: String = "Default"): Category {
        return Category(
            id = id,
            name = name,
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
