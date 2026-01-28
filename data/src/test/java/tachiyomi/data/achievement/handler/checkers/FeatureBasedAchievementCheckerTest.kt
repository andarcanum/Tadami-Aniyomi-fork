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

class FeatureBasedAchievementCheckerTest {

    private lateinit var eventBus: AchievementEventBus
    private lateinit var featureCollector: FeatureUsageCollector
    private lateinit var checker: FeatureBasedAchievementChecker

    @BeforeEach
    fun setup() {
        eventBus = mockk(relaxed = true)
        featureCollector = FeatureUsageCollector(eventBus)
        checker = FeatureBasedAchievementChecker(eventBus, featureCollector)
    }

    @Test
    fun `check returns false for non feature_based achievements`() = runTest {
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
    fun `check download_starter unlocks at 10 downloads`() = runTest {
        val achievement = Achievement(
            id = "download_starter",
            title = "download_starter",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 10,
        )
        val progress = AchievementProgress.createStandard("download_starter", 0, 10, false)

        // 10 скачиваний
        featureCollector.onFeatureUsed(AchievementEvent.Feature.DOWNLOAD, 10)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `check download_starter does not unlock before threshold`() = runTest {
        val achievement = Achievement(
            id = "download_starter",
            title = "download_starter",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 10,
        )
        val progress = AchievementProgress.createStandard("download_starter", 0, 10, false)

        // Только 5 скачиваний
        featureCollector.onFeatureUsed(AchievementEvent.Feature.DOWNLOAD, 5)

        val result = checker.check(achievement, progress)

        assert(result == false)
    }

    @Test
    fun `check search_user counts both search and advanced search`() = runTest {
        val achievement = Achievement(
            id = "search_user",
            title = "search_user",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 25,
        )
        val progress = AchievementProgress.createStandard("search_user", 0, 25, false)

        // 15 обычных + 10 расширенных = 25
        featureCollector.onFeatureUsed(AchievementEvent.Feature.SEARCH, 15)
        featureCollector.onFeatureUsed(AchievementEvent.Feature.ADVANCED_SEARCH, 10)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `check advanced_explorer counts only advanced search`() = runTest {
        val achievement = Achievement(
            id = "advanced_explorer",
            title = "advanced_explorer",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 50,
        )
        val progress = AchievementProgress.createStandard("advanced_explorer", 0, 50, false)

        // 100 обычных не считаются
        featureCollector.onFeatureUsed(AchievementEvent.Feature.SEARCH, 100)
        // 50 расширенных считаются
        featureCollector.onFeatureUsed(AchievementEvent.Feature.ADVANCED_SEARCH, 50)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `check filter_master unlocks at 20 filters`() = runTest {
        val achievement = Achievement(
            id = "filter_master",
            title = "filter_master",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 20,
        )
        val progress = AchievementProgress.createStandard("filter_master", 0, 20, false)

        featureCollector.onFeatureUsed(AchievementEvent.Feature.FILTER, 20)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `check backup_master unlocks at 1 backup`() = runTest {
        val achievement = Achievement(
            id = "backup_master",
            title = "backup_master",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 1,
        )
        val progress = AchievementProgress.createStandard("backup_master", 0, 1, false)

        featureCollector.onFeatureUsed(AchievementEvent.Feature.BACKUP, 1)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `check settings_explorer unlocks at 15 settings opens`() = runTest {
        val achievement = Achievement(
            id = "settings_explorer",
            title = "settings_explorer",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 15,
        )
        val progress = AchievementProgress.createStandard("settings_explorer", 0, 15, false)

        featureCollector.onFeatureUsed(AchievementEvent.Feature.SETTINGS, 15)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `check stats_viewer unlocks at 5 stats opens`() = runTest {
        val achievement = Achievement(
            id = "stats_viewer",
            title = "stats_viewer",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 5,
        )
        val progress = AchievementProgress.createStandard("stats_viewer", 0, 5, false)

        featureCollector.onFeatureUsed(AchievementEvent.Feature.STATS, 5)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `check theme_changer unlocks at 10 theme changes`() = runTest {
        val achievement = Achievement(
            id = "theme_changer",
            title = "theme_changer",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 10,
        )
        val progress = AchievementProgress.createStandard("theme_changer", 0, 10, false)

        featureCollector.onFeatureUsed(AchievementEvent.Feature.THEME_CHANGE, 10)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `check persistent_clicker unlocks at 10 logo clicks`() = runTest {
        val achievement = Achievement(
            id = "persistent_clicker",
            title = "persistent_clicker",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.SECRET,
            threshold = 10,
        )
        val progress = AchievementProgress.createStandard("persistent_clicker", 0, 10, false)

        featureCollector.onFeatureUsed(AchievementEvent.Feature.LOGO_CLICK, 10)

        val result = checker.check(achievement, progress)

        assert(result == true)
    }

    @Test
    fun `getProgress for download_starter calculates correctly`() = runTest {
        val achievement = Achievement(
            id = "download_starter",
            title = "download_starter",
            type = AchievementType.FEATURE_BASED,
            category = tachiyomi.domain.achievement.model.AchievementCategory.BOTH,
            threshold = 10,
        )
        val progress = AchievementProgress.createStandard("download_starter", 0, 10, false)

        // 5 из 10 скачиваний
        featureCollector.onFeatureUsed(AchievementEvent.Feature.DOWNLOAD, 5)

        val progressValue = checker.getProgress(achievement, progress)

        assert(progressValue == 0.5f)
    }
}
