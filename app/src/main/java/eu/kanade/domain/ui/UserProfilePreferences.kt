package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.HomeHeaderLayoutSpec
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
    fun recentGreetingEvents() = preferenceStore.getString("user_profile_recent_greeting_events", "")
    fun nameEdited() = preferenceStore.getBoolean("user_profile_name_edited", false)

    fun nicknameFont() = preferenceStore.getString("user_profile_name_font", "default")
    fun nicknameFontSize() = preferenceStore.getInt("user_profile_name_font_size", 24)
    fun nicknameColor() = preferenceStore.getString("user_profile_name_color", "theme")
    fun nicknameCustomColorHex() = preferenceStore.getString("user_profile_name_custom_color_hex", "#FFFFFF")
    fun nicknameOutline() = preferenceStore.getBoolean("user_profile_name_outline", false)
    fun nicknameOutlineWidth() = preferenceStore.getInt("user_profile_name_outline_width", 2)
    fun nicknameGlow() = preferenceStore.getBoolean("user_profile_name_glow", false)
    fun nicknameEffect() = preferenceStore.getString("user_profile_name_effect", "none")

    fun showHomeGreeting() = preferenceStore.getBoolean("user_profile_show_home_greeting", true)
    fun showHomeStreak() = preferenceStore.getBoolean("user_profile_show_home_streak", true)
    fun homeHeaderGreetingAlignRight() = preferenceStore.getBoolean(
        "user_profile_home_header_greeting_align_right",
        false,
    )
    fun homeHeaderNicknameAlignRight() = preferenceStore.getBoolean(
        "user_profile_home_header_nickname_align_right",
        false,
    )
    fun homeHubLastSection() = preferenceStore.getString("user_profile_home_hub_last_section", "anime")
    fun greetingFont() = preferenceStore.getString("user_profile_greeting_font", "default")
    fun greetingFontSize() = preferenceStore.getInt("user_profile_greeting_font_size", 12)
    fun greetingColor() = preferenceStore.getString("user_profile_greeting_color", "accent")
    fun greetingCustomColorHex() = preferenceStore.getString("user_profile_greeting_custom_color_hex", "#FFFFFF")
    fun greetingDecoration() = preferenceStore.getString("user_profile_greeting_decoration", "sparkle")
    fun greetingItalic() = preferenceStore.getBoolean("user_profile_greeting_italic", true)
    fun greetingAlpha() = preferenceStore.getInt("user_profile_greeting_alpha", 60)
    fun homeHeaderLayoutJson() = preferenceStore.getString("user_profile_home_header_layout_json", "")

    fun getHomeHeaderLayoutOrNull(): HomeHeaderLayoutSpec? {
        return HomeHeaderLayoutSpec.fromJsonOrNull(homeHeaderLayoutJson().get())
    }

    fun getHomeHeaderLayoutOrDefault(): HomeHeaderLayoutSpec {
        return getHomeHeaderLayoutOrNull() ?: HomeHeaderLayoutSpec.default()
    }

    fun setHomeHeaderLayout(layout: HomeHeaderLayoutSpec) {
        homeHeaderLayoutJson().set(layout.toJson())
    }

    fun migrateGreetingDefaultsV026IfNeeded() {
        val migration = preferenceStore.getBoolean("user_profile_greeting_defaults_v026_migrated", false)
        if (migration.get()) return

        val isLegacyDefaultGreetingStyle =
            greetingFont().get() == "default" &&
                greetingFontSize().get() == 12 &&
                greetingColor().get() == "theme" &&
                greetingCustomColorHex().get() == "#FFFFFF" &&
                greetingDecoration().get() == "none" &&
                !greetingItalic().get()

        if (isLegacyDefaultGreetingStyle) {
            greetingColor().set("accent")
            greetingDecoration().set("sparkle")
            greetingItalic().set(true)
        }

        migration.set(true)
    }

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

    fun getGreetingIdsShownWithin(windowMs: Long, nowMillis: Long): Set<String> {
        if (windowMs <= 0L) return emptySet()
        if (nowMillis <= 0L) return emptySet()

        return decodeGreetingEvents(recentGreetingEvents().get())
            .asSequence()
            .filter { event ->
                event.shownAtMillis in 1..nowMillis &&
                    nowMillis - event.shownAtMillis < windowMs
            }
            .map { it.id }
            .toSet()
    }

    fun appendRecentGreetingEvent(
        id: String,
        shownAtMillis: Long,
        limit: Int = GREETING_EVENT_LIMIT,
    ) {
        if (id.isBlank()) return
        if (shownAtMillis <= 0L) return

        val byId = decodeGreetingEvents(recentGreetingEvents().get())
            .associateBy { it.id }
            .toMutableMap()
        byId[id] = GreetingEvent(id = id, shownAtMillis = shownAtMillis)

        val compact = byId.values
            .sortedByDescending { it.shownAtMillis }
            .take(limit.coerceAtLeast(1))
        recentGreetingEvents().set(encodeGreetingEvents(compact))
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

    private fun decodeGreetingEvents(raw: String): List<GreetingEvent> {
        if (raw.isBlank()) return emptyList()

        return raw.split(HISTORY_SEPARATOR)
            .asSequence()
            .map { token -> token.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                val separatorIndex = token.lastIndexOf(GREETING_EVENT_SEPARATOR)
                if (separatorIndex <= 0 || separatorIndex >= token.lastIndex) return@mapNotNull null
                val id = token.substring(0, separatorIndex).trim()
                val shownAt = token.substring(separatorIndex + 1).trim().toLongOrNull() ?: return@mapNotNull null
                if (id.isBlank() || shownAt <= 0L) return@mapNotNull null
                GreetingEvent(id = id, shownAtMillis = shownAt)
            }
            .toList()
    }

    private fun encodeGreetingEvents(events: Collection<GreetingEvent>): String {
        if (events.isEmpty()) return ""
        return events.joinToString(HISTORY_SEPARATOR) { event ->
            "${event.id}${GREETING_EVENT_SEPARATOR}${event.shownAtMillis}"
        }
    }

    private data class GreetingEvent(
        val id: String,
        val shownAtMillis: Long,
    )

    companion object {
        private const val HISTORY_SEPARATOR = "|"
        private const val GREETING_EVENT_SEPARATOR = "@"
        private const val RECENT_HISTORY_LIMIT = 3
        private const val GREETING_EVENT_LIMIT = 200
        const val DEFAULT_NAME = "\u0417\u0440\u0438\u0442\u0435\u043b\u044c"
    }
}
