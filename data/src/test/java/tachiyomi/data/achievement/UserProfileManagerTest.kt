package tachiyomi.data.achievement

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.model.UserProfile

class UserProfileManagerTest {

    private lateinit var userProfileManager: UserProfileManager

    @BeforeEach
    fun setup() {
        userProfileManager = UserProfileManager()
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
