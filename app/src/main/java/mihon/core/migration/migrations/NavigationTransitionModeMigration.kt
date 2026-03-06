package mihon.core.migration.migrations

import eu.kanade.domain.ui.model.NavTransitionMode
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class NavigationTransitionModeMigration : Migration {
    override val version = 134f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val transitionModePref = preferenceStore.getEnum(
            "navigation_transition_mode",
            NavTransitionMode.MODERN,
        )

        if (!transitionModePref.isSet()) {
            transitionModePref.set(NavTransitionMode.LEGACY)
        }

        return true
    }
}
