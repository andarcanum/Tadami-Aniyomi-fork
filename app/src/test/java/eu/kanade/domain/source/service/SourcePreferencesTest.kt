package eu.kanade.domain.source.service

import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcePreferencesTest {

    @Test
    fun `migration strategy reuses prioritize by chapters preference`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_prioritize_by_chapters",
                    data = true,
                    defaultValue = false,
                ),
            ),
        )
        val preferences = SourcePreferences(store)

        assertEquals(SourcePreferences.MigrationStrategy.MOST_CHAPTERS, preferences.migrationStrategy())
    }

    @Test
    fun `migration defaults favor the new wizard flow`() {
        val preferences = SourcePreferences(InMemoryPreferenceStore())

        assertTrue(preferences.migrationSearchKeywords().get())
        assertTrue(preferences.migrationExtraSearchParam().get())
        assertFalse(preferences.migrationSkipNextTime().get())
        assertFalse(preferences.migrationHideNotFound().get())
        assertFalse(preferences.migrationOnlyNewChapters().get())
    }

    @Test
    fun `migration preferences read legacy keys when new keys are unset`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_deep_search_mode",
                    data = false,
                    defaultValue = false,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_hide_unmatched",
                    data = true,
                    defaultValue = false,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_hide_without_updates",
                    data = true,
                    defaultValue = false,
                ),
            ),
        )
        val preferences = SourcePreferences(store)

        assertFalse(preferences.migrationSearchKeywords().get())
        assertTrue(preferences.migrationHideNotFound().get())
        assertTrue(preferences.migrationOnlyNewChapters().get())
    }

    @Test
    fun `migration preferences prefer new keys over legacy keys`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_search_keywords",
                    data = true,
                    defaultValue = true,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_hide_not_found",
                    data = false,
                    defaultValue = false,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_only_new_chapters",
                    data = false,
                    defaultValue = false,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_deep_search_mode",
                    data = false,
                    defaultValue = false,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_hide_unmatched",
                    data = true,
                    defaultValue = false,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "migration_hide_without_updates",
                    data = true,
                    defaultValue = false,
                ),
            ),
        )
        val preferences = SourcePreferences(store)

        assertTrue(preferences.migrationSearchKeywords().get())
        assertFalse(preferences.migrationHideNotFound().get())
        assertFalse(preferences.migrationOnlyNewChapters().get())
    }
}
