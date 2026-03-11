package eu.kanade.presentation.reader.novel

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

private const val READER_BACKGROUND_CATALOG_MANIFEST_FILE_NAME = "manifest.json"
private const val READER_BACKGROUND_CATALOG_BACKUP_FILE_NAME = "manifest.bak.json"

@Serializable
data class ReaderBackgroundCatalogManifest(
    val version: Int = 1,
    val items: List<ReaderBackgroundCatalogItem> = emptyList(),
)

@Serializable
data class ReaderBackgroundCatalogItem(
    val id: String,
    val displayName: String,
    val fileName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDarkHint: Boolean? = null,
)

interface ReaderBackgroundCatalogStorage {
    fun read(): ReaderBackgroundCatalogManifest
    fun write(manifest: ReaderBackgroundCatalogManifest)
    fun add(
        fileName: String,
        displayName: String,
        isDarkHint: Boolean?,
        id: String? = null,
    ): ReaderBackgroundCatalogItem

    fun rename(
        id: String,
        displayName: String,
    ): ReaderBackgroundCatalogItem?

    fun updateFile(
        id: String,
        fileName: String,
        isDarkHint: Boolean?,
    ): ReaderBackgroundCatalogItem?

    fun remove(id: String): Boolean
    fun findById(id: String): ReaderBackgroundCatalogItem?
}

class FileBackedReaderBackgroundCatalogStorage(
    private val directory: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ReaderBackgroundCatalogStorage {

    private val manifestFile: File
        get() = File(directory, READER_BACKGROUND_CATALOG_MANIFEST_FILE_NAME)

    private val backupFile: File
        get() = File(directory, READER_BACKGROUND_CATALOG_BACKUP_FILE_NAME)

    override fun read(): ReaderBackgroundCatalogManifest {
        directory.mkdirs()
        if (!manifestFile.exists()) return ReaderBackgroundCatalogManifest()
        val parsedManifest = runCatching {
            json.decodeFromString<ReaderBackgroundCatalogManifest>(manifestFile.readText())
        }.getOrNull()
        if (parsedManifest != null) return parsedManifest

        val parsedBackup = runCatching {
            json.decodeFromString<ReaderBackgroundCatalogManifest>(backupFile.readText())
        }.getOrNull()
        return parsedBackup ?: ReaderBackgroundCatalogManifest()
    }

    override fun write(manifest: ReaderBackgroundCatalogManifest) {
        directory.mkdirs()
        if (manifestFile.exists()) {
            runCatching { manifestFile.copyTo(backupFile, overwrite = true) }
        }
        val payload = json.encodeToString(manifest)
        val tempFile = File(directory, "${READER_BACKGROUND_CATALOG_MANIFEST_FILE_NAME}.tmp")
        tempFile.writeText(payload)
        if (!tempFile.renameTo(manifestFile)) {
            tempFile.copyTo(manifestFile, overwrite = true)
            tempFile.delete()
        }
    }

    override fun add(
        fileName: String,
        displayName: String,
        isDarkHint: Boolean?,
        id: String?,
    ): ReaderBackgroundCatalogItem {
        val now = nowProvider()
        val item = ReaderBackgroundCatalogItem(
            id = id?.takeIf { it.isNotBlank() } ?: idFactory(),
            displayName = displayName,
            fileName = fileName,
            createdAt = now,
            updatedAt = now,
            isDarkHint = isDarkHint,
        )
        val manifest = read()
        write(manifest.copy(items = manifest.items + item))
        return item
    }

    override fun rename(
        id: String,
        displayName: String,
    ): ReaderBackgroundCatalogItem? {
        val manifest = read()
        var renamed: ReaderBackgroundCatalogItem? = null
        val updatedItems = manifest.items.map { item ->
            if (item.id != id) {
                item
            } else {
                item.copy(
                    displayName = displayName,
                    updatedAt = nowProvider(),
                ).also { renamed = it }
            }
        }
        if (renamed == null) return null
        write(manifest.copy(items = updatedItems))
        return renamed
    }

    override fun updateFile(
        id: String,
        fileName: String,
        isDarkHint: Boolean?,
    ): ReaderBackgroundCatalogItem? {
        val manifest = read()
        var updated: ReaderBackgroundCatalogItem? = null
        val updatedItems = manifest.items.map { item ->
            if (item.id != id) {
                item
            } else {
                item.copy(
                    fileName = fileName,
                    updatedAt = nowProvider(),
                    isDarkHint = isDarkHint,
                ).also { updated = it }
            }
        }
        if (updated == null) return null
        write(manifest.copy(items = updatedItems))
        return updated
    }

    override fun remove(id: String): Boolean {
        val manifest = read()
        val updatedItems = manifest.items.filterNot { it.id == id }
        if (updatedItems.size == manifest.items.size) return false
        write(manifest.copy(items = updatedItems))
        return true
    }

    override fun findById(id: String): ReaderBackgroundCatalogItem? {
        return read().items.firstOrNull { it.id == id }
    }
}
