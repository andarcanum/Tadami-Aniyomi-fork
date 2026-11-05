package eu.kanade.tachiyomi.ui.library

import android.content.Context
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryScreenModel
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibrarySettingsScreenModel
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryScreenModel
import eu.kanade.tachiyomi.ui.library.manga.MangaLibrarySettingsScreenModel
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryScreenModel
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.GetVisibleNovelCategories
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.novel.interactor.GetTracksPerNovel
import tachiyomi.domain.track.novel.model.NovelTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

/**
 * Regression net for the v0.62.8 "Manga section spins forever after finishing a manhwa" report.
 *
 * The tab-held shared library screen models are constructed MANUALLY (never registered with
 * Voyager's ScreenModelStore). For an unregistered model, `ScreenModelStore.getDependencyKey`
 * resolves its `screenModelScope` dependency under `lastScreenModelKey` - the key of whatever
 * screen model was remembered LAST app-wide (in the reported flow: the pushed MangaScreen's
 * model). When that screen pops, `ScreenModelStore.onDispose` sweeps every dependency whose key
 * starts with the screen key and cancels the borrowed scope: all library pipelines die silently
 * (CancellationException, no crash), later `setLibraryPipelineActive(true)` writes are no-ops
 * (MutableStateFlow dedup + dead collector) and any section that had not emitted yet stays on
 * LoadingScreen until a process restart.
 *
 * The tests reproduce the sweep through `ScreenModelStore.onDisposeNavigator(prefix)` (the same
 * private `disposeHolder` prefix sweep the per-screen disposal runs) and assert the shared
 * models neither register under the foreign key nor lose their pipelines when it is swept.
 */
@OptIn(InternalVoyagerApi::class)
class LibrarySharedModelScopeTest {

    private companion object {
        const val FOREIGN_NAVIGATOR_KEY = "borrowedScopeSweepTestNavigator"
        const val FOREIGN_MODEL_KEY = "$FOREIGN_NAVIGATOR_KEY:ForeignScreen:ScreenModel:default"

        /**
         * 'lastScreenModelKey' / 'dependencies' are Kotlin-internal in Voyager's ScreenModelStore
         * (public in bytecode); the test reads them reflectively to stage and inspect the exact
         * borrowed-key state the production flow produces.
         */
        @Suppress("UNCHECKED_CAST")
        fun storeLastKeyFlow(): MutableStateFlow<String?> {
            val field = ScreenModelStore::class.java.getDeclaredField("lastScreenModelKey")
            field.isAccessible = true
            return field.get(ScreenModelStore) as MutableStateFlow<String?>
        }

        @Suppress("UNCHECKED_CAST")
        fun storeDependencyKeys(): Set<String> {
            val field = ScreenModelStore::class.java.getDeclaredField("dependencies")
            field.isAccessible = true
            return (field.get(ScreenModelStore) as Map<String, Any>).keys.toSet()
        }
    }

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var basePreferences: BasePreferences
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var trackerManager: TrackerManager
    private lateinit var mangaDownloadCache: MangaDownloadCache
    private lateinit var animeDownloadCache: AnimeDownloadCache
    private lateinit var novelDownloadCache: NovelDownloadCache
    private lateinit var novelSourceManager: NovelSourceManager
    private var previousLastScreenModelKey: String? = null
    private val activeModels = mutableListOf<cafe.adriel.voyager.core.model.ScreenModel>()

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        val preferenceStore = FakePreferenceStore()
        basePreferences = BasePreferences(
            context = mockk<Context>(relaxed = true),
            preferenceStore = preferenceStore,
        )
        libraryPreferences = LibraryPreferences(preferenceStore)

        trackerManager = mockk()
        every { trackerManager.loggedInTrackersFlow() } returns MutableStateFlow(emptyList<BaseTracker>())
        every { trackerManager.loggedInTrackers() } returns emptyList()

        mangaDownloadCache = mockk()
        every { mangaDownloadCache.changes } returns MutableSharedFlow<Unit>(replay = 1)
            .also { it.tryEmit(Unit) }
        animeDownloadCache = mockk()
        every { animeDownloadCache.changes } returns MutableSharedFlow<Unit>(replay = 1)
            .also { it.tryEmit(Unit) }
        novelDownloadCache = mockk(relaxed = true)
        every { novelDownloadCache.downloadedIds } returns MutableStateFlow(emptySet())
        novelSourceManager = mockk(relaxed = true)
        runCatching { Injekt.get<NovelSourceManager>() }
            .getOrElse { Injekt.addSingleton(fullType<NovelSourceManager>(), novelSourceManager) }

