package eu.kanade.domain.ui

import tachiyomi.core.common.preference.PreferenceStore

class UserProfilePreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun name() = preferenceStore.getString("user_profile_name", DEFAULT_NAME)
    fun avatarUrl() = preferenceStore.getString("user_profile_avatar_url", "")
    fun lastOpenedTime() = preferenceStore.getLong("user_profile_last_opened", 0L)
    fun totalLaunches() = preferenceStore.getLong("user_profile_total_launches", 0L)
    fun recentGreetingIds() = preferenceStore.getString("user_profile_recent_greeting_ids", "")
    fun recentScenarioIds() = preferenceStore.getString("user_profile_recent_scenario_ids", "")
    fun nameEdited() = preferenceStore.getBoolean("user_profile_name_edited", false)

    fun nicknameFont() = preferenceStore.getString("user_profile_name_font", "default")
    fun nicknameColor() = preferenceStore.getString("user_profile_name_color", "theme")
    fun nicknameCustomColorHex() = preferenceStore.getString("user_profile_name_custom_color_hex", "#FFFFFF")
    fun nicknameOutline() = preferenceStore.getBoolean("user_profile_name_outline", false)
    fun nicknameOutlineWidth() = preferenceStore.getInt("user_profile_name_outline_width", 2)
    fun nicknameGlow() = preferenceStore.getBoolean("user_profile_name_glow", false)
    fun nicknameEffect() = preferenceStore.getString("user_profile_name_effect", "none")

    fun showHomeGreeting() = preferenceStore.getBoolean("user_profile_show_home_greeting", true)
    fun showHomeStreak() = preferenceStore.getBoolean("user_profile_show_home_streak", true)
    fun homeHubLastSection() = preferenceStore.getString("user_profile_home_hub_last_section", "anime")
    fun greetingFont() = preferenceStore.getString("user_profile_greeting_font", "default")
    fun greetingFontSize() = preferenceStore.getInt("user_profile_greeting_font_size", 12)
    fun greetingColor() = preferenceStore.getString("user_profile_greeting_color", "theme")
    fun greetingCustomColorHex() = preferenceStore.getString("user_profile_greeting_custom_color_hex", "#FFFFFF")
    fun greetingDecoration() = preferenceStore.getString("user_profile_greeting_decoration", "none")
    fun greetingItalic() = preferenceStore.getBoolean("user_profile_greeting_italic", false)

    fun getRecentGreetingHistory(limit: Int = RECENT_HISTORY_LIMIT): List<String> {
        return decodeHistory(recentGreetingIds().get(), limit)
    }

    fun getRecentScenarioHistory(limit: Int = RECENT_HISTORY_LIMIT): List<String> {
        return decodeHistory(recentScenarioIds().get(), limit)
    }

    fun appendRecentGreetingId(id: String, limit: Int = RECENT_HISTORY_LIMIT) {
        recentGreetingIds().set(encodeHistory(prependHistory(getRecentGreetingHistory(limit), id, limit)))
    }

    fun appendRecentScenarioId(id: String, limit: Int = RECENT_HISTORY_LIMIT) {
        recentScenarioIds().set(encodeHistory(prependHistory(getRecentScenarioHistory(limit), id, limit)))
    }

    private fun decodeHistory(raw: String, limit: Int): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(HISTORY_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(limit)
    }

    private fun encodeHistory(items: List<String>): String {
        return items.joinToString(HISTORY_SEPARATOR)
    }

    private fun prependHistory(existing: List<String>, id: String, limit: Int): List<String> {
        if (id.isBlank()) return existing.take(limit)
        return (listOf(id) + existing.filterNot { it == id }).take(limit)
    }

    companion object {
        private const val HISTORY_SEPARATOR = "|"
        private const val RECENT_HISTORY_LIMIT = 3
        const val DEFAULT_NAME = "\u0417\u0440\u0438\u0442\u0435\u043b\u044c"
    }
}
