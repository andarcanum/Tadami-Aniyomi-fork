package tachiyomi.data.items.novelchapter

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.novel.data.NovelDatabase

class NovelChapterRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: NovelDatabase
    private lateinit var handler: AndroidNovelDatabaseHandler
    private lateinit var repository: NovelChapterRepositoryImpl
    private var novelId: Long = -1

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
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        handler = AndroidNovelDatabaseHandler(
            db = database,
            driver = driver,
            queryDispatcher = kotlinx.coroutines.Dispatchers.Default,
            transactionDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        repository = NovelChapterRepositoryImpl(handler)

        database.novelsQueries.insert(
            source = 1L,
            url = "/novel",
            author = "Author",
            description = null,
            notes = "",
            genre = null,
            title = "Novel",
            status = 1L,
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
            pinned = false,
            version = 0,
        )
        novelId = database.novelsQueries.selectLastInsertedRowId().executeAsOne()
    }

    @Test
    fun `add and get chapter`() = runTest {
        val chapters = repository.addAllChapters(
            listOf(
                NovelChapter.create().copy(
                    novelId = novelId,
                    url = "/chapter-1",
                    name = "Chapter 1",
                    chapterNumber = 1.0,
                    dateUpload = 10,
                ),
            ),
        )

        val stored = repository.getChapterById(chapters.first().id)

        checkNotNull(stored).name shouldBe "Chapter 1"
    }

    @Test
    fun `update chapter fields`() = runTest {
        val chapterId = repository.addAllChapters(
            listOf(
                NovelChapter.create().copy(
                    novelId = novelId,
                    url = "/chapter-2",
                    name = "Chapter 2",
                    chapterNumber = 2.0,
                    dateUpload = 20,
                ),
            ),
        ).first().id

        repository.updateChapter(
            NovelChapterUpdate(
                id = chapterId,
                read = true,
            ),
        )

        repository.getChapterById(chapterId)?.read shouldBe true
    }

    @Test
    fun `add and get chapter preserves raw upload date`() = runTest {
        val chapters = repository.addAllChapters(
            listOf(
                NovelChapter.create().copy(
                    novelId = novelId,
                    url = "/chapter-raw",
                    name = "Chapter raw",
                    chapterNumber = 3.0,
                    dateUpload = 0,
                    dateUploadRaw = "4 hours ago",
                ),
            ),
        )

        val stored = repository.getChapterById(chapters.first().id)

        checkNotNull(stored).dateUploadRaw shouldBe "4 hours ago"
    }

    @Test
    fun `sync chapters in a single transaction`() = runTest {
        val chapter1 = NovelChapter.create().copy(
            novelId = novelId,
            url = "/chapter-sync-1",
            name = "Sync 1",
            chapterNumber = 1.0,
            dateUpload = 10,
        )
        val added = repository.addAllChapters(listOf(chapter1)).first()

        val updatedChapter = NovelChapterUpdate(
            id = added.id,
            read = true,
        )
        val toAddChapter = NovelChapter.create().copy(
            novelId = novelId,
            url = "/chapter-sync-2",
            name = "Sync 2",
            chapterNumber = 2.0,
            dateUpload = 20,
        )

        // Perform the sync
        val syncAdded = repository.syncChapters(
            toAdd = listOf(toAddChapter),
            toUpdate = listOf(updatedChapter),
            toDelete = emptyList(),
        )

        // Verifications
        repository.getChapterById(added.id)?.read shouldBe true
        syncAdded.size shouldBe 1
        syncAdded.first().name shouldBe "Sync 2"
        repository.getChapterById(syncAdded.first().id) shouldBe syncAdded.first()
    }
}
