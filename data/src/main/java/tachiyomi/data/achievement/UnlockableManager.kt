package tachiyomi.data.achievement

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
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
) {

    companion object {
        private const val PREFIX = "unlocked_"
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
        achievement.unlockableId?.let { unlockableId ->
            setUnlockableUnlocked(unlockableId)
            // Apply the unlockable effect
            applyUnlockable(unlockableId)
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
                logcat(LogPriority.INFO) { "Theme unlocked: $unlockableId" }
                // TODO: Integrate with theme system
                // Themes would be made available in theme selection
            }

            // Badge unlockables
            unlockableId.startsWith("badge_") -> {
                logcat(LogPriority.INFO) { "Badge unlocked: $unlockableId" }
                // TODO: Store badge for profile display
                // Badge would be available in profile customization
            }

            // Display preference unlockables
            unlockableId.startsWith("display_") -> {
                logcat(LogPriority.INFO) { "Display preference unlocked: $unlockableId" }
                // TODO: Unlock display options (grid sizes, layouts, etc.)
                // Example: display_grid_large, display_list_compact
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
        return when {
            // Default themes are always available
            themeId.startsWith("default_") -> true
            // Achievement themes need to be unlocked
            themeId.startsWith("achievement_") -> isUnlockableUnlocked("theme_$themeId")
            else -> true
        }
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

            // Badges
            "badge_achievement_master" -> "Бейдж мастера достижений"
            "badge_week_warrior" -> "Бейдж воина недели"

            // Display preferences
            "display_grid_large" -> "Большая сетка библиотеки"
            "display_list_compact" -> "Компактный список"
            "display_grid_extra_large" -> "Очень большая сетка"

            else -> unlockableId.replace("_", " ").capitalize()
        }
    }

    /**
     * Get unlockable type (theme, badge, display, etc.)
     */
    fun getUnlockableType(unlockableId: String): UnlockableType {
        return when {
            unlockableId.startsWith("theme_") -> UnlockableType.THEME
            unlockableId.startsWith("badge_") -> UnlockableType.BADGE
            unlockableId.startsWith("display_") -> UnlockableType.DISPLAY
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
}

/**
 * Types of unlockables
 */
enum class UnlockableType {
    THEME,
    BADGE,
    DISPLAY,
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
