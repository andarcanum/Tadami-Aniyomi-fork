package eu.kanade.presentation.reader.novel

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale

private const val READER_BACKGROUND_DIR_NAME = "reader_backgrounds"
private const val READER_BACKGROUND_FILE_NAME_PREFIX = "custom_background"

fun importNovelReaderCustomBackground(
    context: Context,
    uri: Uri,
): Result<String> {
    return runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open selected image")
        val mimeType = context.contentResolver.getType(uri)
        val extension = resolveReaderBackgroundExtension(mimeType, uri)
        val targetDir = File(context.filesDir, READER_BACKGROUND_DIR_NAME).also { it.mkdirs() }
        targetDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(READER_BACKGROUND_FILE_NAME_PREFIX) }
            ?.forEach { it.delete() }
        val targetFile = File(targetDir, "$READER_BACKGROUND_FILE_NAME_PREFIX.$extension")
        input.use { source ->
            targetFile.outputStream().use { sink ->
                source.copyTo(sink)
            }
        }
        targetFile.absolutePath
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
