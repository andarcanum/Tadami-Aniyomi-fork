package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rx.Observable
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import eu.kanade.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.api.fullType

class BrowseNovelSourceScreenModelTest {

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        ensureUiPreferences()
    }

    @AfterEach
    fun tearDown() {
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

            val screenModel = BrowseNovelSourceScreenModel(
                sourceId = 1L,
                listingQuery = null,
                sourceManager = sourceManager,
                getRemoteNovel = getRemoteNovel,
                sourcePreferences = prefs,
                networkToLocalNovel = networkToLocal,
            )

            val result = screenModel.openNovel(remoteNovel)

            result shouldBe 42L
        }
    }

    private fun ensureUiPreferences() {
        runCatching { Injekt.get<UiPreferences>() }
            .getOrElse {
                Injekt.addSingleton(fullType<UiPreferences>(), UiPreferences(InMemoryPreferenceStore()))
            }
    }

    private class FakeNovelRepository : tachiyomi.domain.entries.novel.repository.NovelRepository {
        constructor(insertId: Long? = null) {
            this.insertId = insertId
        }

        private val insertId: Long?
        override suspend fun getNovelById(id: Long): Novel = Novel.create()
        override suspend fun getNovelByIdAsFlow(id: Long) = MutableStateFlow(Novel.create())
        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) =
            MutableStateFlow<Novel?>(null)
        override suspend fun getNovelFavorites(): List<Novel> = emptyList()
        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
        override suspend fun getLibraryNovel() = emptyList<tachiyomi.domain.library.novel.LibraryNovel>()
        override fun getLibraryNovelAsFlow() = MutableStateFlow(emptyList<tachiyomi.domain.library.novel.LibraryNovel>())
        override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())
        override suspend fun insertNovel(novel: Novel): Long? = insertId
        override suspend fun updateNovel(update: tachiyomi.domain.entries.novel.model.NovelUpdate): Boolean = true
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
        override fun getPopularNovels(sourceId: Long) = TODO()
        override fun getLatestNovels(sourceId: Long) = TODO()
    }
}
