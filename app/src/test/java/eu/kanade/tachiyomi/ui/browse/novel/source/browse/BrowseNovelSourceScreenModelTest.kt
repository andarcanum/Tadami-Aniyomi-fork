package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rx.Observable
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get
import kotlin.system.measureTimeMillis

class BrowseNovelSourceScreenModelTest {
    private val activeScreenModels = mutableListOf<BrowseNovelSourceScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        ensureUiPreferences()
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        activeScreenModels.clear()
        runBlocking {
            repeat(5) { yield() }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `openNovel stores novel and returns id`() {
        runBlocking {
            val source = FakeNovelCatalogueSource(id = 1L, name = "Novel", lang = "en")
            val sourceManager = FakeNovelSourceManager(source)
            val prefs = SourcePreferences(FakePreferenceStore())
            val remoteNovel = SNovel.create().apply {
                url = "/novel"
                title = "Novel"
            }
            val networkToLocal = NetworkToLocalNovel(FakeNovelRepository(insertId = 42L))
            val getRemoteNovel = GetRemoteNovel(
                repository = FakeNovelSourceRepository(),
            )

            val screenModel = track(
                BrowseNovelSourceScreenModel(
                    sourceId = 1L,
                    listingQuery = null,
                    sourceManager = sourceManager,
                    getRemoteNovel = getRemoteNovel,
                    sourcePreferences = prefs,
                    getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(FakeNovelRepository()),
                    networkToLocalNovel = networkToLocal,
                ),
            )

            val result = screenModel.openNovel(remoteNovel)

            result shouldBe 42L
        }
    }

    @Test
    fun `addNovelToLibrary marks local novel as favorite`() {
        runBlocking {
            val source = FakeNovelCatalogueSource(id = 1L, name = "Novel", lang = "en")
            val sourceManager = FakeNovelSourceManager(source)
            val prefs = SourcePreferences(FakePreferenceStore())
            val remoteNovel = SNovel.create().apply {
                url = "/novel-library-add"
                title = "Novel"
            }
            val novelRepository = FakeNovelRepository(insertId = 77L)
            val networkToLocal = NetworkToLocalNovel(novelRepository)
            val getRemoteNovel = GetRemoteNovel(
                repository = FakeNovelSourceRepository(),
            )

            val screenModel = track(
                BrowseNovelSourceScreenModel(
                    sourceId = 1L,
                    listingQuery = null,
                    sourceManager = sourceManager,
                    getRemoteNovel = getRemoteNovel,
                    sourcePreferences = prefs,
                    getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(novelRepository),
                    networkToLocalNovel = networkToLocal,
                    updateNovel = UpdateNovel(novelRepository),
                ),
            )

            val result = screenModel.addNovelToLibrary(remoteNovel)

            result shouldBe true
            novelRepository.lastNovelUpdate?.id shouldBe 77L
            novelRepository.lastNovelUpdate?.favorite shouldBe true
        }
    }

    @Test
    fun `getDuplicateLibraryNovel returns matching favorite novel`() {
        runBlocking {
            val source = FakeNovelCatalogueSource(id = 1L, name = "Novel", lang = "en")
            val sourceManager = FakeNovelSourceManager(source)
            val prefs = SourcePreferences(FakePreferenceStore())
            val duplicate = Novel.create().copy(
                id = 90L,
                title = "Same Title",
                favorite = true,
            )
            val novelRepository = FakeNovelRepository(
                insertId = 77L,
                favorites = listOf(duplicate),
            )
            val getRemoteNovel = GetRemoteNovel(
                repository = FakeNovelSourceRepository(),
            )

            val screenModel = track(
                BrowseNovelSourceScreenModel(
                    sourceId = 1L,
                    listingQuery = null,
                    sourceManager = sourceManager,
                    getRemoteNovel = getRemoteNovel,
                    sourcePreferences = prefs,
                    getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(novelRepository),
                    networkToLocalNovel = NetworkToLocalNovel(novelRepository),
                    getNovelFavoritesInteractor = tachiyomi.domain.entries.novel.interactor.GetNovelFavorites(
                        novelRepository,
                    ),
                ),
            )

            val localNovel = Novel.create().copy(
                id = 77L,
                title = "same title",
                favorite = false,
            )
            val result = screenModel.getDuplicateLibraryNovel(localNovel)

            result?.id shouldBe 90L
        }
    }

    @Test
    fun `applyFilters increments filter version for popular listing`() {
        val source = FakeNovelCatalogueSource(id = 1L, name = "Novel", lang = "en")
        val sourceManager = FakeNovelSourceManager(source)
        val prefs = SourcePreferences(FakePreferenceStore())
        val networkToLocal = NetworkToLocalNovel(FakeNovelRepository(insertId = 1L))
        val getRemoteNovel = GetRemoteNovel(repository = FakeNovelSourceRepository())

        val screenModel = track(
            BrowseNovelSourceScreenModel(
                sourceId = 1L,
                listingQuery = GetRemoteNovel.QUERY_POPULAR,
                sourceManager = sourceManager,
                getRemoteNovel = getRemoteNovel,
                sourcePreferences = prefs,
                getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(FakeNovelRepository()),
                networkToLocalNovel = networkToLocal,
            ),
        )

        screenModel.state.value.filterVersion shouldBe 0
        screenModel.applyFilters()
        val state = screenModel.state.value
        state.filterVersion shouldBe 1
        state.listing shouldBe BrowseNovelSourceScreenModel.Listing.Popular
    }

    @Test
    fun `applyFilters keeps search query and bumps version`() {
        val source = FakeNovelCatalogueSource(id = 1L, name = "Novel", lang = "en")
        val sourceManager = FakeNovelSourceManager(source)
        val prefs = SourcePreferences(FakePreferenceStore())
        val networkToLocal = NetworkToLocalNovel(FakeNovelRepository(insertId = 1L))
        val getRemoteNovel = GetRemoteNovel(repository = FakeNovelSourceRepository())

        val screenModel = track(
            BrowseNovelSourceScreenModel(
                sourceId = 1L,
                listingQuery = null,
                sourceManager = sourceManager,
                getRemoteNovel = getRemoteNovel,
                sourcePreferences = prefs,
                getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(FakeNovelRepository()),
                networkToLocalNovel = networkToLocal,
            ),
        )

        screenModel.search(query = "ranobe")
        screenModel.applyFilters()

        val state = screenModel.state.value
        (state.listing as BrowseNovelSourceScreenModel.Listing.Search).query shouldBe "ranobe"
        state.toolbarQuery shouldBe "ranobe"
        state.filterVersion shouldBe 1
    }

    @Test
    fun `applyFilters keeps popular listing and bumps version when filters are available`() {
        runBlocking {
            val source = FakeNovelCatalogueSourceWithFilters(
                id = 1L,
                name = "Novel",
                lang = "en",
                filters = NovelFilterList(
                    object : NovelFilter.Select<String>(
                        name = "Sort",
                        values = arrayOf("Popular", "Latest"),
                        state = 0,
                    ) {},
                ),
            )
            val sourceManager = FakeNovelSourceManager(source)
            val prefs = SourcePreferences(FakePreferenceStore())
            val networkToLocal = NetworkToLocalNovel(FakeNovelRepository(insertId = 1L))
            val getRemoteNovel = GetRemoteNovel(repository = FakeNovelSourceRepository())

            val screenModel = track(
                BrowseNovelSourceScreenModel(
                    sourceId = 1L,
                    listingQuery = GetRemoteNovel.QUERY_POPULAR,
                    sourceManager = sourceManager,
                    getRemoteNovel = getRemoteNovel,
                    sourcePreferences = prefs,
                    getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(FakeNovelRepository()),
                    networkToLocalNovel = networkToLocal,
                ),
            )

            repeat(20) {
                if (screenModel.state.value.filters.isNotEmpty()) return@repeat
                delay(10)
            }

            val beforeVersion = screenModel.state.value.filterVersion
            screenModel.applyFilters()
            val state = screenModel.state.value

            state.listing shouldBe BrowseNovelSourceScreenModel.Listing.Popular
            state.filterVersion shouldBe beforeVersion + 1
        }
    }

    @Test
    fun `applyFilters does not expose internal latest query in toolbar`() {
        val source = FakeNovelCatalogueSource(id = 1L, name = "Novel", lang = "en")
        val sourceManager = FakeNovelSourceManager(source)
        val prefs = SourcePreferences(FakePreferenceStore())
        val networkToLocal = NetworkToLocalNovel(FakeNovelRepository(insertId = 1L))
        val getRemoteNovel = GetRemoteNovel(repository = FakeNovelSourceRepository())

        val screenModel = track(
            BrowseNovelSourceScreenModel(
                sourceId = 1L,
                listingQuery = GetRemoteNovel.QUERY_LATEST,
                sourceManager = sourceManager,
                getRemoteNovel = getRemoteNovel,
                sourcePreferences = prefs,
                getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(FakeNovelRepository()),
                networkToLocalNovel = networkToLocal,
            ),
        )

        val beforeVersion = screenModel.state.value.filterVersion
        screenModel.applyFilters()
        val state = screenModel.state.value

        state.listing shouldBe BrowseNovelSourceScreenModel.Listing.Latest
        state.toolbarQuery shouldBe null
        state.filterVersion shouldBe beforeVersion + 1
    }

    @Test
    fun `init does not block when source filter loading is slow`() {
        val source = SlowFilterNovelCatalogueSource(
            id = 1L,
            name = "Novel",
            lang = "en",
            delayMillis = 700L,
        )
        val sourceManager = FakeNovelSourceManager(source)
        val prefs = SourcePreferences(FakePreferenceStore())
        val networkToLocal = NetworkToLocalNovel(FakeNovelRepository(insertId = 1L))
        val getRemoteNovel = GetRemoteNovel(repository = FakeNovelSourceRepository())

        val elapsedMs = measureTimeMillis {
            track(
                BrowseNovelSourceScreenModel(
                    sourceId = 1L,
                    listingQuery = null,
                    sourceManager = sourceManager,
                    getRemoteNovel = getRemoteNovel,
                    sourcePreferences = prefs,
                    getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(FakeNovelRepository()),
                    networkToLocalNovel = networkToLocal,
                ),
            )
        }

        assertTrue(
            elapsedMs < 300L,
            "ScreenModel init blocked for ${elapsedMs}ms while loading filters",
        )
    }

    @Test
    fun `init bumps filter version after async filters are loaded`() {
        runBlocking {
            val source = FakeNovelCatalogueSourceWithFilters(
                id = 1L,
                name = "Novel",
                lang = "en",
                filters = NovelFilterList(
                    object : NovelFilter.Text("Keyword", "") {},
                ),
            )
            val sourceManager = FakeNovelSourceManager(source)
            val prefs = SourcePreferences(FakePreferenceStore())
            val networkToLocal = NetworkToLocalNovel(FakeNovelRepository(insertId = 1L))
            val getRemoteNovel = GetRemoteNovel(repository = FakeNovelSourceRepository())

            val screenModel = track(
                BrowseNovelSourceScreenModel(
                    sourceId = 1L,
                    listingQuery = GetRemoteNovel.QUERY_POPULAR,
                    sourceManager = sourceManager,
                    getRemoteNovel = getRemoteNovel,
                    sourcePreferences = prefs,
                    getNovelByUrlAndSourceId = GetNovelByUrlAndSourceId(FakeNovelRepository()),
                    networkToLocalNovel = networkToLocal,
                ),
            )

            repeat(20) {
                if (screenModel.state.value.filters.isNotEmpty()) return@repeat
                delay(10)
            }

            screenModel.state.value.filters.isNotEmpty() shouldBe true
            screenModel.state.value.filterVersion shouldBe 1
        }
    }

    private fun ensureUiPreferences() {
        runCatching { Injekt.get<UiPreferences>() }
            .getOrElse {
                Injekt.addSingleton(fullType<UiPreferences>(), UiPreferences(InMemoryPreferenceStore()))
            }
    }

    private fun track(screenModel: BrowseNovelSourceScreenModel): BrowseNovelSourceScreenModel {
        return screenModel.also(activeScreenModels::add)
    }

    private class FakeNovelRepository : tachiyomi.domain.entries.novel.repository.NovelRepository {
        constructor(
            insertId: Long? = null,
            favorites: List<Novel> = emptyList(),
        ) {
            this.insertId = insertId
            this.favorites = favorites
        }

        private val insertId: Long?
        private val favorites: List<Novel>
        var lastNovelUpdate: NovelUpdate? = null
        override suspend fun getNovelById(id: Long): Novel = Novel.create()
        override suspend fun getNovelByIdAsFlow(id: Long) = MutableStateFlow(Novel.create())
        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) =
            MutableStateFlow<Novel?>(null)
        override suspend fun getNovelFavorites(): List<Novel> = favorites
        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
        override suspend fun getLibraryNovel() = emptyList<tachiyomi.domain.library.novel.LibraryNovel>()
        override fun getLibraryNovelAsFlow() = MutableStateFlow(
            emptyList<tachiyomi.domain.library.novel.LibraryNovel>(),
        )
        override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())
        override suspend fun insertNovel(novel: Novel): Long? = insertId
        override suspend fun updateNovel(update: tachiyomi.domain.entries.novel.model.NovelUpdate): Boolean {
            lastNovelUpdate = update
            return true
        }
        override suspend fun updateAllNovel(
            novelUpdates: List<tachiyomi.domain.entries.novel.model.NovelUpdate>,
        ): Boolean = true
        override suspend fun resetNovelViewerFlags(): Boolean = true
    }

    private class FakeNovelSourceManager(
        private val source: NovelCatalogueSource,
    ) : NovelSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources: Flow<List<NovelCatalogueSource>> = MutableStateFlow(listOf(source))
        override fun get(sourceKey: Long): NovelSource? = if (sourceKey == source.id) source else null
        override fun getOrStub(sourceKey: Long): NovelSource = get(sourceKey)!!
        override fun getOnlineSources(): List<NovelHttpSource> = listOf(source as NovelHttpSource)
        override fun getCatalogueSources(): List<NovelCatalogueSource> = listOf(source)
        override fun getStubSources() = emptyList<tachiyomi.domain.source.novel.model.StubNovelSource>()
    }

    private class FakeNovelCatalogueSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
    ) : NovelHttpSource {
        override val supportsLatest: Boolean = true
        override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun fetchSearchNovels(
            page: Int,
            query: String,
            filters: NovelFilterList,
        ): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun getFilterList(): NovelFilterList = NovelFilterList()
        override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)
        override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
            Observable.just(emptyList())
        override fun fetchChapterText(chapter: SNovelChapter): Observable<String> = Observable.just("")
    }

    private class SlowFilterNovelCatalogueSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
        private val delayMillis: Long,
    ) : NovelHttpSource {
        override val supportsLatest: Boolean = true
        override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun fetchSearchNovels(
            page: Int,
            query: String,
            filters: NovelFilterList,
        ): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun getFilterList(): NovelFilterList {
            Thread.sleep(delayMillis)
            return NovelFilterList()
        }
        override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)
        override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
            Observable.just(emptyList())
        override fun fetchChapterText(chapter: SNovelChapter): Observable<String> = Observable.just("")
    }

    private class FakeNovelCatalogueSourceWithFilters(
        override val id: Long,
        override val name: String,
        override val lang: String,
        private val filters: NovelFilterList,
    ) : NovelHttpSource {
        override val supportsLatest: Boolean = true
        override fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun fetchSearchNovels(
            page: Int,
            query: String,
            filters: NovelFilterList,
        ): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
            Observable.just(NovelsPage(emptyList(), false))
        override fun getFilterList(): NovelFilterList = filters
        override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> = Observable.just(novel)
        override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
            Observable.just(emptyList())
        override fun fetchChapterText(chapter: SNovelChapter): Observable<String> = Observable.just("")
    }

    private class FakePreferenceStore : PreferenceStore {
        private val longs = mutableMapOf<String, Preference<Long>>()
        private val objects = mutableMapOf<String, Preference<Any>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            FakePreference(defaultValue)
        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            longs.getOrPut(key) { FakePreference(defaultValue) }
        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            FakePreference(defaultValue)
        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            FakePreference(defaultValue)
        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            FakePreference(defaultValue)
        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            FakePreference(defaultValue)

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return objects.getOrPut(key) { FakePreference(defaultValue as Any) } as Preference<T>
        }
        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }

    private class FakePreference<T>(
        initial: T,
    ) : Preference<T> {
        private val state = MutableStateFlow(initial)
        override fun key(): String = "fake"
        override fun get(): T = state.value
        override fun set(value: T) {
            state.value = value
        }
        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes() = state
        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope) = state
    }

    private class FakeNovelSourceRepository : NovelSourceRepository {
        override fun getNovelSources() = MutableStateFlow(emptyList<tachiyomi.domain.source.novel.model.Source>())
        override fun getOnlineNovelSources() = MutableStateFlow(emptyList<tachiyomi.domain.source.novel.model.Source>())
        override fun getNovelSourcesWithFavoriteCount() =
            MutableStateFlow(emptyList<Pair<tachiyomi.domain.source.novel.model.Source, Long>>())
        override fun getNovelSourcesWithNonLibraryNovels() =
            MutableStateFlow(emptyList<tachiyomi.domain.source.novel.model.NovelSourceWithCount>())
        override fun searchNovels(
            sourceId: Long,
            query: String,
            filterList: NovelFilterList,
        ) = TODO()
        override fun getPopularNovels(sourceId: Long, filterList: NovelFilterList) = TODO()
        override fun getLatestNovels(sourceId: Long, filterList: NovelFilterList) = TODO()
    }
}
