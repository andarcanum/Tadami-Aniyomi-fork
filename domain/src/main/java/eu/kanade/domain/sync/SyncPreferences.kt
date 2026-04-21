package eu.kanade.domain.sync

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

/**
 * Preferences class for managing sync-related settings including
 * Google Drive authentication tokens and sync configuration.
 */
class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {
    companion object {
        const val SYNC_SERVICE_NONE = 0
        const val SYNC_SERVICE_GOOGLE_DRIVE = 1
    }

    /**
     * The access token for Google Drive API authentication.
     */
    fun googleDriveAccessToken(): Preference<String> = preferenceStore.getString(
        key = Preference.appStateKey("google_drive_access_token"),
        defaultValue = "",
    )

    /**
     * The refresh token for Google Drive API authentication.
     */
    fun googleDriveRefreshToken(): Preference<String> = preferenceStore.getString(
        key = Preference.appStateKey("google_drive_refresh_token"),
        defaultValue = "",
    )

    /**
     * The last sync timestamp.
     */
    fun lastSyncTimestamp(): Preference<Long> = preferenceStore.getLong(
        key = Preference.appStateKey("last_sync_timestamp"),
        defaultValue = 0L,
    )

    /**
     * Whether cloud sync is enabled.
     */
    fun cloudSyncEnabled(): Preference<Boolean> = preferenceStore.getBoolean(
        key = Preference.appStateKey("cloud_sync_enabled"),
        defaultValue = false,
    )

    /**
     * The sync interval in hours (0 means disabled).
     */
    fun syncInterval(): Preference<Int> = preferenceStore.getInt(
        key = "sync_interval",
        defaultValue = 0,
    )

    /**
     * The selected sync service (0 = none, 1 = Google Drive).
     */
    fun syncService(): Preference<Int> = preferenceStore.getInt(
        key = "sync_service",
        defaultValue = SYNC_SERVICE_NONE,
    )

    /**
     * Gets or generates a unique device ID for sync operations.
     * This ID is used to identify which device performed the last sync.
     */
    fun uniqueDeviceID(): String {
        val uniqueIDPreference = preferenceStore.getString(
            key = Preference.appStateKey("unique_device_id"),
            defaultValue = "",
        )

        var uniqueID = uniqueIDPreference.get()
        if (uniqueID.isBlank()) {
            uniqueID = UUID.randomUUID().toString()
            uniqueIDPreference.set(uniqueID)
        }

        return uniqueID
    }

    /**
     * Checks if sync is enabled (i.e., a sync service is selected).
     */
    fun isSyncEnabled(): Boolean {
        return syncService().get() != SYNC_SERVICE_NONE
    }

    /**
     * Checks if user is signed in to Google Drive.
     */
    fun isGoogleDriveSignedIn(): Boolean {
        val accessToken = googleDriveAccessToken().get()
        val refreshToken = googleDriveRefreshToken().get()
        return accessToken.isNotBlank() && refreshToken.isNotBlank()
    }

    /**
     * Clears all Google Drive authentication tokens.
     */
    fun clearGoogleDriveTokens() {
        googleDriveAccessToken().set("")
        googleDriveRefreshToken().set("")
    }
}
