package eu.kanade.tachiyomi.data.backup.create.creators

import android.app.Application
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_chapters
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.book.novel.NovelBookStateRepositoryImpl
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.book.novel.interactor.GetNovelBookState
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.novel.data.NovelDatabase
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelBackupCreatorTest {

    private lateinit var database: NovelDatabase
    private lateinit var creator: NovelBackupCreator

    @Before
    fun setup() {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NovelDatabase.Schema.create(driver)
        database = NovelDatabase(
            driver = driver,
            novel_historyAdapter = Novel_history.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            novel_chaptersAdapter = Novel_chapters.Adapter(memoAdapter = MemoColumnAdapter),
            novelsAdapter = Novels.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
        )
        val handler = FakeNovelDatabaseHandler(database)
        creator = NovelBackupCreator(handler, GetNovelBookState(NovelBookStateRepositoryImpl(handler)))
    }

    @Test
    fun `collects chapters, highlights, categories, history, scanlators and book state`() = runTest {
        val novelId1 = insertNovel(url = "https://x/1", favorite = true)
        val novelId2 = insertNovel(url = "https://x/2", favorite = true)
        val novelId3 = insertNovel(url = "https://x/3", favorite = false)
        val chapterId1 = insertChapter(novelId = novelId1, url = "/c1", sourceOrder = 0)
        val chapterId2 = insertChapter(novelId = novelId1, url = "/c2", sourceOrder = 1)
        insertChapter(novelId = novelId3, url = "/c3", sourceOrder = 0)
        database.novel_historyQueries.upsert(chapterId = chapterId1, readAt = Date(1700000000000), time_read = 45)
        database.novel_categoriesQueries.insert(
            name = "Reading",
            order = 0,
            flags = 0,
            hidden = 0,
            hidden_from_home_hub = 0,
        )
        database.novels_categoriesQueries.insert(novelId = novelId1, categoryId = 1)
        database.novel_highlightsQueries.insert(
            novelId = novelId1,
            chapterId = chapterId1,
            blockIndex = 3,
            charStart = 10,
            charEndExclusive = 25,
            normalizedText = "highlight text",
            colorArgb = 4294901760,
            note = "note",
            createdAt = 0,
            updatedAt = 0,
            pageIndex = 1,
            pageCount = 2,
        )
        database.novel_excluded_scanlatorsQueries.insert(novelId = novelId1, scanlator = "BadGroup")
        database.novel_booksQueries.upsert(
            novelId = novelId1,
            enabled = true,
            bookVersion = 2,
            sourceId = 1L,
            chapterSetHash = "hash",
            totalChars = 1000L,
            chapterCount = 2,
            charOffset = 100L,
            lastChapterId = chapterId2,
            blockIndex = 1,
            chapterCharOffset = 5,
            progressMigrated = true,
            complete = false,
            builtAt = 0,
            updatedAt = 0,
        )

        val backup = creator(
            listOf(novelDomain(novelId1, "https://x/1"), novelDomain(novelId2, "https://x/2")),
            BackupOptions(),
        )

        backup.size shouldBe 2
        val n1 = backup.first { it.url == "https://x/1" }
        n1.chapters.map { it.url } shouldBe listOf("/c1", "/c2")
        n1.categories shouldBe listOf(0L)
        n1.history.first().url shouldBe "/c1"
        n1.history.first().lastRead shouldBe 1700000000000
        n1.history.first().readDuration shouldBe 45
        n1.excludedScanlators shouldBe listOf("BadGroup")
        n1.highlights.size shouldBe 1
        n1.highlights.first().chapterUrl shouldBe "/c1"
        n1.highlights.first().blockIndex shouldBe 3
        n1.bookState!!.enabled shouldBe true
        n1.bookState!!.lastChapterUrl shouldBe "/c2"
        val n2 = backup.first { it.url == "https://x/2" }
        n2.chapters shouldBe emptyList()
        n2.categories shouldBe emptyList()
        n2.history shouldBe emptyList()
        n2.highlights shouldBe emptyList()
        n2.bookState shouldBe null
        backup.none { it.url == "https://x/3" } shouldBe true
    }

    @Test
    fun `book state survives with chapters option disabled`() = runTest {
        val novelId1 = insertNovel(url = "https://x/1", favorite = true)
        val chapterId1 = insertChapter(novelId = novelId1, url = "/c1", sourceOrder = 0)
        database.novel_booksQueries.upsert(
            novelId = novelId1,
            enabled = true,
            bookVersion = 2,
            sourceId = 1L,
            chapterSetHash = "hash",
            totalChars = 1000L,
            chapterCount = 1,
            charOffset = 100L,
            lastChapterId = chapterId1,
            blockIndex = 1,
            chapterCharOffset = 5,
            progressMigrated = true,
            complete = false,
            builtAt = 0,
            updatedAt = 0,
        )

        val backup = creator(
            listOf(novelDomain(novelId1, "https://x/1")),
            BackupOptions(chapters = false, categories = false, tracking = false, history = false),
        )

        val n1 = backup.single()
        n1.chapters shouldBe emptyList()
        n1.highlights shouldBe emptyList()
        n1.history shouldBe emptyList()
        // Book state is not gated by the chapters option (unchanged production behavior).
        n1.bookState!!.enabled shouldBe true
        n1.bookState!!.lastChapterUrl shouldBe "/c1"
    }

    private fun insertNovel(url: String, favorite: Boolean): Long {
        database.novelsQueries.insert(
            source = 1L,
            url = url,
            author = null,
            description = null,
            notes = "",
            genre = null,
            title = "Novel $url",
            status = 0L,
            thumbnailUrl = null,
            favorite = favorite,
            pinned = false,
            lastUpdate = null,
            nextUpdate = null,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 0L,
            completedAt = null,
        )
        return database.novelsQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun insertChapter(novelId: Long, url: String, sourceOrder: Long): Long {
        database.novel_chaptersQueries.insert(
            novelId = novelId,
            url = url,
            name = "Ch $sourceOrder",
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            chapterNumber = sourceOrder.toDouble(),
            sourceOrder = sourceOrder,
            dateFetch = 0L,
            dateUpload = 0L,
            dateUploadRaw = null,
            version = 0L,
        )
        return database.novel_chaptersQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun novelDomain(id: Long, url: String) = Novel(
        id = id,
        source = 1L,
        favorite = true,
        pinned = false,
        lastUpdate = 0L,
        nextUpdate = 0L,
        fetchInterval = 0,
        dateAdded = 0L,
        viewerFlags = 0L,
        chapterFlags = 0L,
        coverLastModified = 0L,
        url = url,
        title = "Novel $url",
        author = null,
        description = null,
        genre = null,
        status = 0L,
        thumbnailUrl = null,
        updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        initialized = true,
        lastModifiedAt = 0L,
        favoriteModifiedAt = null,
        version = 0L,
    )
}

private class FakeNovelDatabaseHandler(
    private val database: NovelDatabase,
) : NovelDatabaseHandler {
    override suspend fun <T> await(
        inTransaction: Boolean,
        block: suspend (NovelDatabase) -> T,
    ): T = block(database)

    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend (NovelDatabase) -> Query<T>,
    ): List<T> = block(database).executeAsList()

    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend (NovelDatabase) -> Query<T>,
    ): T = block(database).executeAsOne()

    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend (NovelDatabase) -> ExecutableQuery<T>,
    ): T = block(database).executeAsOne()

    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend (NovelDatabase) -> Query<T>,
    ): T? = block(database).executeAsOneOrNull()

    override suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend (NovelDatabase) -> ExecutableQuery<T>,
    ): T? = error("unused")

    override fun <T : Any> subscribeToList(
        block: (NovelDatabase) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToOne(
        block: (NovelDatabase) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToOneOrNull(
        block: (NovelDatabase) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToPagingSource(
        countQuery: (NovelDatabase) -> Query<Long>,
        queryProvider: (NovelDatabase, Long, Long) -> Query<T>,
    ) = error("unused")
}
