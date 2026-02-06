package tachiyomi.data.extension.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.novel.createTestNovelDatabase
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelPluginRepositoryImplTest {

    @Test
    fun `upsert and read installed novel plugin`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(database, driver)
        val repository = NovelPluginRepositoryImpl(handler)

        val plugin = NovelPlugin.Installed(
            id = "example.plugin",
            name = "Example",
            site = "https://example.com",
            lang = "en",
            version = 1,
            url = "https://example.com/plugin.js",
            iconUrl = "https://example.com/icon.png",
            customJs = "https://example.com/custom.js",
            customCss = null,
            hasSettings = true,
            sha256 = "deadbeef",
            repoUrl = "https://repo.example",
        )

        repository.upsert(plugin)

        val stored = repository.getById("example.plugin")!!
        stored shouldBe plugin
    }
}
