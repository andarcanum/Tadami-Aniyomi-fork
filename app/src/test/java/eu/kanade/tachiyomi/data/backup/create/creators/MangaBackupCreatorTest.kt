package eu.kanade.tachiyomi.data.backup.create.creators

import android.app.Application
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import `data`.Chapters
import `data`.History
import `data`.Mangas
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.entries.manga.model.Manga
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class MangaBackupCreatorTest {

    private lateinit var database: Database
    private lateinit var creator: MangaBackupCreator

    @Before
    fun setup() {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        database = Database(
            driver = driver,
            historyAdapter = History.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
        )
        creator = MangaBackupCreator(FakeMangaDatabaseHandler(database))
    }

    @Test
    fun `collects chapters, categories, tracks, history and scanlators for library`() = runTest {
        val mangaId1 = insertManga(url = "https://x/1", favorite = true)
        val mangaId2 = insertManga(url = "https://x/2", favorite = true)
        val mangaId3 = insertManga(url = "https://x/3", favorite = false)
        val chapterId1 = insertChapter(mangaId = mangaId1, url = "/c1", sourceOrder = 0)
        insertChapter(mangaId = mangaId1, url = "/c2", sourceOrder = 1)
        insertChapter(mangaId = mangaId3, url = "/c3", sourceOrder = 0)
        database.historyQueries.upsert(chapterId = chapterId1, readAt = Date(1700000000000), time_read = 60)
        database.categoriesQueries.insert(name = "Reading", order = 0, flags = 0)
        database.mangas_categoriesQueries.insert(mangaId = mangaId1, categoryId = 1)
        database.manga_syncQueries.insert(
            mangaId = mangaId1,
            syncId = 1L,
            remoteId = 42L,
            libraryId = null,
            title = "MAL",
            lastChapterRead = 0.0,
            totalChapters = 0,
            status = 1,
            score = 0.0,
            remoteUrl = "",
            startDate = 0,
            finishDate = 0,
            private = false,
        )
        database.excluded_scanlatorsQueries.insert(mangaId = mangaId1, scanlator = "BadGroup")

        val backup = creator(
            listOf(mangaDomain(mangaId1, "https://x/1"), mangaDomain(mangaId2, "https://x/2")),
            BackupOptions(),
        )

        backup.size shouldBe 2
        val m1 = backup.first { it.url == "https://x/1" }
        m1.chapters.map { it.url } shouldBe listOf("/c1", "/c2")
        m1.categories shouldBe listOf(0L)
        m1.tracking.size shouldBe 1
        m1.history.first().url shouldBe "/c1"
        m1.history.first().lastRead shouldBe 1700000000000
        m1.history.first().readDuration shouldBe 60
        m1.excludedScanlators shouldBe listOf("BadGroup")
        val m2 = backup.first { it.url == "https://x/2" }
        m2.chapters shouldBe emptyList()
        m2.categories shouldBe emptyList()
        m2.tracking shouldBe emptyList()
        m2.history shouldBe emptyList()
        backup.none { it.url == "https://x/3" } shouldBe true
    }

    @Test
    fun `option flags gate sections`() = runTest {
        val mangaId1 = insertManga(url = "https://x/1", favorite = true)
        insertChapter(mangaId = mangaId1, url = "/c1", sourceOrder = 0)

        val backup = creator(
            listOf(mangaDomain(mangaId1, "https://x/1")),
            BackupOptions(chapters = false, categories = false, tracking = false, history = false),
        )

        val m1 = backup.single()
        m1.chapters shouldBe emptyList()
        m1.categories shouldBe emptyList()
        m1.tracking shouldBe emptyList()
        m1.history shouldBe emptyList()
    }

    private fun insertManga(url: String, favorite: Boolean): Long {
        database.mangasQueries.insert(
            source = 1L,
            url = url,
            artist = null,
            author = null,
            description = null,
            notes = "",
            genre = null,
            title = "Manga $url",
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
            rating = 0.0,
            memo = kotlinx.serialization.json.JsonObject(emptyMap()),
            completedAt = null,
        )
        return database.mangasQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun insertChapter(mangaId: Long, url: String, sourceOrder: Long): Long {
        database.chaptersQueries.insert(
            mangaId = mangaId,
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
            version = 0L,
            memo = kotlinx.serialization.json.JsonObject(emptyMap()),
        )
        return database.chaptersQueries.selectLastInsertedRowId().executeAsOne()
    }

    private fun mangaDomain(id: Long, url: String) = Manga(
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
        title = "Manga $url",
        artist = null,
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

private class FakeMangaDatabaseHandler(
    private val database: Database,
) : MangaDatabaseHandler {
    override suspend fun <T> await(
        inTransaction: Boolean,
        block: suspend (Database) -> T,
    ): T = block(database)

    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend (Database) -> Query<T>,
    ): List<T> = block(database).executeAsList()

    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend (Database) -> Query<T>,
    ): T = error("unused")

    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend (Database) -> ExecutableQuery<T>,
    ): T = error("unused")

    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend (Database) -> Query<T>,
    ): T? = error("unused")

    override suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend (Database) -> ExecutableQuery<T>,
    ): T? = error("unused")

    override fun <T : Any> subscribeToList(
        block: (Database) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToOne(
        block: (Database) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToOneOrNull(
        block: (Database) -> Query<T>,
    ) = error("unused")

    override fun <T : Any> subscribeToPagingSource(
        countQuery: (Database) -> Query<Long>,
        queryProvider: (Database, Long, Long) -> Query<T>,
    ) = error("unused")
}
