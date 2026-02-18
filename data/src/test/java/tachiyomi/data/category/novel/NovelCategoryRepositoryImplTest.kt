package tachiyomi.data.category.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.entries.novel.NovelRepositoryImpl
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.novel.createTestNovelDatabase
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.entries.novel.model.Novel

class NovelCategoryRepositoryImplTest {

    @Test
    fun `insert and attach categories`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(database, driver)
        val eventBus: AchievementEventBus = mockk(relaxed = true)
        val novelRepository = NovelRepositoryImpl(handler, eventBus)
        val categoryRepository = NovelCategoryRepositoryImpl(handler)

        val novelId = novelRepository.insertNovel(
            Novel.create().copy(
                url = "/novel/1",
                title = "Test Novel",
                source = 2L,
            ),
        )!!

        val categoryId = categoryRepository.insertCategory(
            NovelCategory(
                id = 0,
                name = "Favorites",
                order = 0,
                flags = 0,
                hidden = false,
            ),
        )!!

        categoryRepository.setNovelCategories(novelId, listOf(categoryId))
        val categories = categoryRepository.getCategoriesByNovelId(novelId)
        categories.size shouldBe 1
        categories.first().name shouldBe "Favorites"
    }
}
