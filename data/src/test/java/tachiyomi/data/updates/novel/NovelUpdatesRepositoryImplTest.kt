package tachiyomi.data.updates.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.novel.data.NovelDatabase

class NovelUpdatesRepositoryImplTest {

    private lateinit var database: NovelDatabase
    private lateinit var repository: NovelUpdatesRepositoryImpl
    private var novelId: Long = -1
    private var chapterId: Long = -1

    @BeforeEach
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
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
        val handler = AndroidNovelDatabaseHandler(
            db = database,
            driver = driver,
            queryDispatcher = kotlinx.coroutines.Dispatchers.Default,
            transactionDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        repository = NovelUpdatesRepositoryImpl(handler)

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
            pinned = false,
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
            dateFetch = 1_000,
            dateUpload = 0,
            dateUploadRaw = null,
            version = 1,
        )
        chapterId = database.novel_chaptersQueries.selectLastInsertedRowId().executeAsOne()
    }

    @Test
    fun `recent updates include chapters fetched during sync even without upload date`() = runTest {
        val updates = repository.subscribeAllNovelUpdates(after = 0L, limit = 50L).first()

        updates.map { it.chapterId } shouldContainExactly listOf(chapterId)
        updates.first().novelId shouldBe novelId
        updates.first().chapterName shouldBe "Chapter 1"
    }
}
