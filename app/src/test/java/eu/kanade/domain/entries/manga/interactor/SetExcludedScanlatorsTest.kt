package eu.kanade.domain.entries.manga.interactor

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import `data`.History
import `data`.Mangas
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class SetExcludedScanlatorsTest {

    @Test
    fun `await stores excluded scanlators`() = runTest {
        val environment = createEnvironment()

        environment.interactor.await(environment.mangaId, setOf("Team A", "Team B"))

        environment.database.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(environment.mangaId)
            .executeAsList()
            .toSet() shouldBe setOf("Team A", "Team B")
    }

    @Test
    fun `await replaces excluded scanlators`() = runTest {
        val environment = createEnvironment()

        environment.interactor.await(environment.mangaId, setOf("Team A", "Team B"))
        environment.interactor.await(environment.mangaId, setOf("Team B"))

        environment.database.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(environment.mangaId)
            .executeAsList()
            .toSet() shouldBe setOf("Team B")
    }

    private fun createEnvironment(): TestEnvironment {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            historyAdapter = History.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
        )
        val mangaId = insertTestManga(database)
        return TestEnvironment(
            database = database,
            mangaId = mangaId,
            interactor = SetExcludedScanlators(FakeMangaDatabaseHandler(database)),
        )
    }

    private fun insertTestManga(database: Database): Long {
        database.mangasQueries.insert(
            source = 1L,
            url = "https://example.org/manga/1",
            artist = null,
            author = null,
            description = null,
            genre = null,
            title = "Test manga",
            status = 0L,
            thumbnailUrl = null,
            favorite = false,
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
        )
        return database.mangasQueries.selectLastInsertedRowId().executeAsOne()
    }

    private data class TestEnvironment(
        val database: Database,
        val mangaId: Long,
        val interactor: SetExcludedScanlators,
    )

    private class FakeMangaDatabaseHandler(
        private val database: Database,
    ) : MangaDatabaseHandler {
        override suspend fun <T> await(
            inTransaction: Boolean,
            block: suspend (Database) -> T,
        ): T = block(database)

        override suspend fun <T : Any> awaitList(
            inTransaction: Boolean,
            block: suspend (Database) -> app.cash.sqldelight.Query<T>,
        ): List<T> = block(database).executeAsList()

        override suspend fun <T : Any> awaitOne(
            inTransaction: Boolean,
            block: suspend (Database) -> app.cash.sqldelight.Query<T>,
        ): T = error("unused")

        override suspend fun <T : Any> awaitOneExecutable(
            inTransaction: Boolean,
            block: suspend (Database) -> app.cash.sqldelight.ExecutableQuery<T>,
        ): T = error("unused")

        override suspend fun <T : Any> awaitOneOrNull(
            inTransaction: Boolean,
            block: suspend (Database) -> app.cash.sqldelight.Query<T>,
        ): T? = error("unused")

        override suspend fun <T : Any> awaitOneOrNullExecutable(
            inTransaction: Boolean,
            block: suspend (Database) -> app.cash.sqldelight.ExecutableQuery<T>,
        ): T? = error("unused")

        override fun <T : Any> subscribeToList(
            block: (Database) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToOne(
            block: (Database) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToOneOrNull(
            block: (Database) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToPagingSource(
            countQuery: (Database) -> app.cash.sqldelight.Query<Long>,
            queryProvider: (Database, Long, Long) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")
    }
}
