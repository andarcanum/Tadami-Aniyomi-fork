package tachiyomi.data.achievement

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.achievement.model.Achievement

/**
 * Manages unlockable content that is unlocked via achievements.
 * Handles themes, badges, display preferences, and other unlockables.
 */
class UnlockableManager(
    private val preferences: SharedPreferences,
    private val userProfileManager: UserProfileManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val PREFIX = "unlocked_"
        private val EXCLUSIVE_THEME_IDS = setOf("ONYX_GOLD", "SAKURA_NOIR", "NEBULA_TIDE")
    }

    /**
     * Check if an unlockable is unlocked
     */
    fun isUnlockableUnlocked(unlockableId: String): Boolean {
        return preferences.getBoolean("$PREFIX$unlockableId", false)
    }

    /**
     * Mark an unlockable as unlocked
     */
    fun setUnlockableUnlocked(unlockableId: String) {
        preferences.edit {
            putBoolean("$PREFIX$unlockableId", true)
        }
        logcat(LogPriority.INFO) { "Unlockable unlocked: $unlockableId" }
    }

    /**
     * Get all unlocked unlockables
     */
    fun getUnlockedUnlockables(): Set<String> {
        val allKeys = preferences.all.keys
        return allKeys
            .filter { it.startsWith(PREFIX) }
            .filter { preferences.getBoolean(it, false) }
            .map { it.removePrefix(PREFIX) }
            .toSet()
    }

    /**
     * Unlock rewards for an achievement
     * Called when an achievement is unlocked
     */
    suspend fun unlockAchievementRewards(achievement: Achievement) {
        // Unlock main unlockable if exists
        achievement.unlockableId?.let { unlockableId ->
            setUnlockableUnlocked(unlockableId)
            // Apply the unlockable effect
            applyUnlockable(unlockableId)
        }

        // Unlock all rewards in the rewards list
        achievement.rewards?.forEach { reward ->
            setUnlockableUnlocked(reward.id)
            applyUnlockable(reward.id)
        }
    }

    /**
     * Apply an unlockable effect
     * This handles themes, badges, display preferences, etc.
     */
    private suspend fun applyUnlockable(unlockableId: String) = withContext(Dispatchers.Default) {
        when {
            // Theme unlockables
            unlockableId.startsWith("theme_") -> {
                val themeId = unlockableId.removePrefix("theme_")
                scope.launch {
                    try {
                        userProfileManager.unlockTheme(themeId)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "Failed to unlock theme $themeId: ${e.message}" }
                    }
                }
            }

            unlockableId.startsWith("badge_") -> {
                val badgeName = unlockableId.removePrefix("badge_")
                scope.launch {
                    try {
                        userProfileManager.addBadge(badgeName)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) { "Failed to add badge $badgeName: ${e.message}" }
                    }
                }
            }

            // Aura unlockables
            unlockableId.startsWith("aura_") -> {
                logcat(LogPriority.INFO) { "Aura unlocked: $unlockableId" }
            }

            // Display preference unlockables
            unlockableId.startsWith("display_") -> {
                logcat(LogPriority.INFO) { "Display preference unlocked: $unlockableId" }
                // TODO: Unlock display options (grid sizes, layouts, etc.)
                // Example: display_grid_large, display_list_compact
            }

            // Profile cosmetic unlockables
            unlockableId.startsWith("profile_") -> {
                logcat(LogPriority.INFO) { "Profile reward unlocked: $unlockableId" }
            }

            // Avatar cosmetic unlockables
            unlockableId.startsWith("avatar_") -> {
                logcat(LogPriority.INFO) { "Avatar reward unlocked: $unlockableId" }
            }

            // Reader preset unlockables
            unlockableId.startsWith("reader_") -> {
                logcat(LogPriority.INFO) { "Reader preset unlocked: $unlockableId" }
            }

            // Home/Aurora preset unlockables
            unlockableId.startsWith("home_") -> {
                logcat(LogPriority.INFO) { "Home preset unlocked: $unlockableId" }
            }

            // Special visual unlockables
            unlockableId.startsWith("special_") -> {
                logcat(LogPriority.INFO) { "Special visual reward unlocked: $unlockableId" }
            }

            else -> {
                logcat(LogPriority.WARN) { "Unknown unlockable type: $unlockableId" }
            }
        }
    }

    /**
     * Check if a theme is available (unlocked)
     */
    fun isThemeAvailable(themeId: String): Boolean {
        val normalizedThemeId = themeId.removePrefix("theme_")
        if (!EXCLUSIVE_THEME_IDS.contains(normalizedThemeId.uppercase())) return true

        val bypass = preferences.getBoolean("debug_bypass_treasury_locks", false)
        if (bypass) return true

        val canonicalThemeId = normalizedThemeId.uppercase()
        return isUnlockableUnlocked("theme_$canonicalThemeId") ||
            isUnlockableUnlocked("theme_${canonicalThemeId.lowercase()}")
    }

    /**
     * Check if a badge is available (unlocked)
     */
    fun isBadgeAvailable(badgeId: String): Boolean {
        return when {
            // Default badges are always available
            badgeId.startsWith("default_") -> true
            // Achievement badges need to be unlocked
            badgeId.startsWith("achievement_") -> isUnlockableUnlocked("badge_$badgeId")
            else -> true
        }
    }

    /**
     * Check if a display preference is available (unlocked)
     */
    fun isDisplayPreferenceAvailable(prefId: String): Boolean {
        return when {
            // Default preferences are always available
            prefId.startsWith("default_") -> true
            // Achievement preferences need to be unlocked
            prefId.startsWith("achievement_") -> isUnlockableUnlocked("display_$prefId")
            else -> true
        }
    }

    /**
     * Get unlockable display name (localized)
     */
    fun getUnlockableName(unlockableId: String): String {
        return when (unlockableId) {
            // Themes
            "theme_achievement_gold" -> "Золотая тема достижений"
            "theme_achievement_sapphire" -> "Сапфировая тема достижений"
            "theme_master" -> "Тема мастера контента"
            "theme_ONYX_GOLD" -> "Обсидиановое золото"
            "theme_SAKURA_NOIR" -> "Сакура Ноир"
            "theme_NEBULA_TIDE" -> "Туманность прилива"

            // Badges
            "badge_achievement_master" -> "Бейдж мастера достижений"
            "badge_week_warrior" -> "Бейдж воина недели"

            // Display preferences
            "display_grid_large" -> "Большая сетка библиотеки"
            "display_list_compact" -> "Компактный список"
            "display_grid_extra_large" -> "Очень большая сетка"

            // Auras
            "aura_harem" -> "Гаремная аура"
            "aura_level_up" -> "Аура мастера достижений"
            "aura_matrix" -> "Цифровой дождь"

            // Profile presets
            "profile_nickname_effect_aurora_crown" -> "Никнейм-эффект «Aurora Crown»"
            "profile_nickname_effect_glitch_rune" -> "Никнейм-эффект «Glitch Rune»"
            "profile_nickname_effect_cipher" -> "Никнейм-эффект «Cipher Sigil»"

            // Avatar presets
            "avatar_frame_neon" -> "Неоновая рамка аватара"
            "avatar_frame_hologram" -> "Голографическая рамка аватара"
            "avatar_frame_prismatic" -> "Призматическая рамка аватара"

            // Home presets
            "home_badge_orbit" -> "Эффект Home Hub «Orbit»"
            "home_badge_crown" -> "Эффект Home Hub «Crown»"
            "home_badge_shuriken" -> "Эффект Home Hub «Shuriken»"

            // Special visual rewards
            "special_background_petal_storm" -> "Фон «Лепестковый шторм»"
            "special_background_neon_orbit" -> "Фон «Неоновая орбита»"

            else ->
                unlockableId
                    .removePrefix("theme_")
                    .removePrefix("aura_")
                    .removePrefix("special_")
                    .replace("_", " ")
                    .capitalize()
        }
    }

    /**
     * Get unlockable type (theme, badge, display, etc.)
     */
    fun getUnlockableType(unlockableId: String): UnlockableType {
        return when {
            unlockableId.startsWith("theme_") -> UnlockableType.THEME
            unlockableId.startsWith("aura_") -> UnlockableType.AURA
            unlockableId.startsWith("badge_") -> UnlockableType.BADGE
            unlockableId.startsWith("display_") -> UnlockableType.DISPLAY
            unlockableId.startsWith("profile_") -> UnlockableType.PROFILE
            unlockableId.startsWith("avatar_") -> UnlockableType.AVATAR
            unlockableId.startsWith("reader_") -> UnlockableType.READER
            unlockableId.startsWith("home_") -> UnlockableType.HOME
            unlockableId.startsWith("special_") -> UnlockableType.SPECIAL
            else -> UnlockableType.UNKNOWN
        }
    }

    /**
     * Reset all unlockables (for testing/debugging)
     */
    fun resetAllUnlockables() {
        val allKeys = preferences.all.keys.filter { it.startsWith(PREFIX) }
        preferences.edit {
            allKeys.forEach { remove(it) }
        }
        logcat(LogPriority.INFO) { "All unlockables reset" }
    }

    /**
     * Rebuild unlockable prefs from the current set of unlocked achievements.
     * Clears all existing prefs first, then re-derives them from DB state.
     * Use after any operation that might cause DB ↔ prefs desync.
     */
    suspend fun recomputeUnlockablesFromUnlockedAchievements(
        unlockedAchievements: List<Achievement>,
    ) {
        resetAllUnlockables()
        unlockedAchievements.forEach { achievement ->
            unlockAchievementRewards(achievement)
        }
        logcat(LogPriority.INFO) {
            "Recomputed unlockables from ${unlockedAchievements.size} unlocked achievement(s)"
        }
    }

    /**
     * Lock the unlockables derived from a single achievement.
     * Used when sanitization invalidates an unlock that had no real history.
     */
    fun lockUnlockablesForAchievement(achievement: Achievement) {
        achievement.unlockableId?.let { unlockableId ->
            preferences.edit { remove("$PREFIX$unlockableId") }
            removeUnlockableFromProfile(unlockableId)
        }
        achievement.rewards?.forEach { reward ->
            preferences.edit { remove("$PREFIX${reward.id}") }
            removeUnlockableFromProfile(reward.id)
        }
        logcat(LogPriority.INFO) {
            "Locked unlockables for achievement: ${achievement.id}"
        }
    }

    private fun removeUnlockableFromProfile(unlockableId: String) {
        scope.launch {
            try {
                when {
                    unlockableId.startsWith("theme_") -> {
                        userProfileManager.removeTheme(unlockableId.removePrefix("theme_"))
                    }
                    unlockableId.startsWith("badge_") -> {
                        userProfileManager.removeBadge(unlockableId.removePrefix("badge_"))
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to remove unlockable $unlockableId from profile: ${e.message}"
                }
            }
        }
    }
}

/**
 * Types of unlockables
 */
enum class UnlockableType {
    THEME,
    AURA,
    BADGE,
    DISPLAY,
    PROFILE,
    AVATAR,
    READER,
    HOME,
    SPECIAL,
    UNKNOWN,
}

private inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    action(editor)
    if (commit) {
        editor.commit()
    } else {
        editor.apply()
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
