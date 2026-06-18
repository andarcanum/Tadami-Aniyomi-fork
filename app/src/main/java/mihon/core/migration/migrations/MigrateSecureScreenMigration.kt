package mihon.core.migration.migrations

import android.app.Application
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class MigrateSecureScreenMigration : Migration {
    override val version = 75f

    // Allow disabling secure screen when incognito mode is on
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val securityPreferences = migrationContext.get<SecurityPreferences>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val oldSecureScreen = prefs.getBoolean("secure_screen", false)
        if (oldSecureScreen) {
            securityPreferences.secureScreen().set(
                SecurityPreferences.SecureScreenMode.ALWAYS,
            )
        }
        return true
    }
}
