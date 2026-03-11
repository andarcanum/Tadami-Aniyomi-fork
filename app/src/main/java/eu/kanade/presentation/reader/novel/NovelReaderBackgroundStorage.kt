package eu.kanade.presentation.reader.novel

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale
import java.util.UUID

private const val READER_BACKGROUND_DIR_NAME = "reader_backgrounds"
private const val READER_BACKGROUND_FILE_NAME_PREFIX = "custom_background"

data class NovelReaderCustomBackgroundItem(
    val id: String,
    val displayName: String,
    val fileName: String,
    val absolutePath: String,
    val isDarkHint: Boolean?,
    val createdAt: Long,
    val updatedAt: Long,
)

fun importNovelReaderCustomBackground(
    context: Context,
    uri: Uri,
): Result<String> {
    return importNovelReaderCustomBackgroundItem(context, uri).map { it.absolutePath }
}

fun importNovelReaderCustomBackgroundItem(
    context: Context,
    uri: Uri,
    displayName: String? = null,
    preferredId: String? = null,
): Result<NovelReaderCustomBackgroundItem> {
    return runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open selected image")
        val mimeType = context.contentResolver.getType(uri)
        val extension = resolveReaderBackgroundExtension(mimeType, uri)
        val targetDir = readerBackgroundDirectory(context)
        val fileName = buildReaderBackgroundFileName(extension)
        val targetFile = File(targetDir, fileName)
        input.use { source ->
            targetFile.outputStream().use { sink ->
                source.copyTo(sink)
            }
        }
        val storage = readerBackgroundCatalogStorage(context)
        val resolvedName = resolveReaderBackgroundDisplayName(
            uri = uri,
            requestedDisplayName = displayName,
            fallbackIndex = storage.read().items.size + 1,
        )
        val added = runCatching {
            storage.add(
                fileName = fileName,
                displayName = resolvedName,
                isDarkHint = null,
                id = preferredId,
            )
        }.getOrElse { error ->
            targetFile.delete()
            throw error
        }
        added.toResolved(targetFile)
    }
}

fun clearNovelReaderCustomBackground(path: String?): Result<Unit> {
    return runCatching {
        if (path.isNullOrBlank()) return@runCatching Unit
        val file = File(path)
        if (file.exists() && file.isFile) {
            file.delete()
        }
    }
}

fun readNovelReaderCustomBackgroundItems(context: Context): List<NovelReaderCustomBackgroundItem> {
    val storage = readerBackgroundCatalogStorage(context)
    val directory = readerBackgroundDirectory(context)
    val manifest = storage.read()
    val validItems = mutableListOf<ReaderBackgroundCatalogItem>()
    val resolvedItems = mutableListOf<NovelReaderCustomBackgroundItem>()

    manifest.items.forEach { item ->
        val file = File(directory, item.fileName)
        if (file.exists() && file.isFile) {
            validItems += item
            resolvedItems += item.toResolved(file)
        }
    }

    if (validItems.size != manifest.items.size) {
        storage.write(manifest.copy(items = validItems))
    }

    return resolvedItems
}

fun findNovelReaderCustomBackgroundItem(
    context: Context,
    id: String,
): NovelReaderCustomBackgroundItem? {
    return readNovelReaderCustomBackgroundItems(context).firstOrNull { it.id == id }
}

fun resolveNovelReaderCustomBackgroundPath(
    context: Context,
    id: String,
): String? {
    return findNovelReaderCustomBackgroundItem(context, id)?.absolutePath
}

fun renameNovelReaderCustomBackgroundItem(
    context: Context,
    id: String,
    displayName: String,
): Result<NovelReaderCustomBackgroundItem> {
    return runCatching {
        val cleanedName = displayName.trim()
        require(cleanedName.isNotEmpty()) { "Background name cannot be blank" }
        val storage = readerBackgroundCatalogStorage(context)
        val renamed = storage.rename(id = id, displayName = cleanedName)
            ?: error("Background not found: $id")
        val file = File(readerBackgroundDirectory(context), renamed.fileName)
        require(file.exists() && file.isFile) { "Background file missing: ${renamed.fileName}" }
        renamed.toResolved(file)
    }
}

