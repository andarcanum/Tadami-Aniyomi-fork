package eu.kanade.tachiyomi.data.discord

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class DiscordPreferences(private val preferenceStore: PreferenceStore) {

    fun enabled() = preferenceStore.getBoolean(
        Preference.privateKey("pref_discord_presence_enabled"),
        false,
    )

    fun token() = preferenceStore.getString(
        Preference.privateKey("pref_discord_presence_token"),
        "",
    )
}
