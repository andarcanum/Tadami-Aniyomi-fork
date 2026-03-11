package eu.kanade.presentation.reader.novel

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.Normalizer
import java.util.Locale

private const val READER_FONT_DIR_NAME = "reader_fonts"
private const val READER_LOCAL_FONT_ASSET_DIR = "local/fonts"
private const val READER_USER_FONT_ID_PREFIX = "user:"
private const val READER_LOCAL_FONT_ID_PREFIX = "local:"

fun importNovelReaderCustomFont(
    context: Context,
    uri: Uri,
): Result<NovelReaderFontOption> {
    return runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open selected font")
        val fileName = resolveReaderFontImportFileName(uri)
        require(isSupportedReaderFontFileName(fileName)) { "Unsupported font file" }

        val targetDir = File(context.filesDir, READER_FONT_DIR_NAME).also { it.mkdirs() }
        val uniqueFileName = buildUniqueReaderFontFileName(targetDir, fileName)
        val targetFile = File(targetDir, uniqueFileName)
        input.use { source ->
            targetFile.outputStream().use { sink ->
                source.copyTo(sink)
            }
        }
        createUserImportedReaderFont(targetFile)
    }
}

fun removeNovelReaderCustomFont(path: String?): Result<Unit> {
    return runCatching {
        if (path.isNullOrBlank()) return@runCatching Unit
        val file = File(path)
        if (file.exists() && file.isFile) {
            file.delete()
        }
    }
}

fun getNovelReaderUserFonts(context: Context): List<NovelReaderFontOption> {
    val directory = File(context.filesDir, READER_FONT_DIR_NAME)
    return getNovelReaderUserFonts(directory)
}

internal fun getNovelReaderUserFonts(directory: File): List<NovelReaderFontOption> {
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    return directory.listFiles()
        .orEmpty()
        .filter { it.isFile && isSupportedReaderFontFileName(it.name) }
        .sortedBy { it.name.lowercase(Locale.US) }
        .map { createUserImportedReaderFont(it) }
}

fun getNovelReaderLocalFonts(context: Context): List<NovelReaderFontOption> {
    val assetFiles = runCatching { context.assets.list(READER_LOCAL_FONT_ASSET_DIR).orEmpty() }
        .getOrDefault(emptyArray())
    return assetFiles
        .filter { isSupportedReaderFontFileName(it) }
        .sortedBy { it.lowercase(Locale.US) }
        .map { fileName ->
            NovelReaderFontOption(
                id = "$READER_LOCAL_FONT_ID_PREFIX$fileName",
                label = formatReaderFontLabel(fileName),
                assetFileName = fileName,
                assetPath = "$READER_LOCAL_FONT_ASSET_DIR/$fileName",
                source = NovelReaderFontSource.LOCAL_PRIVATE,
            )
        }
}

fun buildNovelReaderFontCatalog(context: Context): List<NovelReaderFontOption> {
    return buildList {
        addAll(novelReaderBuiltInFonts)
        addAll(getNovelReaderLocalFonts(context))
        addAll(getNovelReaderUserFonts(context))
    }
}

fun resolveNovelReaderSelectedFont(
    fonts: List<NovelReaderFontOption>,
    selectedFontId: String,
): NovelReaderFontOption {
    return fonts.firstOrNull { it.id == selectedFontId }
        ?: novelReaderBuiltInFonts.first()
}

internal fun isSupportedReaderFontFileName(fileName: String): Boolean {
    val normalized = fileName.lowercase(Locale.US)
    return normalized.endsWith(".ttf") || normalized.endsWith(".otf")
}

internal fun formatReaderFontLabel(fileName: String): String {
    val withoutExtension = fileName.substringBeforeLast('.')
    val normalized = Normalizer.normalize(withoutExtension, Normalizer.Form.NFKC)
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return withoutExtension
    return normalized.split(' ')
        .joinToString(" ") { token ->
            token.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }
}

private fun resolveReaderFontImportFileName(uri: Uri): String {
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.takeIf { it.isNotBlank() }
        ?: "custom-font.ttf"
}

private fun buildUniqueReaderFontFileName(
    directory: File,
    fileName: String,
): String {
    if (!File(directory, fileName).exists()) return fileName
    val baseName = fileName.substringBeforeLast('.', fileName)
    val extension = fileName.substringAfterLast('.', "")
    var index = 2
    while (true) {
        val candidate = if (extension.isBlank()) {
            "$baseName-$index"
        } else {
            "$baseName-$index.$extension"
        }
        if (!File(directory, candidate).exists()) {
            return candidate
        }
        index += 1
    }
}

private fun createUserImportedReaderFont(file: File): NovelReaderFontOption {
    return NovelReaderFontOption(
        id = "$READER_USER_FONT_ID_PREFIX${file.name}",
        label = formatReaderFontLabel(file.name),
        source = NovelReaderFontSource.USER_IMPORTED,
        filePath = file.absolutePath,
    )
}
