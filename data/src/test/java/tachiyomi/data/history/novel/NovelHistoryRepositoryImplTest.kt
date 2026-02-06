package tachiyomi.data.history.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.novel.data.NovelDatabase
import java.util.Date

class NovelHistoryRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: NovelDatabase
    private lateinit var handler: AndroidNovelDatabaseHandler
    private lateinit var repository: NovelHistoryRepositoryImpl
    private var novelId: Long = -1
    private var chapterId: Long = -1

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
            queryDispatcher = kotlinx.coroutines.Dispatchers.Default,
            transactionDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        repository = NovelHistoryRepositoryImpl(handler)

        database.novelsQueries.insert(
            source = 1L,
            url = "/novel",
            author = "Author",
            description = null,
            genre = null,
            title = "Novel",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
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
        novelId = database.novelsQueries.selectLastInsertedRowId().executeAsOne()

        database.novel_chaptersQueries.insert(
            novelId = novelId,
            url = "/chapter-1",
            name = "Chapter 1",
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0,
            chapterNumber = 1.0,
            sourceOrder = 0,
            dateFetch = 0,
            dateUpload = 10,
            version = 1,
        )
        chapterId = database.novel_chaptersQueries.selectLastInsertedRowId().executeAsOne()
    }

    @Test
    fun `upsert and get history`() = runTest {
        val readAt = Date(1000)
        repository.upsertNovelHistory(
            NovelHistoryUpdate(
                chapterId = chapterId,
                readAt = readAt,
                sessionReadDuration = 15,
            ),
        )

        val history = repository.getHistoryByNovelId(novelId)

        history.first().chapterId shouldBe chapterId
        history.first().readDuration shouldBe 15
    }

    @Test
    fun `history view maps relations`() = runTest {
        repository.upsertNovelHistory(
            NovelHistoryUpdate(
                chapterId = chapterId,
                readAt = Date(1000),
                sessionReadDuration = 5,
            ),
        )

        val history = repository.getNovelHistory("Novel").first()

        history.first().novelId shouldBe novelId
        history.first().title shouldBe "Novel"
    }
}
