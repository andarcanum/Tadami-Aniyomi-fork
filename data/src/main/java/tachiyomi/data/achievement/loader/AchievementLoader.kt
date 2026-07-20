package tachiyomi.data.achievement.loader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.achievement.handler.AchievementCalculator
import tachiyomi.data.achievement.localization.AchievementTextResolver
import tachiyomi.data.achievement.model.AchievementDefinitions
import tachiyomi.data.achievement.model.AchievementJson
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementRarity
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import java.util.Locale

class AchievementLoader(
    private val context: Context,
    private val repository: AchievementRepository,
    private val textResolver: AchievementTextResolver,
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
        private const val KEY_PROGRESS_RULE_VERSION = "progress_rule_version"
        private const val CURRENT_PROGRESS_RULE_VERSION = 2
        private const val KEY_TREASURY_RECONCILIATION_VERSION = "treasury_reconciliation_version"
        private const val CURRENT_TREASURY_RECONCILIATION_VERSION = 2
        private const val KEY_LOCALE_TAG = "locale_tag"
    }

    suspend fun loadAchievements(): Result<Int> {
        return try {
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Loading achievements from JSON..." }
            val definitions = loadJsonFromAssets()

            // Check version migration
            val currentVersion = getCurrentVersion()
            val currentLocaleTag = getCurrentLocaleTag()
            val savedLocaleTag = getSavedLocaleTag()
            val localeChanged = shouldRefreshAchievementTexts(savedLocaleTag, currentLocaleTag)
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] JSON version: ${definitions.version}, current: $currentVersion" }
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] JSON definitions decoded: ${definitions.achievements.size} achievements found in file"
            }

            if (definitions.version <= currentVersion) {
                logcat(LogPriority.INFO) {
                    "[ACHIEVEMENTS] Achievements already up to date (version $currentVersion), skipping load"
                }
                if (!localeChanged) {
                    // Check if achievements exist in database
                    val existingAchievements = repository.getAll().first()
                    val existingCount = existingAchievements.size
                    logcat(LogPriority.INFO) {
                        "[ACHIEVEMENTS] Existing achievements in database: $existingCount, JSON has: ${definitions.achievements.size}"
                    }

                    val existingIds = existingAchievements.map { it.id }.toSet()
                    val jsonIds = definitions.achievements.map { it.id }.toSet()
                    val hasMissingAchievements = !existingIds.containsAll(jsonIds)

                    // Force reload if counts don't match (new achievements added) or some are missing
                    if (hasMissingAchievements || existingCount < definitions.achievements.size) {
                        logcat(LogPriority.WARN) {
                            "[ACHIEVEMENTS] WARNING: Database has fewer or missing achievements compared to JSON! Forcing reload..."
                        }
                        saveVersion(0)
                    } else if (existingCount == 0) {
                        logcat(LogPriority.WARN) {
                            "[ACHIEVEMENTS] WARNING: Version says up to date but database is empty! Forcing reload..."
                        }
                        saveVersion(0)
                    } else if (shouldBackfillRewards(existingAchievements, definitions.achievements)) {
                        logcat(LogPriority.WARN) {
                            "[ACHIEVEMENTS] WARNING: Reward data is missing for some achievements! Forcing reward backfill..."
                        }
                        // Drop the saved version gate so we re-insert reward-bearing rows
                        // without invalidating other persisted state like user progress.
                        saveVersion(0)
                    } else {
                        runRetroactiveCalculationIfNeeded(definitions.version)
                        runTreasuryReconciliationIfNeeded()
                        return Result.success(0)
                    }
                } else {
                    logcat(LogPriority.INFO) {
                        "[ACHIEVEMENTS] App locale changed from '$savedLocaleTag' to '$currentLocaleTag'; refreshing achievement texts"
                    }
                }
            }

            // Insert achievements
            var inserted = 0
            definitions.achievements.forEach { achievementJson ->
                val achievement = achievementJson.toDomainModel()
                repository.insertAchievement(achievement)
                inserted++
                logcat(LogPriority.VERBOSE) {
                    "[ACHIEVEMENTS] Inserted achievement: ${achievement.id} - ${achievement.title}"
                }
            }

            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Inserted $inserted achievements from JSON" }

            // Verify insertion
            val finalCount = repository.getAll().first().size
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Verification: Database now contains $finalCount achievements" }

            // Save version
            saveVersion(definitions.version)
            saveLocaleTag(currentLocaleTag)

            // Trigger retroactive calculation on first load, JSON version upgrade,
            // or achievement progress rule migration.
            runRetroactiveCalculationIfNeeded(definitions.version)

            Result.success(inserted)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "[ACHIEVEMENTS] Failed to load achievements from JSON: ${e.message}" }
            Result.failure(e)
        }
    }

    private suspend fun runRetroactiveCalculationIfNeeded(jsonVersion: Int) {
        if (!shouldCalculateInitialProgress(jsonVersion)) return
        calculator?.let {
            logcat(LogPriority.INFO) { "[ACHIEVEMENTS] Running retroactive achievement calculation..." }
            val result = it.calculateInitialProgress()
            if (result.success) {
                saveCalculationVersion(jsonVersion)
                saveProgressRuleVersion(CURRENT_PROGRESS_RULE_VERSION)
                saveTreasuryReconciliationVersion(CURRENT_TREASURY_RECONCILIATION_VERSION)
                logcat(LogPriority.INFO) {
                    "[ACHIEVEMENTS] Retroactive calculation completed: ${result.achievementsUnlocked} achievements unlocked"
                }
            } else {
                logcat(LogPriority.ERROR) { "[ACHIEVEMENTS] Retroactive calculation failed: ${result.error}" }
            }
        }
    }

    private suspend fun runTreasuryReconciliationIfNeeded() {
        if (getTreasuryReconciliationVersion() >= CURRENT_TREASURY_RECONCILIATION_VERSION) return
        calculator?.let {
            logcat(LogPriority.INFO) {
                "[ACHIEVEMENTS] Running one-time Treasury reward reconciliation..."
            }
            val result = it.calculateInitialProgress()
            if (result.success) {
                saveTreasuryReconciliationVersion(CURRENT_TREASURY_RECONCILIATION_VERSION)
                logcat(LogPriority.INFO) {
                    "[ACHIEVEMENTS] Treasury reward reconciliation completed"
                }
            } else {
                logcat(LogPriority.ERROR) {
                    "[ACHIEVEMENTS] Treasury reward reconciliation failed: ${result.error}"
                }
            }
        }
    }

    private fun getTreasuryReconciliationVersion(): Int {
        return getPreferences().getInt(KEY_TREASURY_RECONCILIATION_VERSION, 0)
    }

    private fun saveTreasuryReconciliationVersion(version: Int) {
        getPreferences().edit().putInt(KEY_TREASURY_RECONCILIATION_VERSION, version).apply()
    }

    private suspend fun loadJsonFromAssets(): AchievementDefinitions = withContext(Dispatchers.IO) {
        val jsonString = context.assets.open("achievements/achievements.json")
            .bufferedReader()
            .use { it.readText() }
        val normalizedJson = jsonString.trimStart('\uFEFF')
        json.decodeFromString<AchievementDefinitions>(normalizedJson)
    }

    private fun getCurrentVersion(): Int {
        return getPreferences().getInt(KEY_VERSION, 0)
    }

    private fun saveVersion(version: Int) {
        getPreferences().edit().putInt(KEY_VERSION, version).apply()
    }

    private fun getSavedLocaleTag(): String {
        return getPreferences().getString(KEY_LOCALE_TAG, "").orEmpty()
    }

    private fun saveLocaleTag(localeTag: String) {
        getPreferences().edit().putString(KEY_LOCALE_TAG, localeTag).apply()
    }

    private fun getCurrentLocaleTag(): String {
        return context.resources.configuration.locales[0]
            ?.toLanguageTag()
            ?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().toLanguageTag()
    }

    private fun shouldCalculateInitialProgress(jsonVersion: Int): Boolean {
        return shouldRecalculateAchievementProgress(
            calculationVersion = getCalculationVersion(),
            jsonVersion = jsonVersion,
            progressRuleVersion = getProgressRuleVersion(),
            currentProgressRuleVersion = CURRENT_PROGRESS_RULE_VERSION,
        )
    }

    private fun getCalculationVersion(): Int {
        return getPreferences().getInt(KEY_CALCULATION_VERSION, 0)
    }

    private fun saveCalculationVersion(version: Int) {
        getPreferences().edit().putInt(KEY_CALCULATION_VERSION, version).apply()
    }

    private fun getProgressRuleVersion(): Int {
        return getPreferences().getInt(KEY_PROGRESS_RULE_VERSION, 0)
    }

    private fun saveProgressRuleVersion(version: Int) {
        getPreferences().edit().putInt(KEY_PROGRESS_RULE_VERSION, version).apply()
    }

    private fun getPreferences(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun AchievementJson.toDomainModel(): Achievement {
        val localizedText = textResolver.resolve(this)
        return Achievement(
            id = id,
            type = AchievementType.valueOf(type.uppercase()),
            category = AchievementCategory.valueOf(category.uppercase()),
            threshold = threshold,
            points = points,
            title = localizedText.title,
            description = localizedText.description,
            badgeIcon = badgeIcon,
            isHidden = isHidden,
            isSecret = isSecret,
            unlockableId = unlockableId,
            version = 1,
            createdAt = 0L,
            rewards = rewards,
            rarity = AchievementRarity.fromString(rarity),
            tags = tags,
            hint = localizedText.hint ?: localizedText.hintVague ?: hint,
            hintVague = localizedText.hintVague,
            hintDirect = localizedText.hintDirect,
            hintObvious = localizedText.hintObvious,
            season = season,
            tierGroup = tierGroup,
            tierLevel = tierLevel,
            rewardSet = rewardSet,
        )
    }
}

