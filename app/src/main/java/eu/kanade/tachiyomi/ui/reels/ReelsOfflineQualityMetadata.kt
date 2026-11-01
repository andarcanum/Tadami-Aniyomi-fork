package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** One app-local sidecar record; it contains no URL, token or account secret. */
@Serializable
data class ReelsStoredVideoQuality(
    val schemaVersion: Int,
    val sourceId: Long,
    val itemId: String,
    val fileName: String,
    val byteLength: Long,
    val lastModifiedMs: Long,
    val variant: FeedVideoQualityVariant,
)

/**
 * Sidecars live beside (not inside) the flat media directory:
 * `reels_offline/quality/<sourceId>/<sha256(itemId)>.json`.
 * Media files remain the authority; a missing/corrupt/mismatched record is "quality unknown".
 */
object ReelsOfflineQualityMetadata {

    const val SCHEMA_VERSION = 1
    private const val DIRECTORY = "quality"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun storedQuality(root: File, sourceId: Long, itemId: String): ReelsStoredVideoQuality? {
        val record = readRecord(root, sourceId, itemId) ?: return null
        if (record.schemaVersion != SCHEMA_VERSION) return null
        if (record.sourceId != sourceId || record.itemId != itemId) return null
        val media = File(root, record.fileName)
        if (!media.isFile) return null
        if (media.length() != record.byteLength) return null
        if (media.lastModified() != record.lastModifiedMs) return null
        return record
    }

    fun write(root: File, record: ReelsStoredVideoQuality): Boolean {
        val target = recordFile(root, record.sourceId, record.itemId)
        val temp = File(target.parentFile, "${target.name}.${System.nanoTime()}.tmp")
        return runCatching {
            target.parentFile?.mkdirs()
            temp.writeText(json.encodeToString(ReelsStoredVideoQuality.serializer(), record))
            if (!temp.renameTo(target)) {
                temp.delete()
                return@runCatching false
            }
            true
        }.getOrDefault(false)
    }

    fun delete(root: File, sourceId: Long, itemId: String): Boolean =
        recordFile(root, sourceId, itemId).delete()

    fun deleteSource(root: File, sourceId: Long): Boolean =
        sourceDirectory(root, sourceId).deleteRecursively()

    fun deleteAll(root: File): Boolean =
        File(root, DIRECTORY).deleteRecursively()

    fun usedBytes(root: File): Long =
        File(root, DIRECTORY).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    private fun readRecord(root: File, sourceId: Long, itemId: String): ReelsStoredVideoQuality? {
        val file = recordFile(root, sourceId, itemId)
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString(ReelsStoredVideoQuality.serializer(), file.readText())
        }.getOrNull()
    }

    private fun recordFile(root: File, sourceId: Long, itemId: String): File =
        File(sourceDirectory(root, sourceId), "${sha256(itemId)}.json")

    private fun sourceDirectory(root: File, sourceId: Long): File =
        File(File(root, DIRECTORY), sourceId.toString())

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
