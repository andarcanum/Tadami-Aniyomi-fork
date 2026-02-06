package tachiyomi.data.handlers.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import datanovel.Novel_history
import datanovel.Novels
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.novel.data.NovelDatabase

class NovelDatabaseHandlerTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: NovelDatabase
    private lateinit var handler: NovelDatabaseHandler
    private val testDispatcher = UnconfinedTestDispatcher()

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
            queryDispatcher = testDispatcher,
            transactionDispatcher = testDispatcher,
        )
    }

    @Test
    fun `awaitList returns rows from novel queries`() = runTest(testDispatcher) {
        database.novelsQueries.insert(
            source = 1,
            url = "/novel",
            author = "Author",
            description = "Desc",
            genre = listOf("Action"),
            title = "Title",
            status = 1,
            thumbnailUrl = null,
            favorite = false,
            lastUpdate = 0,
            nextUpdate = 0,
            initialized = false,
            viewerFlags = 0,
            chapterFlags = 0,
            coverLastModified = 0,
            dateAdded = 0,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0,
            version = 0,
        )

        val rows = handler.awaitList { novelsQueries.getAllNovel() }

        rows.size shouldBe 1
        rows.first().title shouldBe "Title"
    }
}
