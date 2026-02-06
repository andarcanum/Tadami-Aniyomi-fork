package tachiyomi.data.source.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rx.Observable
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.novel.data.NovelDatabase

class NovelSourceRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: NovelDatabase
    private lateinit var handler: AndroidNovelDatabaseHandler
    private lateinit var sourceManager: FakeNovelSourceManager
    private lateinit var repository: NovelSourceRepository

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NovelDatabase.Schema.create(driver)
        database = NovelDatabase(
            driver = driver,
            novel_historyAdapter = Novel_history.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            novelsAdapter = Novels.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
        )
        handler = AndroidNovelDatabaseHandler(
            db = database,
            driver = driver,
            queryDispatcher = UnconfinedTestDispatcher(),
            transactionDispatcher = UnconfinedTestDispatcher(),
        )
        sourceManager = FakeNovelSourceManager()
        repository = NovelSourceRepositoryImpl(sourceManager, handler)
    }

    @Test
    fun `getNovelSources maps catalogue sources`() = runTest {
        val source = FakeNovelCatalogueSource(id = 10L, name = "Novel", lang = "en")
        sourceManager.emitSources(listOf(source))

        val sources = repository.getNovelSources().first()

        sources.first() shouldBe Source(
            id = 10L,
            lang = "en",
            name = "Novel",
            supportsLatest = true,
            isStub = false,
        )
    }

    @Test
    fun `getNovelSourcesWithFavoriteCount maps counts`() = runTest {
        val source = FakeNovelCatalogueSource(id = 10L, name = "Novel", lang = "en")
        sourceManager.emitSources(listOf(source))

        database.novelsQueries.insert(
            source = 10L,
            url = "/novel",
            author = "Author",
            description = "Desc",
            genre = listOf("Action"),
            title = "Title",
            status = 1,
            thumbnailUrl = null,
            favorite = true,
            lastUpdate = 0,
            nextUpdate = 0,
            initialized = false,
            viewerFlags = 0,
            chapterFlags = 0,
            coverLastModified = 0,
            dateAdded = 0,
            updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0,
            version = 0,
        )

        val result = repository.getNovelSourcesWithFavoriteCount().first()

        result.first().second shouldBe 1L
    }

    private class FakeNovelSourceManager : NovelSourceManager {
        private val state = MutableStateFlow<List<NovelCatalogueSource>>(emptyList())

        override val isInitialized = MutableStateFlow(true)

        override val catalogueSources: Flow<List<NovelCatalogueSource>> = state

        fun emitSources(list: List<NovelCatalogueSource>) {
            state.value = list
        }

        override fun get(sourceKey: Long): NovelSource? = state.value.firstOrNull { it.id == sourceKey }

        override fun getOrStub(sourceKey: Long): NovelSource = get(sourceKey)!!

        override fun getOnlineSources(): List<NovelHttpSource> = state.value.filterIsInstance<NovelHttpSource>()

        override fun getCatalogueSources(): List<NovelCatalogueSource> = state.value

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

        override fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
            Observable.just("")
    }
}
