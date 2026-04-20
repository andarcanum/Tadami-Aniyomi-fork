package mihon.core.migration.migrations

import eu.kanade.domain.ui.model.EInkProfile
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class EInkProfileMigration : Migration {
    override val version = 137f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        migrateEInkProfile(
            eInkProfilePref = preferenceStore.getEnum("e_ink_profile", EInkProfile.OFF),
            legacyEInkModePref = preferenceStore.getBoolean("e_ink_mode", false),
        )
        return true
    }
}

internal fun migrateEInkProfile(
    eInkProfilePref: Preference<EInkProfile>,
    legacyEInkModePref: Preference<Boolean>,
) {
    val hadLegacyEInkMode = legacyEInkModePref.get()
    legacyEInkModePref.delete()
    if (eInkProfilePref.isSet()) return

    eInkProfilePref.set(
        if (hadLegacyEInkMode) {
            EInkProfile.MONOCHROME
        } else {
            EInkProfile.OFF
        },
    )
}
