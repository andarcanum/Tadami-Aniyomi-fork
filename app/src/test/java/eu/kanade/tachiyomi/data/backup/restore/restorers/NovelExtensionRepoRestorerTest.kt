package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.novel.data.NovelDatabase

class NovelExtensionRepoRestorerTest {

    @Test
    fun `restore inserts novel repo when no conflicts`() {
        runTest {
            Class.forName("org.sqlite.JDBC")
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val database = createTestNovelDatabase(driver)
            val handler = FakeNovelDatabaseHandler(database)
            val getRepos = mockk<GetNovelExtensionRepo>()
            coEvery { getRepos.getAll() } returns emptyList()

            val restorer = NovelExtensionRepoRestorer(handler, getRepos)

            restorer(
                BackupExtensionRepos(
                    baseUrl = "https://example.org",
                    name = "Example",
                    shortName = null,
                    website = "https://example.org",
                    signingKeyFingerprint = "ABC",
                ),
            )

            database.novel_extension_reposQueries.findAll().executeAsList().size shouldBe 1
            driver.close()
        }
    }

    private fun createTestNovelDatabase(driver: JdbcSqliteDriver): NovelDatabase {
        NovelDatabase.Schema.create(driver)
        return NovelDatabase(
            driver = driver,
            novel_historyAdapter = Novel_history.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            novelsAdapter = Novels.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            ),
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
            block: suspend (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ): List<T> = error("unused")

        override suspend fun <T : Any> awaitOne(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ): T = error("unused")

        override suspend fun <T : Any> awaitOneExecutable(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.ExecutableQuery<T>,
        ): T = error("unused")

        override suspend fun <T : Any> awaitOneOrNull(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ): T? = error("unused")

        override suspend fun <T : Any> awaitOneOrNullExecutable(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.ExecutableQuery<T>,
        ): T? = error("unused")

        override fun <T : Any> subscribeToList(
            block: (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToOne(
            block: (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToOneOrNull(
            block: (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToPagingSource(
            countQuery: (NovelDatabase) -> app.cash.sqldelight.Query<Long>,
            queryProvider: (NovelDatabase, Long, Long) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")
    }
}
