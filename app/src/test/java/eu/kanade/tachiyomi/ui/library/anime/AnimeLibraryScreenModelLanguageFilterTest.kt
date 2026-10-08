package eu.kanade.tachiyomi.ui.library.anime

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
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
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack

class AnimeLibraryScreenModelLanguageFilterTest {

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
    fun `language filter keeps only entries from selected source languages`() = runTest(testDispatcher) {
        val english = libraryAnime(id = 1L, title = "English Anime", source = 1L)
        val japanese = libraryAnime(id = 2L, title = "Japanese Anime", source = 2L)
        animeFlow.value = listOf(english, japanese)
        every { sourceManager.getOrStub(1L).lang } returns "en"
        every { sourceManager.getOrStub(2L).lang } returns "ja"

        val screenModel = trackedAnimeLibraryScreenModel()

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleLanguage("ja")
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(
            animeItem(japanese),
        )
        screenModel.state.value.hasActiveFilters shouldBe true
    }

    @Test
    fun `clearing language filter shows all entries again`() = runTest(testDispatcher) {
        val english = libraryAnime(id = 1L, title = "English Anime", source = 1L)
        val japanese = libraryAnime(id = 2L, title = "Japanese Anime", source = 2L)
        animeFlow.value = listOf(english, japanese)
        every { sourceManager.getOrStub(1L).lang } returns "en"
        every { sourceManager.getOrStub(2L).lang } returns "ja"

        val screenModel = trackedAnimeLibraryScreenModel()

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleLanguage("ja")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.clearLanguageFilter()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactlyInAnyOrder(
            animeItem(english),
            animeItem(japanese),
        )
        screenModel.state.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `library languages lists distinct sorted source languages`() = runTest(testDispatcher) {
        val a = libraryAnime(id = 1L, title = "A", source = 1L)
        val b = libraryAnime(id = 2L, title = "B", source = 2L)
        val c = libraryAnime(id = 3L, title = "C", source = 1L)
        animeFlow.value = listOf(a, b, c)
        every { sourceManager.getOrStub(1L).lang } returns "en"
        every { sourceManager.getOrStub(2L).lang } returns "ja"

        val screenModel = trackedAnimeLibraryScreenModel()

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.libraryLanguages shouldBe listOf("en", "ja")
    }

    @Test
    fun `setAllLanguages replaces the language filter set`() = runTest(testDispatcher) {
        val english = libraryAnime(id = 1L, title = "English Anime", source = 1L)
        val japanese = libraryAnime(id = 2L, title = "Japanese Anime", source = 2L)
        animeFlow.value = listOf(english, japanese)
        every { sourceManager.getOrStub(1L).lang } returns "en"
        every { sourceManager.getOrStub(2L).lang } returns "ja"

        val screenModel = trackedAnimeLibraryScreenModel()

        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.setAllLanguages(setOf("ja"))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldContainExactly(
            animeItem(japanese),
        )
        screenModel.state.value.hasActiveFilters shouldBe true
    }

    private fun trackedAnimeLibraryScreenModel(): AnimeLibraryScreenModel {
        return AnimeLibraryScreenModel(
            getLibraryAnime = getLibraryAnime,
            getLibraryAnimeSeries = mockk { every { subscribe() } returns MutableStateFlow(emptyList()) },
            getAnimeIdsInAnySeries = mockk { every { subscribe() } returns MutableStateFlow(emptySet()) },
            getCategories = getCategories,
            getTracksPerAnime = getTracksPerAnime,
            getNextEpisodes = mockk(relaxed = true),
            getEpisodesByAnimeId = mockk(relaxed = true),
            setSeenStatus = mockk(relaxed = true),
            updateAnime = mockk(relaxed = true),
            setAnimeCategories = mockk(relaxed = true),
            createAnimeSeries = mockk(relaxed = true),
            addAnimesToSeries = mockk(relaxed = true),
            updateAnimeSeries = mockk(relaxed = true),
            deleteAnimeSeries = mockk(relaxed = true),
            removeAnimeFromSeries = mockk(relaxed = true),
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

    private fun animeItem(libraryAnime: LibraryAnime): AnimeLibraryItem {
        // Mirrors the item shape produced by the pipeline with default badge preferences:
        // downloadBadge=false, unreadBadge=true, localBadge=true, languageBadge=false.
        return AnimeLibraryItem(
            libraryAnime = libraryAnime,
            downloadCount = 0,
            unseenCount = libraryAnime.unseenCount,
            isLocal = false,
            sourceLanguage = "",
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
