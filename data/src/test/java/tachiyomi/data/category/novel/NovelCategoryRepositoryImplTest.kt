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
import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
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
                hiddenFromHomeHub = false,
            ),
        )!!

        database.novel_categoriesQueries.getCategories().executeAsList().size shouldBe 2

        categoryRepository.setNovelCategories(novelId, listOf(categoryId))
        val categories = categoryRepository.getCategoriesByNovelId(novelId)
        categories.size shouldBe 1
        categories.first().name shouldBe "Favorites"
    }

    @Test
    fun `migrates legacy categories table into novel categories table`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(database, driver)
        val categoryRepository = NovelCategoryRepositoryImpl(handler)

        database.categoriesQueries.insert(
            name = "Legacy",
            order = 5,
            flags = 11,
        )
        val legacyCategoryId = database.categoriesQueries.selectLastInsertedRowId()
            .executeAsOne()

        database.novel_categoriesQueries.getCategory(legacyCategoryId).executeAsOneOrNull() shouldBe null

        val categories = categoryRepository.getCategories()

        categories.any { it.id == legacyCategoryId && it.name == "Legacy" } shouldBe true
        database.novel_categoriesQueries.getCategory(legacyCategoryId).executeAsOneOrNull()?.name shouldBe "Legacy"
    }

    @Test
    fun `new category has hiddenFromHomeHub defaulting to false`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(database, driver)
        val categoryRepository = NovelCategoryRepositoryImpl(handler)

        val categoryId = categoryRepository.insertCategory(
            NovelCategory(
                id = 0,
                name = "Test Category",
                order = 0,
                flags = 0,
                hidden = false,
                hiddenFromHomeHub = false,
            ),
        )!!

        val category = categoryRepository.getCategory(categoryId)
        category?.hiddenFromHomeHub shouldBe false
    }

    @Test
    fun `toggle hiddenFromHomeHub via partial update`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(database, driver)
        val categoryRepository = NovelCategoryRepositoryImpl(handler)

        val categoryId = categoryRepository.insertCategory(
            NovelCategory(
                id = 0,
                name = "NSFW",
                order = 0,
                flags = 0,
                hidden = false,
                hiddenFromHomeHub = false,
            ),
        )!!

        // Initially false
        var category = categoryRepository.getCategory(categoryId)
        category?.hiddenFromHomeHub shouldBe false

        // Toggle to true
        categoryRepository.updatePartialCategory(
            NovelCategoryUpdate(id = categoryId, hiddenFromHomeHub = true),
        )
        category = categoryRepository.getCategory(categoryId)
        category?.hiddenFromHomeHub shouldBe true

        // Toggle back to false
        categoryRepository.updatePartialCategory(
            NovelCategoryUpdate(id = categoryId, hiddenFromHomeHub = false),
        )
        category = categoryRepository.getCategory(categoryId)
        category?.hiddenFromHomeHub shouldBe false
    }

    @Test
    fun `getCategories returns hiddenFromHomeHub correctly`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(database, driver)
        val categoryRepository = NovelCategoryRepositoryImpl(handler)

        categoryRepository.insertCategory(
            NovelCategory(id = 0, name = "Visible", order = 0, flags = 0, hidden = false, hiddenFromHomeHub = false),
        )
        categoryRepository.insertCategory(
            NovelCategory(id = 0, name = "HiddenFromHub", order = 1, flags = 0, hidden = false, hiddenFromHomeHub = true),
        )

        val categories = categoryRepository.getCategories()
        val visibleCategory = categories.find { it.name == "Visible" }
        val hiddenFromHubCategory = categories.find { it.name == "HiddenFromHub" }

        visibleCategory?.hiddenFromHomeHub shouldBe false
        hiddenFromHubCategory?.hiddenFromHomeHub shouldBe true
    }

    @Test
    fun `legacy migration copies hiddenFromHomeHub as false`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(database, driver)
        val categoryRepository = NovelCategoryRepositoryImpl(handler)

        // Insert a legacy category
        database.categoriesQueries.insert(
            name = "Legacy NSFW",
            order = 5,
            flags = 11,
        )
        val legacyCategoryId = database.categoriesQueries.selectLastInsertedRowId().executeAsOne()

        // Trigger migration
        val categories = categoryRepository.getCategories()
        val migrated = categories.find { it.id == legacyCategoryId }

        migrated?.hiddenFromHomeHub shouldBe false
        migrated?.hidden shouldBe false
    }
}
