package eu.kanade.tachiyomi.data.sync

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Manager class for handling synchronization tasks between local database and sync services.
 */
class SyncManager(
    private val context: Context,
    private val syncPreferences: SyncPreferences = Injekt.get(),
) {
    private val notifier: SyncNotifier = SyncNotifier(context)
    private val protoBuf: ProtoBuf = Injekt.get()
    private val backupCreator: BackupCreator = BackupCreator(context, isAutoBackup = false)
    private val backupDecoder: BackupDecoder = BackupDecoder(context)
    private val backupRestorer: BackupRestorer = BackupRestorer(
        context = context,
        notifier = BackupNotifier(context),
        isSync = true,
    )

    enum class SyncService(val value: Int) {
        NONE(0),
        GOOGLE_DRIVE(1),
        ;

        companion object {
            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
        }
    }

    /**
     * Syncs data with the configured sync service.
     */
    suspend fun syncData() {
        logcat { "Starting sync" }

        // Handle sync based on the selected service
        val syncService = when (SyncService.fromInt(syncPreferences.syncService().get())) {
            SyncService.GOOGLE_DRIVE -> {
                GoogleDriveSyncService(context)
            }
            SyncService.NONE -> {
                logcat { "No sync service configured" }
                notifier.showSyncError("No sync service configured")
                return
            }
        }

        try {
            val localBackup = createLocalBackup()
            if (localBackup == null) {
                notifier.showSyncError("Failed to create local backup for sync")
                return
            }

            val syncData = SyncData(
                deviceId = syncPreferences.uniqueDeviceID(),
                backup = localBackup,
            )

            val syncedBackup = syncService.doSync(syncData)

            if (syncedBackup == null) {
                logcat { "Sync failed - skipping restore" }
                notifier.showSyncError("Sync failed. Check your connection and try again.")
                return
            }

            if (syncedBackup != localBackup) {
                restoreMergedBackup(syncedBackup)
            }

            logcat { "Sync completed successfully" }

            // Update sync timestamp
            syncPreferences.lastSyncTimestamp().set(Date().time)
            notifier.showSyncSuccess("Sync completed successfully")
        } catch (e: Exception) {
            this.logcat(LogPriority.ERROR, e) { "Sync error" }
            notifier.showSyncError("Sync error: ${e.message}")
        }
    }

    /**
     * Gets the last sync timestamp.
     */
    fun getLastSyncTimestamp(): Long {
        return syncPreferences.lastSyncTimestamp().get()
    }

    /**
     * Checks if sync is enabled.
     */
    fun isSyncEnabled(): Boolean {
        return syncPreferences.isSyncEnabled()
    }

    /**
     * Gets the current sync service.
     */
    fun getCurrentSyncService(): SyncService {
        return SyncService.fromInt(syncPreferences.syncService().get())
    }

    private suspend fun createLocalBackup(): Backup? {
        val backupFile = context.createFileInCacheDir("cloud_sync_local.tachibk")
        return try {
            backupCreator.backup(backupFile.toUri(), BackupOptions())
            backupDecoder.decode(backupFile.toUri())
        } catch (e: Exception) {
            this.logcat(LogPriority.ERROR, e) { "Failed to create local backup for sync" }
            null
        } finally {
            backupFile.delete()
        }
    }

    private suspend fun restoreMergedBackup(backup: Backup) {
        val backupFile = context.createFileInCacheDir("cloud_sync_merged.tachibk")
        try {
            val backupBytes = protoBuf.encodeToByteArray(Backup.serializer(), backup)
            backupFile.sink().gzip().buffer().use { sink ->
                sink.write(backupBytes)
            }
            backupRestorer.restore(backupFile.toUri(), RestoreOptions())
        } finally {
            backupFile.delete()
        }
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
