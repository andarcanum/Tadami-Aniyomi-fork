package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Service class for synchronizing backup data with Google Drive.
 * Handles push and pull operations for the app's sync data.
 */
class GoogleDriveSyncService(context: Context) : SyncService(
    context,
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
    Injekt.get<SyncPreferences>(),
) {

    private val googleDriveService: GoogleDriveService = Injekt.get()

    private val protoBuf: ProtoBuf = Injekt.get()

    enum class DeleteSyncDataStatus {
        NOT_INITIALIZED,
        NO_FILES,
        SUCCESS,
        ERROR,
    }

    private val remoteFileName = "${context.packageName}_sync.proto.gz"

    init {
        logcat { "GoogleDriveSyncService initialized" }
    }

    override suspend fun doSync(syncData: SyncData): Backup? {
        logcat { "Starting sync operation" }

        // Try to refresh first, but keep going with the current token if refresh fails.
        try {
            googleDriveService.refreshToken()
        } catch (e: Exception) {
            this.logcat(LogPriority.ERROR, e) {
                "Failed to refresh token before sync, continuing with existing credentials"
            }
            if (googleDriveService.driveService == null) {
                return null
            }
        }

        try {
            // Try to get remote data
            val remoteSyncData = pullSyncData()

            if (remoteSyncData != null) {
                // Get local unique device ID
                val localDeviceId = syncPreferences.uniqueDeviceID()
                val lastSyncDeviceId = remoteSyncData.deviceId

                logcat { "Local device: $localDeviceId, Last sync device: $lastSyncDeviceId" }

                // If the last sync was done by the same device, overwrite with local data
                return if (lastSyncDeviceId == localDeviceId) {
                    logcat { "Same device, pushing local data" }
                    pushSyncData(syncData)
                    syncData.backup
                } else {
                    // Merge the local and remote sync data
                    logcat { "Different device, merging data" }
                    val mergedSyncData = mergeSyncData(syncData, remoteSyncData)
                    pushSyncData(mergedSyncData)
                    mergedSyncData.backup
                }
            }

            // No remote data, just push local data
            logcat { "No remote data, pushing local data" }
            pushSyncData(syncData)
            return syncData.backup
        } catch (e: Exception) {
            this.logcat(LogPriority.ERROR, e) { "Error during sync" }
            throw e
        }
    }

    /**
     * Pulls sync data from Google Drive.
     */
    private fun pullSyncData(): SyncData? {
        val drive = googleDriveService.driveService
            ?: throw Exception("Not signed in to Google Drive")

        val fileList = getAppDataFileList(drive)

        if (fileList.isEmpty()) {
            logcat { "No files found in app data folder" }
            return null
        }

        val gdriveFileId = fileList[0].id
        logcat { "Found sync file with ID: $gdriveFileId" }

        try {
            drive.files().get(gdriveFileId).executeMediaAsInputStream().use { inputStream ->
                GZIPInputStream(inputStream).use { gzipInputStream ->
                    val byteArray = gzipInputStream.readBytes()
                    val backup = protoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
                    val deviceId = fileList[0].appProperties["deviceId"] ?: ""
                    return SyncData(deviceId = deviceId, backup = backup)
                }
            }
        } catch (e: Exception) {
            if (isDriveApiDisabled(e)) {
                throw IllegalStateException(
                    "Google Drive API is disabled for this project. " +
                        "Enable Drive API in Google Cloud Console and try again.",
                    e,
                )
            }
            this.logcat(LogPriority.ERROR, e) { "Error downloading file from Google Drive" }
            throw Exception("Failed to download sync data: ${e.message}", e)
        }
    }

    /**
     * Pushes sync data to Google Drive.
     */
    private suspend fun pushSyncData(syncData: SyncData) {
        val drive = googleDriveService.driveService
            ?: throw Exception("Not signed in to Google Drive")

        val fileList = getAppDataFileList(drive)
        val backup = syncData.backup ?: return

        val byteArray = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (byteArray.isEmpty()) {
            throw IllegalStateException("Empty backup data")
        }

        withContext(Dispatchers.IO) {
            PipedOutputStream().use { pos ->
                PipedInputStream(pos).use { pis ->
                    launch {
                        GZIPOutputStream(pos).use { gzipOutputStream ->
                            gzipOutputStream.write(byteArray)
                        }
                    }

                    val mediaContent = InputStreamContent("application/octet-stream", pis)

                    if (fileList.isNotEmpty()) {
                        // Update existing file
                        val fileId = fileList[0].id
                        val fileMetadata = File().apply {
                            name = remoteFileName
                            mimeType = "application/x-gzip"
                            appProperties = mapOf("deviceId" to syncData.deviceId)
                        }
                        drive.files().update(fileId, fileMetadata, mediaContent).execute()
                        logcat { "Updated existing sync file with ID: $fileId" }
                    } else {
                        // Create new file
                        val fileMetadata = File().apply {
                            name = remoteFileName
                            mimeType = "application/x-gzip"
                            parents = listOf("appDataFolder")
                            appProperties = mapOf("deviceId" to syncData.deviceId)
                        }
                        val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute()
                        logcat { "Created new sync file with ID: ${uploadedFile.id}" }
                    }
                }
            }
        }
    }

    /**
     * Gets the list of sync files in the appData folder.
     */
    private fun getAppDataFileList(drive: Drive): MutableList<File> {
        return try {
            // Search for existing file by name in the appData folder
            val query = "mimeType='application/x-gzip' and name = '$remoteFileName'"
            val fileList = drive.files()
                .list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .setFields("files(id, name, createdTime, appProperties)")
                .execute()
                .files
            logcat { "AppData folder file list: $fileList" }
            fileList ?: mutableListOf()
        } catch (e: Exception) {
            if (isDriveApiDisabled(e)) {
                throw IllegalStateException(
                    "Google Drive API is disabled for this project. " +
                        "Enable Drive API in Google Cloud Console and try again.",
                    e,
                )
            }
            this.logcat(LogPriority.ERROR, e) { "Error getting file list from appData folder" }
            mutableListOf()
        }
    }

    /**
     * Deletes sync data from Google Drive.
     */
    suspend fun deleteSyncDataFromGoogleDrive(): DeleteSyncDataStatus {
        val drive = googleDriveService.driveService

        if (drive == null) {
            logcat { "Google Drive service not initialized" }
            return DeleteSyncDataStatus.NOT_INITIALIZED
        }

        // Refresh token before deleting
        try {
            googleDriveService.refreshToken()
        } catch (e: Exception) {
            this.logcat(LogPriority.ERROR, e) { "Failed to refresh token before delete" }
            return DeleteSyncDataStatus.NOT_INITIALIZED
        }

        return withContext(Dispatchers.IO) {
            try {
                val appDataFileList = getAppDataFileList(drive)

                if (appDataFileList.isEmpty()) {
                    logcat { "No sync data file found in appData folder" }
                    DeleteSyncDataStatus.NO_FILES
                } else {
                    for (file in appDataFileList) {
                        drive.files().delete(file.id).execute()
                        logcat { "Deleted sync data file with ID: ${file.id}" }
                    }
                    DeleteSyncDataStatus.SUCCESS
                }
            } catch (e: Exception) {
                this.logcat(LogPriority.ERROR, e) { "Error occurred while interacting with Google Drive" }
                DeleteSyncDataStatus.ERROR
            }
        }
    }

    companion object {
        private const val TAG = "GoogleDriveSyncService"
    }

    private fun isDriveApiDisabled(exception: Exception): Boolean {
        val googleException = exception as? GoogleJsonResponseException ?: return false
        val reason = googleException.details?.errors?.firstOrNull()?.reason
        return googleException.statusCode == 403 &&
            (reason == "SERVICE_DISABLED" || reason == "accessNotConfigured")
    }
}
