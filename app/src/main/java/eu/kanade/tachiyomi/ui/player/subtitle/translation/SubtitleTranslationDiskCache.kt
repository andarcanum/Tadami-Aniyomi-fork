package eu.kanade.tachiyomi.ui.player.subtitle.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SubtitleTranslationDiskCache(
    private val rootDir: File,
) {
    suspend fun read(cacheKey: String): SubtitleDocument? = withContext(Dispatchers.IO) {
        val file = fileFor(cacheKey)
        if (!file.isFile) return@withContext null
        val raw = file.readText(Charsets.UTF_8)
        SubtitleParser.parse(raw, file.extension)
    }

    suspend fun write(cacheKey: String, document: SubtitleDocument): File = withContext(Dispatchers.IO) {
        if (!rootDir.exists()) rootDir.mkdirs()
        val file = fileFor(cacheKey, document.format.extension)
        file.writeText(SubtitleWriter.write(document), Charsets.UTF_8)
        file
    }

    suspend fun file(cacheKey: String, format: SubtitleFormat = SubtitleFormat.Vtt): File = withContext(
        Dispatchers.IO,
    ) {
        fileFor(cacheKey, format.extension)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        rootDir.deleteRecursively()
        rootDir.mkdirs()
    }

    private fun fileFor(cacheKey: String, extension: String? = null): File {
        if (!rootDir.exists()) rootDir.mkdirs()
        if (extension != null) return File(rootDir, "$cacheKey.$extension")
        return rootDir.listFiles()
            ?.firstOrNull { it.nameWithoutExtension == cacheKey }
            ?: File(rootDir, "$cacheKey.vtt")
    }
}
