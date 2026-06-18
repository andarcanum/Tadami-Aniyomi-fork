package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
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

    private data class RemoteSyncData(
        val file: File,
        val syncData: SyncData,
        val syncTimestamp: Long,
        val contentHash: String,
    )

    private class RemoteChangedException : Exception("Remote sync data changed while syncing")

    private val remoteFileName = "${context.packageName}_sync.proto.gz"

    init {
        logcat { "GoogleDriveSyncService initialized" }
    }

    override suspend fun doSync(syncData: SyncData): Backup? {
        logcat { "Starting sync operation" }

        try {
            googleDriveService.refreshToken()
        } catch (e: Exception) {
            this.logcat(LogPriority.ERROR, e) { "Failed to refresh token before sync" }
            return null
        }

        return withContext(Dispatchers.IO) {
            val localDeviceId = syncPreferences.uniqueDeviceID()
            var lastError: Exception? = null

            repeat(MAX_SYNC_RETRIES) { attempt ->
                try {
                    val remoteSyncData = pullRemoteSyncData()
                    val dataToPush = if (remoteSyncData != null) {
                        val lastSyncDeviceId = remoteSyncData.syncData.deviceId
                        logcat { "Local device: $localDeviceId, Last sync device: $lastSyncDeviceId" }

                        if (lastSyncDeviceId == localDeviceId) {
                            logcat { "Same device, pushing local data" }
                            syncData
                        } else {
                            logcat { "Different device, merging data" }
                            mergeSyncData(syncData, remoteSyncData.syncData)
                        }
                    } else {
                        logcat { "No remote data, pushing local data" }
                        syncData
                    }

                    pushSyncData(dataToPush, remoteSyncData)
                    return@withContext dataToPush.backup
                } catch (e: RemoteChangedException) {
                    lastError = e
                    logcat { "Remote changed during sync; retrying (${attempt + 1}/$MAX_SYNC_RETRIES)" }
                }
            }

            throw lastError ?: RemoteChangedException()
        }
    }

    /**
     * Pulls sync data from Google Drive.
     */
    private fun pullRemoteSyncData(): RemoteSyncData? {
        val drive = googleDriveService.driveService
            ?: throw Exception("Not signed in to Google Drive")

        val fileList = getAppDataFileList(drive)

        if (fileList.isEmpty()) {
            logcat { "No files found in app data folder" }
            return null
        }

        if (fileList.size > 1) {
            logcat { "Found ${fileList.size} sync files; keeping the newest and deleting stale duplicates" }
            deleteDuplicateSyncFiles(drive, fileList.drop(1))
        }

        val gdriveFile = fileList.first()
        val gdriveFileId = gdriveFile.id
        logcat { "Found sync file with ID: $gdriveFileId" }

        try {
            drive.files().get(gdriveFileId).executeMediaAsInputStream().use { inputStream ->
                GZIPInputStream(inputStream).use { gzipInputStream ->
                    val byteArray = gzipInputStream.readBytes()
                    val backup = protoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
                    val deviceId = gdriveFile.appProperties?.get(APP_PROPERTY_DEVICE_ID).orEmpty()
                    val syncTimestamp = gdriveFile.appProperties?.get(APP_PROPERTY_SYNC_TIMESTAMP)?.toLongOrNull() ?: 0L
                    val contentHash = gdriveFile.appProperties?.get(APP_PROPERTY_CONTENT_HASH).orEmpty()
                    return RemoteSyncData(
                        file = gdriveFile,
                        syncData = SyncData(deviceId = deviceId, backup = backup),
                        syncTimestamp = syncTimestamp,
                        contentHash = contentHash,
                    )
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
     * Pushes sync data to Google Drive with optimistic remote-change detection.
     */
    private fun pushSyncData(syncData: SyncData, expectedRemote: RemoteSyncData?) {
        val drive = googleDriveService.driveService
            ?: throw Exception("Not signed in to Google Drive")

        val backup = syncData.backup ?: return
        val backupBytes = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (backupBytes.isEmpty()) {
            throw IllegalStateException("Empty backup data")
        }

        val gzippedBackupBytes = gzip(backupBytes)
        val now = System.currentTimeMillis()
        val contentHash = sha256(backupBytes)
        val latestFiles = getAppDataFileList(drive)
        val latestFile = latestFiles.firstOrNull()

        if (expectedRemote == null) {
            if (latestFile != null) {
                throw RemoteChangedException()
            }
        } else {
            val matchingLatestFile = latestFiles.firstOrNull { it.id == expectedRemote.file.id }
                ?: throw RemoteChangedException()
            val latestTimestamp =
                matchingLatestFile.appProperties?.get(APP_PROPERTY_SYNC_TIMESTAMP)?.toLongOrNull() ?: 0L
            val latestHash = matchingLatestFile.appProperties?.get(APP_PROPERTY_CONTENT_HASH).orEmpty()
            if (latestTimestamp != expectedRemote.syncTimestamp || latestHash != expectedRemote.contentHash) {
                throw RemoteChangedException()
            }
        }

        val mediaContent = ByteArrayContent("application/x-gzip", gzippedBackupBytes)

        if (expectedRemote != null) {
            val fileId = expectedRemote.file.id
            val fileMetadata = buildFileMetadata(syncData.deviceId, now, contentHash)
            try {
                drive.files().update(fileId, fileMetadata, mediaContent)
                    .setFields(FILE_FIELDS)
                    .execute()
                logcat { "Updated existing sync file with ID: $fileId" }
            } catch (e: GoogleJsonResponseException) {
                val errors = e.details?.errors.orEmpty()
                val reasons = errors.joinToString { error ->
                    "domain=${error.domain}, reason=${error.reason}, message=${error.message}, location=${error.location}"
                }

                this.logcat(LogPriority.ERROR, e) {
                    "Google Drive update failed: " +
                        "fileId=$fileId, " +
                        "status=${e.statusCode}, " +
                        "statusMessage=${e.statusMessage}, " +
                        "detailsMessage=${e.details?.message}, " +
                        "errors=[$reasons]"
                }

                throw e
            }
        } else {
            val fileMetadata = buildFileMetadata(syncData.deviceId, now, contentHash).apply {
                parents = listOf("appDataFolder")
            }
            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields(FILE_FIELDS)
                .execute()
            logcat { "Created new sync file with ID: ${uploadedFile.id}" }
        }
    }

    private fun buildFileMetadata(deviceId: String, syncTimestamp: Long, contentHash: String): File {
        return File().apply {
            name = remoteFileName
            mimeType = "application/x-gzip"
            appProperties = mapOf(
                APP_PROPERTY_DEVICE_ID to deviceId,
                APP_PROPERTY_SYNC_TIMESTAMP to syncTimestamp.toString(),
                APP_PROPERTY_CONTENT_HASH to contentHash,
            )
        }
    }

    /**
     * Gets the list of sync files in the appData folder, newest first.
     */
    private fun getAppDataFileList(drive: Drive): List<File> {
        return try {
            val query = "trashed = false and mimeType = 'application/x-gzip' and name = '$remoteFileName'"
            val fileList = drive.files()
                .list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .setFields("files($FILE_FIELDS)")
                .setOrderBy("modifiedTime desc")
                .execute()
                .files
                .orEmpty()

            logcat { "AppData folder sync file count: ${fileList.size}" }
            fileList
        } catch (e: Exception) {
            if (isDriveApiDisabled(e)) {
                throw IllegalStateException(
                    "Google Drive API is disabled for this project. " +
                        "Enable Drive API in Google Cloud Console and try again.",
                    e,
                )
            }
            this.logcat(LogPriority.ERROR, e) { "Error getting file list from appData folder" }
            throw Exception("Failed to list Google Drive sync data: ${e.message}", e)
        }
    }

    private fun deleteDuplicateSyncFiles(drive: Drive, duplicateFiles: List<File>) {
        duplicateFiles.forEach { file ->
            try {
                drive.files().delete(file.id).execute()
                logcat { "Deleted stale duplicate sync file with ID: ${file.id}" }
            } catch (e: Exception) {
                this.logcat(LogPriority.WARN, e) { "Failed to delete stale duplicate sync file with ID: ${file.id}" }
            }
        }
    }

    /**
     * Deletes sync data from Google Drive.
     */
    suspend fun deleteSyncDataFromGoogleDrive(): DeleteSyncDataStatus {
        try {
            googleDriveService.refreshToken()
        } catch (e: Exception) {
            this.logcat(LogPriority.ERROR, e) { "Failed to refresh token before delete" }
            return DeleteSyncDataStatus.NOT_INITIALIZED
        }

        val drive = googleDriveService.driveService
        if (drive == null) {
            logcat { "Google Drive service not initialized" }
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

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzipOutputStream ->
            gzipOutputStream.write(bytes)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "GoogleDriveSyncService"
        private const val MAX_SYNC_RETRIES = 3
        private const val APP_PROPERTY_DEVICE_ID = "deviceId"
        private const val APP_PROPERTY_SYNC_TIMESTAMP = "syncTimestamp"
        private const val APP_PROPERTY_CONTENT_HASH = "contentHash"
        private const val FILE_FIELDS = "id, name, createdTime, modifiedTime, appProperties"
    }

    private fun isDriveApiDisabled(exception: Exception): Boolean {
        val googleException = exception as? GoogleJsonResponseException ?: return false
        val reason = googleException.details?.errors?.firstOrNull()?.reason
        return googleException.statusCode == 403 &&
            (reason == "SERVICE_DISABLED" || reason == "accessNotConfigured")
    }
}
