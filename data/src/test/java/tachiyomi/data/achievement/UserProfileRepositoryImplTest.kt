package tachiyomi.data.achievement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.UserProfile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProfileRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AchievementsDatabase
    private lateinit var repository: UserProfileRepositoryImpl

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        tachiyomi.data.achievement.AchievementsDatabase.Schema.create(driver)
        database = AchievementsDatabase(driver)
        repository = UserProfileRepositoryImpl(database)
    }

    @AfterEach
    fun teardown() {
        driver.close()
    }

    @Test
    fun `saveProfile inserts new profile`() = runTest {
        val profile = UserProfile(
            userId = "test_user",
            username = "TestUser",
            level = 5,
            currentXP = 230,
            xpToNextLevel = 500,
            totalXP = 1230,
            titles = listOf("Bookworm", "Anime Fan"),
            badges = listOf("🏅", "📚"),
            unlockedThemes = listOf("aurora", "dark"),
            achievementsUnlocked = 10,
            totalAchievements = 50,
            joinDate = System.currentTimeMillis(),
        )

        repository.saveProfile(profile)

        val retrieved = repository.getProfileSync("test_user")
        assertNotNull(retrieved)
        assertEquals("TestUser", retrieved.username)
        assertEquals(5, retrieved.level)
        assertEquals(1230, retrieved.totalXP)
        assertEquals(2, retrieved.titles.size)
        assertEquals(2, retrieved.badges.size)
        assertEquals(2, retrieved.unlockedThemes.size)
    }

    @Test
    fun `saveProfile updates existing profile`() = runTest {
        val initialProfile = UserProfile(
            userId = "default",
            username = "User1",
            level = 1,
            currentXP = 0,
            xpToNextLevel = 100,
            totalXP = 0,
            titles = emptyList(),
            badges = emptyList(),
            unlockedThemes = emptyList(),
            achievementsUnlocked = 0,
            totalAchievements = 50,
            joinDate = System.currentTimeMillis(),
        )

        repository.saveProfile(initialProfile)

        // Update profile
        val updatedProfile = initialProfile.copy(
            username = "User2",
            level = 5,
            totalXP = 1500,
            titles = listOf("Pro"),
        )

        repository.saveProfile(updatedProfile)

        val retrieved = repository.getProfileSync("default")
        assertNotNull(retrieved)
        assertEquals("User2", retrieved.username)
        assertEquals(5, retrieved.level)
        assertEquals(1500, retrieved.totalXP)
        assertEquals(1, retrieved.titles.size)
    }

    @Test
    fun `getProfile returns null for non-existent user`() = runTest {
        val profile = repository.getProfileSync("non_existent")
        assertNull(profile)
    }

    @Test
    fun `getProfile flow emits updates`() = runTest {
        val userId = "flow_test"

        // Initially null
        val initial = repository.getProfile(userId).first()
        assertNull(initial)

        // Save profile
        val profile = UserProfile.createDefault().copy(userId = userId, username = "FlowTest")
        repository.saveProfile(profile)

        // Should emit the saved profile
        val updated = repository.getProfile(userId).first()
        assertNotNull(updated)
        assertEquals("FlowTest", updated.username)
    }

    @Test
    fun `updateXP updates only XP-related fields`() = runTest {
        val profile = UserProfile.createDefault()
        repository.saveProfile(profile)

        // Update XP
        repository.updateXP(
            userId = "default",
            totalXP = 2500,
            currentXP = 200,
            level = 10,
            xpToNextLevel = 1000,
        )

        val updated = repository.getProfileSync("default")
        assertNotNull(updated)
        assertEquals(10, updated.level)
        assertEquals(2500, updated.totalXP)
        assertEquals(200, updated.currentXP)
        assertEquals(1000, updated.xpToNextLevel)

        // Other fields should remain unchanged
        assertEquals(profile.username, updated.username)
        assertEquals(profile.joinDate, updated.joinDate)
    }

    @Test
    fun `addTitle adds new title and deduplicates`() = runTest {
        val profile = UserProfile.createDefault().copy(
            titles = listOf("Beginner"),
        )
        repository.saveProfile(profile)

        // Add new title
        repository.addTitle("default", "Pro")

        val updated = repository.getProfileSync("default")
        assertNotNull(updated)
        assertEquals(2, updated.titles.size)
        assertTrue(updated.titles.contains("Beginner"))
        assertTrue(updated.titles.contains("Pro"))

        // Try to add duplicate
        repository.addTitle("default", "Pro")

        val afterDuplicate = repository.getProfileSync("default")
        assertNotNull(afterDuplicate)
        assertEquals(2, afterDuplicate.titles.size) // Still 2, not 3
    }

    @Test
    fun `addBadge adds new badge and deduplicates`() = runTest {
        val profile = UserProfile.createDefault().copy(
            badges = listOf("🏅"),
        )
        repository.saveProfile(profile)

        repository.addBadge("default", "📚")

        val updated = repository.getProfileSync("default")
        assertNotNull(updated)
        assertEquals(2, updated.badges.size)
        assertTrue(updated.badges.contains("🏅"))
        assertTrue(updated.badges.contains("📚"))

        // Duplicate
        repository.addBadge("default", "📚")

        val afterDuplicate = repository.getProfileSync("default")
        assertEquals(2, afterDuplicate!!.badges.size)
    }

    @Test
    fun `addTheme adds new theme and deduplicates`() = runTest {
        val profile = UserProfile.createDefault().copy(
            unlockedThemes = listOf("aurora"),
        )
        repository.saveProfile(profile)

        repository.addTheme("default", "dark_blue")

        val updated = repository.getProfileSync("default")
        assertNotNull(updated)
        assertEquals(2, updated.unlockedThemes.size)
        assertTrue(updated.unlockedThemes.contains("aurora"))
        assertTrue(updated.unlockedThemes.contains("dark_blue"))

        // Duplicate
        repository.addTheme("default", "dark_blue")

        val afterDuplicate = repository.getProfileSync("default")
        assertEquals(2, afterDuplicate!!.unlockedThemes.size)
    }

    @Test
    fun `updateAchievementCounts updates counts only`() = runTest {
        val profile = UserProfile.createDefault().copy(
            level = 5,
            totalXP = 1000,
        )
        repository.saveProfile(profile)

        repository.updateAchievementCounts(
            userId = "default",
            unlocked = 25,
            total = 100,
        )

        val updated = repository.getProfileSync("default")
        assertNotNull(updated)
        assertEquals(25, updated.achievementsUnlocked)
        assertEquals(100, updated.totalAchievements)

        // Other fields unchanged
        assertEquals(5, updated.level)
        assertEquals(1000, updated.totalXP)
    }

    @Test
    fun `deleteProfile removes profile`() = runTest {
        val profile = UserProfile.createDefault()
        repository.saveProfile(profile)

        // Verify exists
        val beforeDelete = repository.getProfileSync("default")
        assertNotNull(beforeDelete)

        // Delete
        repository.deleteProfile("default")

        // Verify deleted
        val afterDelete = repository.getProfileSync("default")
        assertNull(afterDelete)
    }

    @Test
    fun `JSON serialization handles empty lists`() = runTest {
        val profile = UserProfile.createDefault().copy(
            titles = emptyList(),
            badges = emptyList(),
            unlockedThemes = emptyList(),
        )

        repository.saveProfile(profile)

        val retrieved = repository.getProfileSync("default")
        assertNotNull(retrieved)
        assertEquals(0, retrieved.titles.size)
        assertEquals(0, retrieved.badges.size)
        assertEquals(0, retrieved.unlockedThemes.size)
    }

    @Test
    fun `JSON serialization handles complex lists`() = runTest {
        val profile = UserProfile.createDefault().copy(
            titles = listOf("Title 1", "Title with spaces", "Title_with_underscores"),
            badges = listOf("🏅", "📚", "🎬", "🔥"),
            unlockedThemes = listOf("theme-with-dashes", "theme_with_underscores", "THEME_CAPS"),
        )

        repository.saveProfile(profile)

        val retrieved = repository.getProfileSync("default")
        assertNotNull(retrieved)
        assertEquals(3, retrieved.titles.size)
        assertEquals("Title with spaces", retrieved.titles[1])
        assertEquals(4, retrieved.badges.size)
        assertEquals(3, retrieved.unlockedThemes.size)
    }

    @Test
    fun `multiple users can coexist`() = runTest {
        val user1 = UserProfile.createDefault().copy(userId = "user1", username = "Alice")
        val user2 = UserProfile.createDefault().copy(userId = "user2", username = "Bob")

        repository.saveProfile(user1)
        repository.saveProfile(user2)

        val retrieved1 = repository.getProfileSync("user1")
        val retrieved2 = repository.getProfileSync("user2")

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals("Alice", retrieved1.username)
        assertEquals("Bob", retrieved2.username)
    }

    @Test
    fun `addTitle does nothing if profile doesn't exist`() = runTest {
        // Should not crash
        repository.addTitle("non_existent", "Title")

        val profile = repository.getProfileSync("non_existent")
        assertNull(profile) // Profile was not created
    }
}
