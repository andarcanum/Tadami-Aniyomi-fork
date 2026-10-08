package eu.kanade.tachiyomi.ui.library.anime

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
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
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.series.anime.interactor.AddAnimesToSeries
import tachiyomi.domain.series.anime.interactor.CreateAnimeSeries
import tachiyomi.domain.series.anime.interactor.DeleteAnimeSeries
import tachiyomi.domain.series.anime.interactor.GetAnimeIdsInAnySeries
import tachiyomi.domain.series.anime.interactor.GetLibraryAnimeSeries
import tachiyomi.domain.series.anime.interactor.RemoveAnimeFromSeries
import tachiyomi.domain.series.anime.interactor.UpdateAnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack

class AnimeLibraryScreenModelSeriesTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var getLibraryAnime: GetLibraryAnime
    private lateinit var getLibraryAnimeSeries: GetLibraryAnimeSeries
    private lateinit var getAnimeIdsInAnySeries: GetAnimeIdsInAnySeries
    private lateinit var getCategories: GetVisibleAnimeCategories
    private lateinit var getTracksPerAnime: GetTracksPerAnime
    private lateinit var getNextEpisodes: GetNextEpisodes
    private lateinit var getEpisodesByAnimeId: GetEpisodesByAnimeId
    private lateinit var setSeenStatus: SetSeenStatus
    private lateinit var updateAnime: UpdateAnime
    private lateinit var setAnimeCategories: SetAnimeCategories
    private lateinit var sourceManager: AnimeSourceManager
    private lateinit var downloadCache: AnimeDownloadCache
    private lateinit var downloadManager: AnimeDownloadManager
    private lateinit var trackerManager: TrackerManager
    private lateinit var createAnimeSeries: CreateAnimeSeries
    private lateinit var addAnimesToSeries: AddAnimesToSeries
    private lateinit var updateAnimeSeries: UpdateAnimeSeries
    private lateinit var deleteAnimeSeries: DeleteAnimeSeries
    private lateinit var removeAnimeFromSeries: RemoveAnimeFromSeries
    private lateinit var animeFlow: MutableStateFlow<List<LibraryAnime>>
    private lateinit var seriesFlow: MutableStateFlow<List<LibraryAnimeSeries>>
    private lateinit var seriesIdsFlow: MutableStateFlow<Set<Long>>
    private lateinit var categoriesFlow: MutableStateFlow<List<Category>>
    private lateinit var tracksFlow: MutableStateFlow<Map<Long, List<AnimeTrack>>>
    private lateinit var downloadCacheChanges: MutableSharedFlow<Unit>
    private lateinit var basePreferences: BasePreferences
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var activeScreenModels: MutableList<AnimeLibraryScreenModel>

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        activeScreenModels = mutableListOf()

        animeFlow = MutableStateFlow(emptyList())
        seriesFlow = MutableStateFlow(emptyList())
        seriesIdsFlow = MutableStateFlow(emptySet())

        getLibraryAnime = mockk()
        getLibraryAnimeSeries = mockk()
        getAnimeIdsInAnySeries = mockk()
        getCategories = mockk()
        getTracksPerAnime = mockk()
        getNextEpisodes = mockk()
        getEpisodesByAnimeId = mockk()
        setSeenStatus = mockk(relaxed = true)
        updateAnime = mockk(relaxed = true)
        setAnimeCategories = mockk(relaxed = true)
        sourceManager = mockk(relaxed = true)
        downloadCache = mockk()
        downloadManager = mockk(relaxed = true)
        trackerManager = mockk()
        createAnimeSeries = mockk(relaxed = true)
        addAnimesToSeries = mockk(relaxed = true)
        updateAnimeSeries = mockk(relaxed = true)
        deleteAnimeSeries = mockk(relaxed = true)
        removeAnimeFromSeries = mockk(relaxed = true)
        categoriesFlow = MutableStateFlow(listOf(category()))
        tracksFlow = MutableStateFlow(emptyMap())
        downloadCacheChanges = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
        every { getLibraryAnime.subscribe() } returns animeFlow
        every { getLibraryAnimeSeries.subscribe() } returns seriesFlow
        every { getAnimeIdsInAnySeries.subscribe() } returns seriesIdsFlow
        every { getCategories.subscribe() } returns categoriesFlow
        every { getTracksPerAnime.subscribe() } returns tracksFlow
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
    fun `groups anime series into series items`() = runTest(testDispatcher) {
        val single = libraryAnime(id = 1L, title = "Single Anime")
        val seriesAnime = libraryAnime(id = 2L, title = "Series Volume")
        val series = librarySeries(
            id = 7L,
            title = "Series",
            anime = seriesAnime,
        )
        animeFlow.value = listOf(single, seriesAnime)
        seriesFlow.value = listOf(series)
        seriesIdsFlow.value = setOf(seriesAnime.id)

        val screenModel = AnimeLibraryScreenModel(
            getLibraryAnime = getLibraryAnime,
            getLibraryAnimeSeries = getLibraryAnimeSeries,
            getAnimeIdsInAnySeries = getAnimeIdsInAnySeries,
            getCategories = getCategories,
            getTracksPerAnime = getTracksPerAnime,
            getNextEpisodes = mockk(relaxed = true),
            getEpisodesByAnimeId = mockk(relaxed = true),
            setSeenStatus = mockk(relaxed = true),
            updateAnime = mockk(relaxed = true),
            setAnimeCategories = mockk(relaxed = true),
            createAnimeSeries = createAnimeSeries,
            addAnimesToSeries = addAnimesToSeries,
            updateAnimeSeries = updateAnimeSeries,
            deleteAnimeSeries = deleteAnimeSeries,
            removeAnimeFromSeries = removeAnimeFromSeries,
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            backgroundCache = mockk(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            downloadCache = downloadCache,
            trackerManager = trackerManager,
            libraryDispatcher = testDispatcher,
        )
        activeScreenModels += screenModel

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.library.values.single().shouldContainExactly(
            AnimeLibraryItem.Series(series),
            AnimeLibraryItem.Single(single, downloadCount = 0, unseenCount = single.unseenCount),
        )
        screenModel.state.value.library.values.single()[0].id shouldBe -series.id
        screenModel.state.value.library.values.single()[0].title shouldBe "Series"
    }

    private fun category(
        id: Long = 0L,
        name: String = "Default",
        order: Long = 0L,
        flags: Long = 0L,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            hidden = false,
            hiddenFromHomeHub = false,
        )
    }

    private fun libraryAnime(
        id: Long,
        title: String,
        category: Long = 0L,
        source: Long = 1L,
        totalCount: Long = 12L,
        seenCount: Long = 0L,
        status: Long = 1L,
    ): LibraryAnime {
        val anime = Anime.create().copy(
            id = id,
            source = source,
            title = title,
            status = status,
        )
        return LibraryAnime(
            anime = anime,
            category = category,
            totalCount = totalCount,
            seenCount = seenCount,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }

    private fun librarySeries(
        id: Long,
        title: String,
        anime: LibraryAnime? = null,
        entries: List<LibraryAnime> = anime?.let(::listOf) ?: emptyList(),
        categoryId: Long = 0L,
    ): LibraryAnimeSeries {
        return LibraryAnimeSeries(
            series = AnimeSeries(
                id = id,
                title = title,
                description = null,
                categoryId = categoryId,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = entries,
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
