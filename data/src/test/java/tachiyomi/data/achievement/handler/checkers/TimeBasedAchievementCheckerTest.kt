package tachiyomi.data.achievement.handler.checkers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.handler.FeatureUsageCollector
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType

class TimeBasedAchievementCheckerTest {

    private lateinit var eventBus: AchievementEventBus
    private lateinit var featureCollector: FeatureUsageCollector
    private lateinit var checker: TimeBasedAchievementChecker

    @BeforeEach
    fun setup() {
        eventBus = mockk(relaxed = true)
        featureCollector = FeatureUsageCollector(eventBus)
        checker = TimeBasedAchievementChecker(eventBus, featureCollector)
    }

    @Test
    fun `check returns false for non time_based achievements`() = runTest {
        val achievement = Achievement(
            id = "test",
            title = "test",
            type = AchievementType.QUANTITY,
            category = tachiyomi.domain.achievement.model.AchievementCategory.MANGA,
        )
        val progress = AchievementProgress.createStandard("test", 0, 10, false)

        val result = checker.check(achievement, progress)

        assert(result == false)
    }

    @Test
    fun `checkNightOwl returns true for night sessions`() = runTest {
        val achievement = Achievement(
            id = "night_owl",
            title = "night_owl",
            type = AchievementType.TIME_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        )
        val progress = AchievementProgress.createStandard("night_owl", 0, 1, false)

        // Создаем ночную сессию (3 AM)
        featureCollector.onSessionStart(System.currentTimeMillis(), 3)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `checkNightOwl returns false for non-night sessions`() = runTest {
        val achievement = Achievement(
            id = "night_owl",
            title = "night_owl",
            type = AchievementType.TIME_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        )
        val progress = AchievementProgress.createStandard("night_owl", 0, 1, false)

        // Создаем дневную сессию (10 AM)
        featureCollector.onSessionStart(System.currentTimeMillis(), 10)

        val result = checker.check(achievement, progress)

        assert(result == false)
    }

    @Test
    fun `checkEarlyBird returns true for morning sessions`() = runTest {
        val achievement = Achievement(
            id = "early_bird",
            title = "early_bird",
            type = AchievementType.TIME_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        )
        val progress = AchievementProgress.createStandard("early_bird", 0, 1, false)

        // Создаем утреннюю сессию (7 AM)
        featureCollector.onSessionStart(System.currentTimeMillis(), 7)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `checkEarlyBird returns false for non-morning sessions`() = runTest {
        val achievement = Achievement(
            id = "early_bird",
            title = "early_bird",
            type = AchievementType.TIME_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        )
        val progress = AchievementProgress.createStandard("early_bird", 0, 1, false)

        // Создаем вечернюю сессию (8 PM)
        featureCollector.onSessionStart(System.currentTimeMillis(), 20)

        val result = checker.check(achievement, progress)

        assert(result == false)
    }

    @Test
    fun `checkMarathonReader returns true for long sessions`() = runTest {
        val achievement = Achievement(
            id = "marathon_reader",
            title = "marathon_reader",
            type = AchievementType.TIME_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        )
        val progress = AchievementProgress.createStandard("marathon_reader", 0, 1, false)

        // Создаем длинную сессию (> 2 часа)
        featureCollector.onSessionEnd(7_500_000L) // ~2 часа 5 мин

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `checkMarathonReader returns false for short sessions`() = runTest {
        val achievement = Achievement(
            id = "marathon_reader",
            title = "marathon_reader",
            type = AchievementType.TIME_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        )
        val progress = AchievementProgress.createStandard("marathon_reader", 0, 1, false)

        // Создаем короткую сессию (< 2 часа)
        featureCollector.onSessionEnd(3_600_000L) // 1 час

        val result = checker.check(achievement, progress)

        assert(result == false)
    }

    @Test
    fun `getProgress for marathon_reader calculates correctly`() = runTest {
        val achievement = Achievement(
            id = "marathon_reader",
            title = "marathon_reader",
            type = AchievementType.TIME_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
        )
        val progress = AchievementProgress.createStandard("marathon_reader", 0, 1, false)

        // Сессия 1.5 часа
        featureCollector.onSessionEnd(5_400_000L)

        val progressValue = checker.getProgress(achievement, progress)

        // 1.5 часа из 2 часов = 0.75
        assert(progressValue == 0.75f)
    }
}
