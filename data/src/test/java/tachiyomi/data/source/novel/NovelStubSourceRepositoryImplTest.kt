package tachiyomi.data.source.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.novel.data.NovelDatabase

class NovelStubSourceRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: NovelDatabase
    private lateinit var handler: AndroidNovelDatabaseHandler
    private lateinit var repository: NovelStubSourceRepositoryImpl

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
        repository = NovelStubSourceRepositoryImpl(handler)
    }

    @Test
    fun `upsert and get stub source`() = runTest {
        repository.upsertStubNovelSource(1L, "en", "Novel")

        val source = checkNotNull(repository.getStubNovelSource(1L))

        source.id shouldBe 1L
        source.lang shouldBe "en"
        source.name shouldBe "Novel"
    }

    @Test
    fun `subscribe returns all stub sources`() = runTest {
        repository.upsertStubNovelSource(1L, "en", "Novel")

        val sources = repository.subscribeAllNovel().first()

        sources.size shouldBe 1
        sources.first().name shouldBe "Novel"
    }
}
