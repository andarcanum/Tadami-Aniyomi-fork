package mihon.core.migration.migrations

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import `data`.History
import `data`.Mangas
import dataanime.Animehistory
import dataanime.Animes
import datanovel.Novel_history
import datanovel.Novels
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.migration.MigrationContext
import mihon.data.extension.service.ExtensionStoreService
import mihon.data.repository.anime.AnimeExtensionStoreRepositoryImpl
import mihon.data.repository.manga.MangaExtensionStoreRepositoryImpl
import mihon.data.repository.novel.NovelExtensionStoreRepositoryImpl
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.novel.data.NovelDatabase
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import tachiyomi.data.Database as MangaDatabase

class ExtensionRepoToStoreMigrationIntegrationTest {

    private lateinit var mangaDriver: JdbcSqliteDriver
    private lateinit var animeDriver: JdbcSqliteDriver
    private lateinit var novelDriver: JdbcSqliteDriver
    private lateinit var mangaDatabase: MangaDatabase
    private lateinit var animeDatabase: AnimeDatabase
    private lateinit var novelDatabase: NovelDatabase
    private lateinit var mangaHandler: AndroidMangaDatabaseHandler
    private lateinit var animeHandler: AndroidAnimeDatabaseHandler
    private lateinit var novelHandler: AndroidNovelDatabaseHandler

    @BeforeEach
    fun setUp() {
        Class.forName("org.sqlite.JDBC")

        mangaDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        animeDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        novelDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDatabase.Schema.create(mangaDriver)
        AnimeDatabase.Schema.create(animeDriver)
        NovelDatabase.Schema.create(novelDriver)

        mangaDatabase = MangaDatabase(
            driver = mangaDriver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        animeDatabase = AnimeDatabase(
            driver = animeDriver,
            animehistoryAdapter = Animehistory.Adapter(last_seenAdapter = DateColumnAdapter),
            episodesAdapter = dataanime.Episodes.Adapter(memoAdapter = MemoColumnAdapter),
            animesAdapter = Animes.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = FetchTypeColumnAdapter,
            ),
        )
        novelDatabase = NovelDatabase(
            driver = novelDriver,
            novel_historyAdapter = Novel_history.Adapter(last_readAdapter = DateColumnAdapter),
            novel_chaptersAdapter = datanovel.Novel_chapters.Adapter(memoAdapter = MemoColumnAdapter),
            novelsAdapter = Novels.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )

        mangaHandler = AndroidMangaDatabaseHandler(
            db = mangaDatabase,
            driver = mangaDriver,
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )
        animeHandler = AndroidAnimeDatabaseHandler(
            db = animeDatabase,
            driver = animeDriver,
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )
        novelHandler = AndroidNovelDatabaseHandler(
            db = novelDatabase,
            driver = novelDriver,
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )

        mangaDatabase.extension_reposQueries.insert(
            base_url = "https://manga.repo.example",
            name = "Manga Repo",
            short_name = "manga",
            website = "https://manga.repo.example",
            fingerprint = "MANGA-FP",
        )
        animeDatabase.extension_reposQueries.insert(
            base_url = "https://anime.repo.example",
            name = "Anime Repo",
            short_name = null,
            website = "https://anime.repo.example",
            fingerprint = "ANIME-FP",
        )
        novelDatabase.novel_extension_reposQueries.insert(
            base_url = "https://novel.repo.example",
            name = "Novel Repo",
            short_name = "novel",
            website = "https://novel.repo.example",
            fingerprint = "NOVEL-FP",
        )

        val storeService = ExtensionStoreService(OkHttpClient(), Json { ignoreUnknownKeys = true }, ProtoBuf)
        Injekt.addSingleton(fullType<MangaDatabaseHandler>(), mangaHandler)
        Injekt.addSingleton(fullType<AnimeDatabaseHandler>(), animeHandler)
        Injekt.addSingleton(fullType<NovelDatabaseHandler>(), novelHandler)
        Injekt.addSingleton(
            fullType<MangaExtensionStoreRepository>(),
            MangaExtensionStoreRepositoryImpl(mangaHandler, storeService),
        )
        Injekt.addSingleton(
            fullType<AnimeExtensionStoreRepository>(),
            AnimeExtensionStoreRepositoryImpl(animeHandler, storeService),
        )
        Injekt.addSingleton(
            fullType<NovelExtensionStoreRepository>(),
            NovelExtensionStoreRepositoryImpl(novelHandler, storeService),
        )
    }

    @AfterEach
    fun tearDown() {
        mangaDriver.close()
        animeDriver.close()
        novelDriver.close()
    }

    @Test
    fun `legacy repo to store migration (ALWAYS) copies previously added repos when stores empty`() = runTest {
        val result = ExtensionRepoToStoreMigration().invoke(MigrationContext(dryrun = false))

        result shouldBe true

        val mangaStores = mangaHandler.awaitList { db -> db.extension_storeQueries.getAll() }
        val animeStores = animeHandler.awaitList { db -> db.extension_storeQueries.getAll() }
        val novelStores = novelHandler.awaitList { db -> db.extension_storeQueries.getAll() }

        mangaStores.single().index_url shouldBe "https://manga.repo.example/repo.json"
        mangaStores.single().signing_key shouldBe "MANGA-FP"
        animeStores.single().index_url shouldBe "https://anime.repo.example/repo.json"
        novelStores.single().index_url shouldBe "https://novel.repo.example"
        novelStores.single().is_legacy shouldBe true
    }
}
