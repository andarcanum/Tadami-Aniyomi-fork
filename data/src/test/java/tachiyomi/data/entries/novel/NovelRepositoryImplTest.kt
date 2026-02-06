package tachiyomi.data.entries.novel

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
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.novel.data.NovelDatabase

class NovelRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: NovelDatabase
    private lateinit var handler: AndroidNovelDatabaseHandler
    private lateinit var repository: NovelRepositoryImpl

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
        repository = NovelRepositoryImpl(handler)
    }

    @Test
    fun `insert and get novel`() = runTest {
        val novelId = repository.insertNovel(
            Novel.create().copy(
                source = 1L,
                url = "/novel",
                title = "Novel",
                author = "Author",
                status = 1L,
                updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            ),
        )

        val novel = repository.getNovelById(checkNotNull(novelId))

        novel.title shouldBe "Novel"
        novel.author shouldBe "Author"
    }

    @Test
    fun `update novel fields`() = runTest {
        val novelId = repository.insertNovel(
            Novel.create().copy(
                source = 2L,
                url = "/novel-2",
                title = "Old",
                status = 1L,
                updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            ),
        )

        val updated = repository.updateNovel(
            NovelUpdate(
                id = checkNotNull(novelId),
                title = "New",
            ),
        )

        updated shouldBe true
        repository.getNovelById(checkNotNull(novelId)).title shouldBe "New"
    }

    @Test
    fun `get library novel returns favorites`() = runTest {
        repository.insertNovel(
            Novel.create().copy(
                source = 3L,
                url = "/novel-3",
                title = "Library",
                favorite = true,
                status = 1L,
                updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            ),
        )

        val library = repository.getLibraryNovelAsFlow().first()

        library.size shouldBe 1
        library.first().novel.title shouldBe "Library"
    }

    @Test
    fun `reset viewer flags clears values`() = runTest {
        val novelId = repository.insertNovel(
            Novel.create().copy(
                source = 4L,
                url = "/novel-4",
                title = "Viewer",
                viewerFlags = 123L,
                status = 1L,
                updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            ),
        )

        repository.resetNovelViewerFlags()

        repository.getNovelById(checkNotNull(novelId)).viewerFlags shouldBe 0L
    }
}
