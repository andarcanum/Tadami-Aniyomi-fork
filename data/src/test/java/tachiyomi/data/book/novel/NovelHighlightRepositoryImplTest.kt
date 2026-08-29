package tachiyomi.data.book.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.novel.data.NovelDatabase

class NovelHighlightRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: NovelDatabase
    private lateinit var handler: AndroidNovelDatabaseHandler
    private lateinit var repository: NovelHighlightRepositoryImpl
    private var novelId: Long = -1
    private var secondNovelId: Long = -1
    private var chapterId: Long = -1

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NovelDatabase.Schema.create(driver)
        database = NovelDatabase(
            driver = driver,
            novel_chaptersAdapter = datanovel.Novel_chapters.Adapter(memoAdapter = MemoColumnAdapter),
            novel_historyAdapter = Novel_history.Adapter(
                last_readAdapter = tachiyomi.data.DateColumnAdapter,
            ),
            novelsAdapter = Novels.Adapter(
                memoAdapter = MemoColumnAdapter,
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
        repository = NovelHighlightRepositoryImpl(handler)

        novelId = insertNovel("/novel")
        secondNovelId = insertNovel("/novel-2")
        chapterId = insertChapter(novelId, "/chapter-1")
    }

    private fun insertNovel(url: String): Long {
        database.novelsQueries.insert(
            source = 1L,
            url = url,
            author = null,
            description = null,
            notes = "",
            genre = null,
            title = "Novel $url",
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
        return database.novelsQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun insertChapter(novelId: Long, url: String): Long {
        database.novel_chaptersQueries.insert(
            novelId = novelId,
            url = url,
            name = "Chapter",
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0,
            chapterNumber = 1.0,
            sourceOrder = 0,
            dateFetch = 0,
            dateUpload = 10,
            dateUploadRaw = null,
            version = 1,
        )
        return database.novel_chaptersQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun highlight(
        novelId: Long = this.novelId,
        chapterId: Long = this.chapterId,
        blockIndex: Int = 3,
        charStart: Int = 10,
        charEndExclusive: Int = 20,
        normalizedText: String = "test snippet",
        colorArgb: Long = 4294688813L,
        note: String = "",
        pageIndex: Int = 0,
        pageCount: Int = 0,
    ) = NovelHighlight(
        id = 0L,
        novelId = novelId,
        chapterId = chapterId,
        blockIndex = blockIndex,
        charStart = charStart,
        charEndExclusive = charEndExclusive,
        normalizedText = normalizedText,
        colorArgb = colorArgb,
        note = note,
        createdAt = 100L,
        updatedAt = 100L,
        pageIndex = pageIndex,
        pageCount = pageCount,
    )

    @Test
    fun `insert then getForChapter returns highlight with mapped fields`() = runTest {
        val id = repository.add(highlight(pageIndex = 2, pageCount = 24))

        val stored = repository.subscribeForChapter(chapterId).first().single()
        stored.id shouldBe id
        stored.novelId shouldBe novelId
        stored.chapterId shouldBe chapterId
        stored.blockIndex shouldBe 3
        stored.charStart shouldBe 10
        stored.charEndExclusive shouldBe 20
        stored.normalizedText shouldBe "test snippet"
        stored.colorArgb shouldBe 4294688813L
        stored.note shouldBe ""
        stored.pageIndex shouldBe 2
        stored.pageCount shouldBe 24
    }

    @Test
    fun `pages default to zero for legacy-style rows`() = runTest {
        val id = repository.add(highlight())

        val stored = repository.getForNovel(novelId).single()
        stored.id shouldBe id
        stored.pageIndex shouldBe 0
        stored.pageCount shouldBe 0
    }

    @Test
    fun `getForNovel filters by novel and orders by chapter block char`() = runTest {
        val secondChapterId = insertChapter(secondNovelId, "/other-chapter")
        repository.add(highlight(blockIndex = 5))
        repository.add(highlight(blockIndex = 3))
        repository.add(highlight(novelId = secondNovelId, chapterId = secondChapterId))

        val stored = repository.getForNovel(novelId)

        stored.map { it.blockIndex } shouldBe listOf(3, 5)
        stored.all { it.novelId == novelId } shouldBe true
    }

    @Test
    fun `updateNoteAndColor persists changes`() = runTest {
        val id = repository.add(highlight())

        repository.updateNoteAndColor(id, note = "my note", colorArgb = 0xFF90CAF9, updatedAt = 200L)

        val stored = repository.getForNovel(novelId).single()
        stored.note shouldBe "my note"
        stored.colorArgb shouldBe 0xFF90CAF9
        stored.updatedAt shouldBe 200L
    }

    @Test
    fun `reanchor moves coordinates`() = runTest {
        val id = repository.add(highlight())

        repository.reanchor(id, blockIndex = 9, charStart = 1, charEndExclusive = 7, updatedAt = 300L)

        val stored = repository.getForNovel(novelId).single()
        stored.blockIndex shouldBe 9
        stored.charStart shouldBe 1
        stored.charEndExclusive shouldBe 7
    }

    @Test
    fun `delete removes row and subscription emits updates`() = runTest {
        val id = repository.add(highlight())
        repository.subscribeForChapter(chapterId).first().map { it.id } shouldBe listOf(id)

        repository.delete(id)

        repository.subscribeForChapter(chapterId).first() shouldBe emptyList()
    }

    @Test
    fun `countAll counts inserted highlights`() = runTest {
        repository.countAll() shouldBe 0

        repository.add(highlight())
        repository.add(highlight(novelId = secondNovelId))

        repository.countAll() shouldBe 2
    }
}
