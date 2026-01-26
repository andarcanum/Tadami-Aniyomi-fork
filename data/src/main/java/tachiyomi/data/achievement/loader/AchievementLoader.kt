package tachiyomi.data.achievement.loader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.handler.AchievementCalculator
import tachiyomi.data.achievement.model.AchievementDefinitions
import tachiyomi.data.achievement.model.AchievementJson
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository

class AchievementLoader(
    private val context: Context,
    private val repository: AchievementRepository,
    private val calculator: AchievementCalculator? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    },
) {

    companion object {
        private const val PREFS_NAME = "achievement_loader"
        private const val KEY_VERSION = "json_version"
        private const val KEY_CALCULATION_VERSION = "calculation_version"
    }

    suspend fun loadAchievements(): Result<Int> {
        return try {
            val definitions = loadJsonFromAssets()

            // Check version migration
            val currentVersion = getCurrentVersion()
            if (definitions.version <= currentVersion) {
                return Result.success(0)
            }

            // Insert achievements
            var inserted = 0
            definitions.achievements.forEach { achievementJson ->
                val achievement = achievementJson.toDomainModel()
                repository.insertAchievement(achievement)
                inserted++
            }

            // Save version
            saveVersion(definitions.version)

            // Trigger retroactive calculation on first load or version upgrade
            if (shouldCalculateInitialProgress(definitions.version)) {
                calculator?.let {
                    logcat(LogPriority.INFO) { "Running retroactive achievement calculation..." }
                    val result = it.calculateInitialProgress()
                    if (result.success) {
                        saveCalculationVersion(definitions.version)
                        logcat(LogPriority.INFO) {
                            "Retroactive calculation completed: ${result.achievementsUnlocked} achievements unlocked"
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Retroactive calculation failed: ${result.error}" }
                    }
                }
            }

            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun loadJsonFromAssets(): AchievementDefinitions = withContext(Dispatchers.IO) {
        val json = context.assets.open("achievements/achievements.json")
            .bufferedReader()
            .use { it.readText() }
        json.decodeFromString<AchievementDefinitions>(json)
    }

    private fun getCurrentVersion(): Int {
        return getPreferences().getInt(KEY_VERSION, 0)
    }

    private fun saveVersion(version: Int) {
        getPreferences().edit().putInt(KEY_VERSION, version).apply()
    }

    private fun shouldCalculateInitialProgress(jsonVersion: Int): Boolean {
        val calculationVersion = getCalculationVersion()
        return calculationVersion < jsonVersion
    }

    private fun getCalculationVersion(): Int {
        return getPreferences().getInt(KEY_CALCULATION_VERSION, 0)
    }

    private fun saveCalculationVersion(version: Int) {
        getPreferences().edit().putInt(KEY_CALCULATION_VERSION, version).apply()
    }

    private fun getPreferences(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun AchievementJson.toDomainModel(): Achievement {
        return Achievement(
            id = id,
            type = AchievementType.valueOf(type.uppercase()),
            category = AchievementCategory.valueOf(category.uppercase()),
            threshold = threshold,
            points = points,
            title = title,
            description = description,
            badgeIcon = badgeIcon,
            isHidden = isHidden,
            isSecret = isSecret,
            unlockableId = unlockableId,
            version = 1,
            createdAt = System.currentTimeMillis(),
        )
    }
}
