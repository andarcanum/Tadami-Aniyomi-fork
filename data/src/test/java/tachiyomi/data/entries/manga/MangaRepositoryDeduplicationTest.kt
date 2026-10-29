package tachiyomi.data.entries.manga

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import `data`.History
import `data`.Mangas
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.data.Database as MangaDatabase

class MangaRepositoryDeduplicationTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var handler: AndroidMangaDatabaseHandler
    private lateinit var repository: MangaRepositoryImpl

    @BeforeEach
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDatabase.Schema.create(driver)
        val database = MangaDatabase(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        handler = AndroidMangaDatabaseHandler(
            db = database,
            driver = driver,
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )
        repository = MangaRepositoryImpl(handler, AchievementEventBus())
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `insertManga with existing url and source returns existing id instead of inserting duplicate`() = runTest {
        val manga1 = Manga.create().copy(
            url = "/manga/sololeveling",
            title = "Solo Leveling",
            source = 10L,
            favorite = true,
        )
        val id1 = repository.insertManga(manga1)

        val manga2 = Manga.create().copy(
            url = "/manga/sololeveling",
            title = "Solo Leveling Duplicate",
            source = 10L,
            favorite = false,
        )
        val id2 = repository.insertManga(manga2)

        id1 shouldBe id2

        val all = repository.getMangaFavorites()
        all.size shouldBe 1
    }

    @Test
    fun `insertNetworkMangas with duplicate urls within same batch returns same id and single record`() = runTest {
        val item1 = Manga.create().copy(url = "/manga/batch-duplicate", title = "Batch 1", source = 10L)
        val item2 = Manga.create().copy(url = "/manga/batch-duplicate", title = "Batch 2", source = 10L)

        val results = repository.insertNetworkMangas(listOf(item1, item2))
        results.size shouldBe 2
        results[0].id shouldBe results[1].id

        val fromDb = repository.getMangaByUrlAndSourceId("/manga/batch-duplicate", 10L)
        fromDb?.id shouldBe results[0].id
    }
}
