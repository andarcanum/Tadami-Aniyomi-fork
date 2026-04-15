package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.Serializable

/**
 * Data class representing the sync data that will be uploaded to/downloaded from
 * Google Drive or other sync services.
 *
 * @param deviceId The unique identifier of the device that created this sync data
 * @param backup The backup data containing manga, anime, novels, categories, etc.
 */
@Serializable
data class SyncData(
    val deviceId: String = "",
    val backup: Backup? = null,
)