internal fun shouldRefreshAchievementTexts(
    savedLocaleTag: String?,
    currentLocaleTag: String,
): Boolean {
    return savedLocaleTag.orEmpty() != currentLocaleTag
}

/**
 * Decide whether the loader must rewrite achievement rows from JSON even
 * though the saved version number says it is up to date.
 *
 * Older installs persisted achievement rows with a NULL `rewards` column.
 * If a JSON definition carries rewards for an achievement and the matching
 * DB row does not, we have to backfill the rewards data without forcing the
 * user through a destructive version bump.
 */
internal fun shouldBackfillRewards(
    existing: List<Achievement>,
    json: List<AchievementJson>,
): Boolean {
    if (json.isEmpty()) return false
    val existingById = existing.associateBy { it.id }
    return json.any { jsonEntry ->
        val jsonRewards = jsonEntry.rewards
        if (jsonRewards.isNullOrEmpty()) return@any false
        val dbEntry = existingById[jsonEntry.id] ?: return@any true
        dbEntry.rewards.isNullOrEmpty()
    }
}

internal fun shouldRecalculateAchievementProgress(
    calculationVersion: Int,
    jsonVersion: Int,
    progressRuleVersion: Int,
    currentProgressRuleVersion: Int,
): Boolean {
    return calculationVersion < jsonVersion || progressRuleVersion < currentProgressRuleVersion
}
