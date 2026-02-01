package tachiyomi.data.achievement

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.model.UserProfile
import tachiyomi.domain.achievement.repository.UserProfileRepository

class UserProfileManagerTest {

    private lateinit var userProfileManager: UserProfileManager
    private lateinit var mockRepository: UserProfileRepository

    @BeforeEach
    fun setup() {
        var currentProfile = UserProfile.createDefault()

        mockRepository = mockk(relaxed = true) {
            coEvery { getProfileSync(any()) } answers { currentProfile }
            coEvery { saveProfile(any()) } answers { currentProfile = firstArg() }

            // Mock updateXP - вызывается из addXP()
            coEvery { updateXP(any(), any(), any(), any(), any()) } answers {
                val totalXP = secondArg<Int>()
                val currentXP = thirdArg<Int>()
                val level = arg<Int>(3)
                val xpToNextLevel = arg<Int>(4)
                currentProfile = currentProfile.copy(
                    totalXP = totalXP,
                    currentXP = currentXP,
                    level = level,
                    xpToNextLevel = xpToNextLevel
                )
            }

            // Mock addTitle - вызывается из addTitle()
            coEvery { addTitle(any(), any()) } answers {
                val title = secondArg<String>()
                currentProfile = currentProfile.copy(
                    titles = currentProfile.titles + title
                )
            }

            // Mock addBadge - вызывается из addBadge()
            coEvery { addBadge(any(), any()) } answers {
                val badge = secondArg<String>()
                currentProfile = currentProfile.copy(
                    badges = currentProfile.badges + badge
                )
            }

            // Mock addTheme - вызывается из unlockTheme()
            coEvery { addTheme(any(), any()) } answers {
                val themeId = secondArg<String>()
                currentProfile = currentProfile.copy(
                    unlockedThemes = currentProfile.unlockedThemes + themeId
                )
            }

            // Mock updateAchievementCounts - вызывается из updateAchievementsCount()
            coEvery { updateAchievementCounts(any(), any(), any()) } answers {
                val unlocked = secondArg<Int>()
                val total = thirdArg<Int>()
                currentProfile = currentProfile.copy(
                    achievementsUnlocked = unlocked,
                    totalAchievements = total
                )
            }

            every { getProfile(any()) } returns flowOf(currentProfile)
        }

        userProfileManager = UserProfileManager(mockRepository)
    }

    @Test
    fun `addXP increases total XP`() = runTest {
        userProfileManager.loadProfile()
        val initialProfile = userProfileManager.getCurrentProfile()

        userProfileManager.addXP(50)

        val updatedProfile = userProfileManager.getCurrentProfile()
        assert(updatedProfile.totalXP == initialProfile.totalXP + 50)
    }

    @Test
    fun `addXP can trigger level up`() = runTest {
        // Добавляем достаточно XP для повышения уровня
        // Уровень 1 → 2 требует 282 XP (100 * 2^1.5)
        val leveledUp = userProfileManager.addXP(300)

        assert(leveledUp == true)
        val profile = userProfileManager.getCurrentProfile()
        assert(profile.level >= 2)
    }

    @Test
    fun `addTitle adds title to profile`() = runTest {
        userProfileManager.loadProfile()

        userProfileManager.addTitle("Магистр ордена")

        val profile = userProfileManager.getCurrentProfile()
        assert(profile.hasTitle("Магистр ордена"))
    }

    @Test
    fun `addTitle does not add duplicate titles`() = runTest {
        userProfileManager.addTitle("Читатель")
        userProfileManager.addTitle("Читатель")

        val profile = userProfileManager.getCurrentProfile()
        assert(profile.titles.count { it == "Читатель" } == 1)
    }

    @Test
    fun `addBadge adds badge to profile`() = runTest {
        userProfileManager.addBadge("Бета-тестер")

        val profile = userProfileManager.getCurrentProfile()
        assert(profile.hasBadge("Бета-тестер"))
    }

    @Test
    fun `unlockTheme adds theme to profile`() = runTest {
        userProfileManager.unlockTheme("dark_theme")

        val profile = userProfileManager.getCurrentProfile()
        assert(profile.hasTheme("dark_theme"))
    }

    @Test
    fun `grantRewards grants all reward types`() = runTest {
        val rewards = listOf(
            Reward.experience(100, "100 XP"),
            Reward.title("bookworm", "Книголюб"),
            Reward.badge("early_adopter", "Ранний адоптер"),
            Reward.theme("midnight", "Полуночная тема"),
        )

        userProfileManager.grantRewards(rewards)

        val profile = userProfileManager.getCurrentProfile()
        assert(profile.totalXP >= 100)
        assert(profile.hasTitle("Книголюб"))
        assert(profile.hasBadge("Ранний адоптер"))
        assert(profile.hasTheme("midnight"))
    }

    @Test
    fun `getLevelName returns correct level names`() = runTest {
        val profile1 = UserProfile.createDefault().copy(level = 1)
        assert(profile1.getLevelName() == "Новичок")

        val profile5 = UserProfile.createDefault().copy(level = 5)
        assert(profile5.getLevelName() == "Опытный")

        val profile25 = UserProfile.createDefault().copy(level = 25)
        assert(profile25.getLevelName() == "Эксперт")

        val profile100 = UserProfile.createDefault().copy(level = 100)
        assert(profile100.getLevelName() == "Легенда")
    }

    @Test
    fun `levelProgress calculates correctly`() = runTest {
        val profile = UserProfile.createDefault().copy(
            currentXP = 50,
            xpToNextLevel = 100,
        )

        val progress = profile.levelProgress
        assert(progress == 0.5f) // 50/100 = 0.5
    }

    @Test
    fun `Reward factory methods create correct rewards`() {
        val xpReward = Reward.experience(100)
        assert(xpReward.type == RewardType.EXPERIENCE)
        assert(xpReward.value == 100)

        val titleReward = Reward.title("test", "Test Title")
        assert(titleReward.type == RewardType.TITLE)
        assert(titleReward.title == "Test Title")

        val themeReward = Reward.theme("dark", "Dark Theme")
        assert(themeReward.type == RewardType.THEME)
        assert(themeReward.title == "Dark Theme")

        val badgeReward = Reward.badge("beta", "Beta Tester")
        assert(badgeReward.type == RewardType.BADGE)
        assert(badgeReward.title == "Beta Tester")
    }

    @Test
    fun `updateAchievementsCount updates counts`() = runTest {
        userProfileManager.updateAchievementsCount(5, 10)

        val profile = userProfileManager.getCurrentProfile()
        assert(profile.achievementsUnlocked == 5)
        assert(profile.totalAchievements == 10)
    }
}