        // The store points at a foreign (pushed) screen's model key while the shared models are
        // created - exactly the SHORTCUT_MANGA flow: the library tab first composes under the
        // MangaScreen push/pop transition.
        previousLastScreenModelKey = storeLastKeyFlow().value
        storeLastKeyFlow().value = FOREIGN_MODEL_KEY
    }

    @AfterEach
    fun tearDown() {
        activeModels.forEach { it.onDispose() }
        activeModels.clear()
        testDispatcher.scheduler.advanceUntilIdle()
        storeLastKeyFlow().value = previousLastScreenModelKey
        Dispatchers.resetMain()
    }

    @Test
    fun `manga library pipeline survives the sweep of the borrowed store key`() = runTest(testDispatcher) {
        val screenModel = mangaLibraryScreenModel()

        // The foreign screen pops: the store sweeps everything registered under its key prefix.
        ScreenModelStore.onDisposeNavigator(FOREIGN_NAVIGATOR_KEY)

        screenModel.setLibraryPipelineActive(true)
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isLoading shouldBe false
    }

    @Test
    fun `anime library pipeline survives the sweep of the borrowed store key`() = runTest(testDispatcher) {
        val screenModel = animeLibraryScreenModel()

        ScreenModelStore.onDisposeNavigator(FOREIGN_NAVIGATOR_KEY)

        screenModel.setLibraryPipelineActive(true)
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isLoading shouldBe false
    }

    @Test
    fun `novel library pipeline survives the sweep of the borrowed store key`() = runTest(testDispatcher) {
        val screenModel = novelLibraryScreenModel()

        ScreenModelStore.onDisposeNavigator(FOREIGN_NAVIGATOR_KEY)

        screenModel.setLibraryPipelineActive(true)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isLoading shouldBe false
    }

    @Test
    fun `shared library models register no dependency under the foreign screen key`() {
        // Structural pin of the mechanism: before the fix every one of these constructors
        // registered "<FOREIGN_MODEL_KEY>:ScreenModelCoroutineScope" (and the Io scope for
        // settings actions) in the store, handing the models' lifetime to an unrelated screen.
        activeModels += mangaLibraryScreenModel()
        activeModels += animeLibraryScreenModel()
        activeModels += novelLibraryScreenModel()
        activeModels += MangaLibrarySettingsScreenModel(
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            setMangaDisplayMode = mockk(relaxed = true),
            setSortModeForCategory = mockk(relaxed = true),
            trackerManager = trackerManager,
            achievementHandler = mockk(relaxed = true),
        )
        activeModels += AnimeLibrarySettingsScreenModel(
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            setAnimeDisplayMode = mockk(relaxed = true),
            setSortModeForCategory = mockk(relaxed = true),
            trackerManager = trackerManager,
            achievementHandler = mockk(relaxed = true),
        )

        storeDependencyKeys().none { it.startsWith(FOREIGN_NAVIGATOR_KEY) } shouldBe true
    }

    private fun mangaLibraryScreenModel(): MangaLibraryScreenModel {
        val mangaFlow = MutableStateFlow(emptyList<tachiyomi.domain.library.manga.LibraryManga>())
        val seriesFlow =
            MutableStateFlow(emptyList<tachiyomi.domain.series.manga.model.LibraryMangaSeries>())
        val getLibraryManga = mockk<GetLibraryManga>()
        every { getLibraryManga.subscribe() } returns mangaFlow
        val getLibraryMangaSeries = mockk<tachiyomi.domain.series.manga.interactor.GetLibraryMangaSeries>()
        every { getLibraryMangaSeries.subscribe() } returns seriesFlow
        val getMangaIdsInAnySeries = mockk<tachiyomi.domain.series.manga.interactor.GetMangaIdsInAnySeries>()
        every { getMangaIdsInAnySeries.subscribe() } returns MutableStateFlow(emptySet())
        val getCategories = mockk<tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories>()
        every { getCategories.subscribe() } returns MutableStateFlow(listOf(category()))
        val getTracksPerManga = mockk<GetTracksPerManga>()
        every { getTracksPerManga.subscribe() } returns MutableStateFlow(emptyMap<Long, List<MangaTrack>>())

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
            sourceManager = mockk<MangaSourceManager>(relaxed = true),
            downloadManager = mockk<MangaDownloadManager>(relaxed = true),
            downloadCache = mangaDownloadCache,
            trackerManager = trackerManager,
            startActive = false,
            libraryDispatcher = testDispatcher,
        ).also(activeModels::add)
    }

    private fun animeLibraryScreenModel(): AnimeLibraryScreenModel {
        val animeFlow = MutableStateFlow(emptyList<tachiyomi.domain.library.anime.LibraryAnime>())
        val getLibraryAnime = mockk<GetLibraryAnime>()
        every { getLibraryAnime.subscribe() } returns animeFlow
        val getLibraryAnimeSeries = mockk<tachiyomi.domain.series.anime.interactor.GetLibraryAnimeSeries>()
        every { getLibraryAnimeSeries.subscribe() } returns MutableStateFlow(emptyList())
        val getAnimeIdsInAnySeries = mockk<tachiyomi.domain.series.anime.interactor.GetAnimeIdsInAnySeries>()
        every { getAnimeIdsInAnySeries.subscribe() } returns MutableStateFlow(emptySet())
        val getCategories = mockk<GetVisibleAnimeCategories>()
        every { getCategories.subscribe() } returns MutableStateFlow(listOf(category()))
        val getTracksPerAnime = mockk<GetTracksPerAnime>()
        every { getTracksPerAnime.subscribe() } returns MutableStateFlow(emptyMap<Long, List<AnimeTrack>>())

        return AnimeLibraryScreenModel(
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
            createAnimeSeries = mockk(relaxed = true),
            addAnimesToSeries = mockk(relaxed = true),
            updateAnimeSeries = mockk(relaxed = true),
            deleteAnimeSeries = mockk(relaxed = true),
            removeAnimeFromSeries = mockk(relaxed = true),
            preferences = basePreferences,
            libraryPreferences = libraryPreferences,
            coverCache = mockk(relaxed = true),
            backgroundCache = mockk(relaxed = true),
            sourceManager = mockk<AnimeSourceManager>(relaxed = true),
            downloadManager = mockk<AnimeDownloadManager>(relaxed = true),
            downloadCache = animeDownloadCache,
            trackerManager = trackerManager,
            startActive = false,
            libraryDispatcher = testDispatcher,
        ).also(activeModels::add)
    }

    private fun novelLibraryScreenModel(): NovelLibraryScreenModel {
        val novelFlow = MutableStateFlow(emptyList<tachiyomi.domain.library.novel.LibraryNovel>())
        val getLibraryNovel = mockk<GetLibraryNovel>()
        every { getLibraryNovel.subscribe() } returns novelFlow
        val getLibraryNovelSeries = mockk<tachiyomi.domain.series.novel.interactor.GetLibraryNovelSeries>()
        every { getLibraryNovelSeries.subscribe() } returns MutableStateFlow(emptyList())
        val getNovelIdsInAnySeries = mockk<tachiyomi.domain.series.novel.interactor.GetNovelIdsInAnySeries>()
        every { getNovelIdsInAnySeries.subscribe() } returns MutableStateFlow(emptySet())
        val getVisibleNovelCategories = mockk<GetVisibleNovelCategories>()
        every { getVisibleNovelCategories.subscribe() } returns
            MutableStateFlow(listOf(NovelCategory.createDefault(0L)))
        val getTracksPerNovel = mockk<GetTracksPerNovel>()
        every { getTracksPerNovel.subscribe() } returns MutableStateFlow(emptyMap<Long, List<NovelTrack>>())
        val getNovelCategories = mockk<GetNovelCategories>()
        coEvery { getNovelCategories.await(any<Long>()) } returns emptyList()
        coEvery { getNovelCategories.await() } returns emptyList<NovelCategory>()
        val chapterRepository = mockk<NovelChapterRepository>()
        coEvery { chapterRepository.getChapterByNovelId(any(), any()) } returns emptyList()

        return NovelLibraryScreenModel(
            getLibraryNovel = getLibraryNovel,
            getLibraryNovelSeries = getLibraryNovelSeries,
            getNovelIdsInAnySeries = getNovelIdsInAnySeries,
            deleteNovelSeries = mockk(relaxed = true),
            createNovelSeries = mockk(relaxed = true),
            addNovelsToSeries = mockk(relaxed = true),
            updateNovelSeries = mockk(relaxed = true),
            getNovelCategories = getNovelCategories,
            getVisibleNovelCategories = getVisibleNovelCategories,
            getTracksPerNovel = getTracksPerNovel,
            setNovelCategories = mockk(relaxed = true),
            updateNovel = mockk(relaxed = true),
            chapterRepository = chapterRepository,
            getNovelBookState = mockk { coEvery { await(any()) } returns null },
            novelHistoryRepository = mockk(relaxed = true),
            basePreferences = basePreferences,
            libraryPreferences = libraryPreferences,
            sourceManager = novelSourceManager,
            downloadCache = novelDownloadCache,
            searchDebounceMillis = 0L,
            trackerManager = trackerManager,
            startActive = false,
            libraryDispatcher = testDispatcher,
        ).also(activeModels::add)
    }

    private fun category(id: Long = 0L, name: String = "Default", flags: Long = 0L): Category {
        return Category(
            id = id,
            name = name,
            order = 0,
            flags = flags,
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
