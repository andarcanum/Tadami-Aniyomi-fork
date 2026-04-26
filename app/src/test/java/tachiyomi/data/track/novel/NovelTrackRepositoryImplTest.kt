package tachiyomi.data.track.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.domain.track.novel.model.NovelTrack
import tachiyomi.novel.data.NovelDatabase

class NovelTrackRepositoryImplTest {

    private lateinit var database: NovelDatabase
    private lateinit var repository: NovelTrackRepositoryImpl
    private var novelId: Long = -1L

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
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )
        repository = NovelTrackRepositoryImpl(handler)

        database.novelsQueries.insert(
            source = 1L,
            url = "/novel",
            author = "Author",
            description = "Desc",
            genre = listOf("Action"),
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
    }

    @Test
    fun `insertNovel persists rows in novel sync table`() = runTest {
        repository.insertNovel(
            NovelTrack(
                id = 55L,
                novelId = novelId,
                trackerId = 5L,
                remoteId = 88L,
                libraryId = 99L,
                title = "Novel",
                lastChapterRead = 12.0,
                totalChapters = 100L,
                status = 1L,
                score = 8.5,
                remoteUrl = "https://example.org",
                startDate = 10L,
                finishDate = 20L,
                private = true,
            ),
        )

        val tracks = repository.getTracksByNovelId(novelId)

        tracks shouldHaveSize 1
        tracks.first().novelId shouldBe novelId
        tracks.first().trackerId shouldBe 5L
        tracks.first().remoteId shouldBe 88L
        tracks.first().title shouldBe "Novel"
    }

    @Test
    fun `getTrackByNovelId returns inserted track`() = runTest {
        repository.insertNovel(
            NovelTrack(
                id = 0L,
                novelId = novelId,
                trackerId = 7L,
                remoteId = 99L,
                libraryId = null,
                title = "Novel Two",
                lastChapterRead = 1.0,
                totalChapters = 10L,
                status = 2L,
                score = 0.0,
                remoteUrl = "https://example.org/novel-two",
                startDate = 0L,
                finishDate = 0L,
                private = false,
            ),
        )

        val track = repository.getTrackByNovelId(novelId)

        track?.novelId shouldBe novelId
        track?.trackerId shouldBe 7L
        track?.title shouldBe "Novel Two"
    }
}
