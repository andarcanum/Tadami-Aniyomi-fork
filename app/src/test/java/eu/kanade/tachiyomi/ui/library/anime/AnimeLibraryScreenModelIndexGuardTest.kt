package eu.kanade.tachiyomi.ui.library.anime

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
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
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack

/**
 * D-M8/NEW-19 anime port guards: the persisted activeCategoryIndex can go stale when the
 * category list shrinks (or is transiently empty right after a push/pop). Selection and
 * random-entry actions must degrade to no-ops instead of crashing with IndexOutOfBounds.
 */
class AnimeLibraryScreenModelIndexGuardTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var getLibraryAnime: GetLibraryAnime
    private lateinit var getCategories: GetVisibleAnimeCategories
    private lateinit var getTracksPerAnime: GetTracksPerAnime
    private lateinit var sourceManager: AnimeSourceManager
    private lateinit var downloadCache: AnimeDownloadCache
    private lateinit var downloadManager: AnimeDownloadManager
    private lateinit var trackerManager: TrackerManager
    private lateinit var animeFlow: MutableStateFlow<List<LibraryAnime>>
    private lateinit var categoriesFlow: MutableStateFlow<List<Category>>
    private lateinit var tracksFlow: MutableStateFlow<Map<Long, List<AnimeTrack>>>
    private lateinit var downloadCacheChanges: MutableSharedFlow<Unit>
    private lateinit var basePreferences: BasePreferences
    private lateinit var libraryPreferences: LibraryPreferences
    private val activeScreenModels = mutableListOf<AnimeLibraryScreenModel>()

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        animeFlow = MutableStateFlow(emptyList())

        getLibraryAnime = mockk()
        getCategories = mockk()
        getTracksPerAnime = mockk()
        sourceManager = mockk(relaxed = true)
        downloadCache = mockk()
        downloadManager = mockk(relaxed = true)
        trackerManager = mockk()
        categoriesFlow = MutableStateFlow(listOf(category()))
        tracksFlow = MutableStateFlow(emptyMap())
        downloadCacheChanges = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }

        every { getLibraryAnime.subscribe() } returns animeFlow
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
    fun `invertSelection with stale index keeps selection and does not crash`() = runTest(testDispatcher) {
        val a = libraryAnime(id = 1L, title = "A")
        val b = libraryAnime(id = 2L, title = "B")
        animeFlow.value = listOf(a, b)

        val screenModel = trackedAnimeLibraryScreenModel()
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.toggleSelection(a)
        screenModel.invertSelection(5)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.selection.shouldContainExactly(a)
    }

    @Test
    fun `invertSelection with valid index swaps the selection`() = runTest(testDispatcher) {
        val a = libraryAnime(id = 1L, title = "A")
        val b = libraryAnime(id = 2L, title = "B")
        animeFlow.value = listOf(a, b)

        val screenModel = trackedAnimeLibraryScreenModel()
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.toggleSelection(a)
        screenModel.invertSelection(0)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.selection.map { it.id } shouldContainExactly listOf(2L)
    }

    @Test
    fun `random item with stale category index returns null instead of crashing`() = runTest(testDispatcher) {
        animeFlow.value = listOf(libraryAnime(id = 1L, title = "A"))

        val screenModel = trackedAnimeLibraryScreenModel()
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.activeCategoryIndex = 7
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.getRandomAnimelibItemForCurrentCategory().shouldBeNull()
    }

    @Test
    fun `random item from the current category returns an entry`() = runTest(testDispatcher) {
        val a = libraryAnime(id = 1L, title = "A")
        val b = libraryAnime(id = 2L, title = "B")
        animeFlow.value = listOf(a, b)

        val screenModel = trackedAnimeLibraryScreenModel()
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.activeCategoryIndex = 0
        testDispatcher.scheduler.advanceUntilIdle()

        val item = screenModel.getRandomAnimelibItemForCurrentCategory()
        (item != null) shouldBe true
        listOf(1L, 2L).contains(item!!.libraryAnime.anime.id) shouldBe true
    }

    @Test
    fun `initial group preference emission does not reset the persisted category index`() = runTest(testDispatcher) {
        animeFlow.value = listOf(libraryAnime(id = 1L, title = "A"))
        libraryPreferences.lastUsedAnimeCategory().set(1)

        val screenModel = trackedAnimeLibraryScreenModel()
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.activeCategoryIndex shouldBe 1
    }

    @Test
    fun `actual group type change resets the category index`() = runTest(testDispatcher) {
        animeFlow.value = listOf(libraryAnime(id = 1L, title = "A"))
        libraryPreferences.lastUsedAnimeCategory().set(1)

        val screenModel = trackedAnimeLibraryScreenModel()
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        libraryPreferences.animeGroupLibraryBy().set(LibraryGroup.BY_SOURCE)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.activeCategoryIndex shouldBe 0
        screenModel.state.value.groupType shouldBe LibraryGroup.BY_SOURCE
    }

    @Test
    fun `grouped view sorts group contents by the effective sort mode`() = runTest(testDispatcher) {
        // E1: BY_SOURCE merges items from several categories into one group; the group must be
        // globally sorted. Before the fix the group was a concatenation of per-category runs:
        // [Zeta(cat 1), Alpha(cat 2)] regardless of the alphabetical sort selection.
        val sort = AnimeLibrarySort(
            AnimeLibrarySort.Type.Alphabetical,
            AnimeLibrarySort.Direction.Ascending,
        )
        categoriesFlow.value = listOf(
            category(id = 1L, name = "One").copy(flags = sort.flag),
            category(id = 2L, name = "Two").copy(flags = sort.flag),
        )
        val zeta = libraryAnime(id = 1L, title = "Zeta").copy(category = 1L)
        val alpha = libraryAnime(id = 2L, title = "Alpha").copy(category = 2L)
        animeFlow.value = listOf(zeta, alpha)
        libraryPreferences.animeSortingMode().set(sort)
        libraryPreferences.animeGroupLibraryBy().set(LibraryGroup.BY_SOURCE)

        val screenModel = trackedAnimeLibraryScreenModel()
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        val group = screenModel.state.value.library.values.single()
        group.map { it.libraryAnime.anime.title } shouldContainExactly listOf("Alpha", "Zeta")
    }

    private fun trackedAnimeLibraryScreenModel(): AnimeLibraryScreenModel {
        return AnimeLibraryScreenModel(
            getLibraryAnime = getLibraryAnime,
            getCategories = getCategories,
            getTracksPerAnime = getTracksPerAnime,
            getNextEpisodes = mockk(relaxed = true),
            getEpisodesByAnimeId = mockk(relaxed = true),
            setSeenStatus = mockk(relaxed = true),
            updateAnime = mockk(relaxed = true),
            setAnimeCategories = mockk(relaxed = true),
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            backgroundCache = mockk(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            downloadCache = downloadCache,
            trackerManager = trackerManager,
            libraryDispatcher = testDispatcher,
        ).also(activeScreenModels::add)
    }

    private fun libraryAnime(
        id: Long,
        title: String,
        source: Long = 1L,
    ): LibraryAnime {
        return LibraryAnime(
            anime = Anime.create().copy(
                id = id,
                title = title,
                url = "https://example.com/$id",
                source = source,
                favorite = true,
            ),
            category = 0L,
            totalCount = 10L,
            seenCount = 1L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
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