fun replaceNovelReaderCustomBackgroundItem(
    context: Context,
    id: String,
    uri: Uri,
): Result<NovelReaderCustomBackgroundItem> {
    return runCatching {
        val storage = readerBackgroundCatalogStorage(context)
        val directory = readerBackgroundDirectory(context)
        val existing = storage.findById(id) ?: error("Background not found: $id")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open selected image")
        val mimeType = context.contentResolver.getType(uri)
        val extension = resolveReaderBackgroundExtension(mimeType, uri)
        val fileName = buildReaderBackgroundFileName(extension)
        val targetFile = File(directory, fileName)
        input.use { source ->
            targetFile.outputStream().use { sink ->
                source.copyTo(sink)
            }
        }
        val updated = runCatching {
            storage.updateFile(
                id = id,
                fileName = fileName,
                isDarkHint = null,
            )
        }.getOrElse { error ->
            targetFile.delete()
            throw error
        } ?: error("Background not found after replace: $id")
        val previousFile = File(directory, existing.fileName)
        if (previousFile.exists() && previousFile.isFile) {
            previousFile.delete()
        }
        updated.toResolved(targetFile)
    }
}

fun removeNovelReaderCustomBackgroundItem(
    context: Context,
    id: String,
): Result<Boolean> {
    return runCatching {
        val storage = readerBackgroundCatalogStorage(context)
        val existing = storage.findById(id) ?: return@runCatching false
        val removed = storage.remove(id)
        if (!removed) return@runCatching false
        val file = File(readerBackgroundDirectory(context), existing.fileName)
        if (file.exists() && file.isFile) {
            file.delete()
        }
        true
    }
}

fun ensureLegacyNovelReaderBackgroundItem(
    context: Context,
    legacyPath: String,
    preferredId: String = legacyPath,
): Result<NovelReaderCustomBackgroundItem?> {
    return runCatching {
        val normalizedPath = legacyPath.trim()
        if (normalizedPath.isBlank()) return@runCatching null
        val legacyFile = File(normalizedPath)
        if (!legacyFile.exists() || !legacyFile.isFile) return@runCatching null

        val storage = readerBackgroundCatalogStorage(context)
        val directory = readerBackgroundDirectory(context)
        val existing = storage.findById(preferredId)
        if (existing != null) {
            val existingFile = File(directory, existing.fileName)
            if (existingFile.exists() && existingFile.isFile) {
                return@runCatching existing.toResolved(existingFile)
            }
        }

        val extension = legacyFile.extension
            .lowercase(Locale.US)
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
            ?: "jpg"
        val fileName = buildReaderBackgroundFileName(extension)
        val targetFile = File(directory, fileName)
        legacyFile.inputStream().use { source ->
            targetFile.outputStream().use { sink ->
                source.copyTo(sink)
            }
        }
        val added = runCatching {
            storage.add(
                fileName = fileName,
                displayName = legacyFile.nameWithoutExtension.ifBlank { "Custom background" },
                isDarkHint = null,
                id = preferredId,
            )
        }.getOrElse { error ->
            targetFile.delete()
            throw error
        }
        added.toResolved(targetFile)
    }
}

private fun resolveReaderBackgroundExtension(
    mimeType: String?,
    uri: Uri,
): String {
    val normalizedMime = mimeType.orEmpty().lowercase(Locale.US)
    return when {
        normalizedMime.contains("png") -> "png"
        normalizedMime.contains("webp") -> "webp"
        normalizedMime.contains("jpeg") || normalizedMime.contains("jpg") -> "jpg"
        else ->
            uri.lastPathSegment
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase(Locale.US)
                ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
                ?: "jpg"
    }
}

private fun readerBackgroundDirectory(context: Context): File {
    return File(context.filesDir, READER_BACKGROUND_DIR_NAME).also { it.mkdirs() }
}

private fun readerBackgroundCatalogStorage(context: Context): FileBackedReaderBackgroundCatalogStorage {
    return FileBackedReaderBackgroundCatalogStorage(readerBackgroundDirectory(context))
}

private fun buildReaderBackgroundFileName(extension: String): String {
    val normalized = extension.lowercase(Locale.US).replace("jpeg", "jpg")
    val safeExtension = normalized.takeIf { it in setOf("jpg", "png", "webp") } ?: "jpg"
    val suffix = UUID.randomUUID().toString().replace("-", "")
    return "${READER_BACKGROUND_FILE_NAME_PREFIX}_$suffix.$safeExtension"
}

private fun resolveReaderBackgroundDisplayName(
    uri: Uri,
    requestedDisplayName: String?,
    fallbackIndex: Int,
): String {
    val direct = requestedDisplayName?.trim().orEmpty()
    if (direct.isNotEmpty()) return direct
    val fromUri = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.', missingDelimiterValue = "")
        ?.trim()
        .orEmpty()
    if (fromUri.isNotEmpty()) return fromUri
    return "Custom background $fallbackIndex"
}

private fun ReaderBackgroundCatalogItem.toResolved(file: File): NovelReaderCustomBackgroundItem {
    return NovelReaderCustomBackgroundItem(
        id = id,
        displayName = displayName,
        fileName = fileName,
        absolutePath = file.absolutePath,
        isDarkHint = isDarkHint,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
