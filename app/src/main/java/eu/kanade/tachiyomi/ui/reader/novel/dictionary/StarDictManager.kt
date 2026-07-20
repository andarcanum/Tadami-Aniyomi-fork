package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

data class InstalledStarDict(
    val id: String,
    val bookname: String,
    val wordCount: Int,
    val sizeBytes: Long,
)

/**
 * Manages user-imported StarDict dictionaries for the novel reader.
 *
 * Dictionaries are never bundled with the APK. The user imports either a `.zip` archive that
 * contains the StarDict files, or the individual `.ifo` / `.idx(.gz)` / `.dict(.dz)` / `.syn`
 * files. Imported dictionaries are stored under `filesDir/novel_dictionaries/<id>/` with all
 * gzip/dictzip payloads decompressed once at import time so lookups can use random access.
 */
object StarDictManager {

    private const val BASE_DIR_NAME = "novel_dictionaries"

    private val loadedCache = HashMap<String, StarDictDictionary>()

    /** Bumped whenever the set of installed dictionaries changes; used in cache fingerprints. */
    @Volatile
    var revision: Int = 0
        private set

    fun baseDir(context: Context): File = File(context.filesDir, BASE_DIR_NAME)

    fun listInstalled(context: Context): List<InstalledStarDict> {
        val dirs = baseDir(context).listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val ifo = dir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("ifo", true) }
                ?: return@mapNotNull null
            val info = StarDictDictionary.parseIfo(ifo) ?: return@mapNotNull null
            InstalledStarDict(
                id = dir.name,
                bookname = info.bookname,
                wordCount = info.wordcount,
                sizeBytes = dir.listFiles()?.sumOf { it.length() } ?: 0L,
            )
        }.sortedBy { it.bookname.lowercase() }
    }

    /**
     * Imports a StarDict dictionary from the given SAF [uris].
     * Accepts a single `.zip` archive or a group of raw StarDict files.
     */
    fun importFromUris(context: Context, uris: List<Uri>): Result<InstalledStarDict> = runCatching {
        require(uris.isNotEmpty()) { "No files selected" }
        val stagingDir = File(context.cacheDir, "stardict_import_${System.currentTimeMillis()}")
        stagingDir.mkdirs()
        try {
            uris.forEach { uri -> stageUri(context, uri, stagingDir) }
            decompressStagedFiles(stagingDir)

            val ifoFile = stagingDir.listFiles()
                ?.firstOrNull { it.isFile && it.extension.equals("ifo", true) }
                ?: error("No .ifo file found in the selected files")
            val info = StarDictDictionary.parseIfo(ifoFile) ?: error("Invalid .ifo file: ${ifoFile.name}")
            val base = ifoFile.name.removeSuffix(".ifo")
            check(File(stagingDir, "$base.idx").isFile) { "Missing .idx file for $base" }
            check(File(stagingDir, "$base.dict").isFile) { "Missing .dict file for $base" }

            // Sanity check: the dictionary must fully parse before it is installed.
            StarDictDictionary.load(id = "staging", dir = stagingDir)

            val id = buildDictionaryId(base, info)
            val targetDir = File(baseDir(context), id)
            if (targetDir.exists()) targetDir.deleteRecursively()
            check(targetDir.mkdirs()) { "Cannot create dictionary directory" }
            val keepExtensions = setOf("ifo", "idx", "dict", "syn")
            stagingDir.listFiles().orEmpty()
                .filter { it.isFile && it.extension.lowercase() in keepExtensions }
                .forEach { file -> file.copyTo(File(targetDir, file.name), overwrite = true) }

            synchronized(loadedCache) { loadedCache.clear() }
            revision += 1

            InstalledStarDict(
                id = id,
                bookname = info.bookname,
                wordCount = info.wordcount,
                sizeBytes = targetDir.listFiles()?.sumOf { it.length() } ?: 0L,
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    fun delete(context: Context, id: String): Boolean {
        val dir = File(baseDir(context), id)
        val deleted = dir.deleteRecursively()
        synchronized(loadedCache) { loadedCache.remove(id) }
        revision += 1
        return deleted
    }

    /** Loads all installed dictionaries that are not in [disabledIds]; results are cached. */
    fun loadEnabled(context: Context, disabledIds: Set<String>): List<StarDictDictionary> {
        val dirs = baseDir(context).listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs
            .filter { it.name !in disabledIds }
            .sortedBy { it.name.lowercase() }
            .mapNotNull { dir ->
                synchronized(loadedCache) { loadedCache[dir.name] }?.let { return@mapNotNull it }
                val dictionary = runCatching { StarDictDictionary.load(dir.name, dir) }.getOrNull()
                    ?: return@mapNotNull null
                synchronized(loadedCache) { loadedCache[dir.name] = dictionary }
                dictionary
            }
    }

    private fun stageUri(context: Context, uri: Uri, stagingDir: File) {
        val displayName = queryDisplayName(context, uri) ?: "import-${System.nanoTime()}"
        val safeName = File(displayName).name
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $displayName")
        input.use { stream ->
            if (safeName.lowercase().endsWith(".zip")) {
                extractZip(stream, stagingDir)
            } else {
                File(stagingDir, safeName).outputStream().use { output -> stream.copyTo(output) }
            }
        }
    }

    private fun extractZip(input: InputStream, stagingDir: File) {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // Flatten paths: only the file name matters for the StarDict layout,
                    // and this also protects against zip path traversal.
                    val name = File(entry.name).name
                    if (name.isNotEmpty() && !name.startsWith(".")) {
                        File(stagingDir, name).outputStream().use { output -> zip.copyTo(output) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /** dictzip (`.dz`) archives are gzip-compatible; plain `.gz` index files are also handled. */
    private fun decompressStagedFiles(stagingDir: File) {
        stagingDir.listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(".dz", true) || it.name.endsWith(".gz", true)) }
            .forEach { compressed ->
                val targetName = compressed.name.replace(Regex("\\.(dz|gz)$", RegexOption.IGNORE_CASE), "")
                if (targetName.isEmpty()) return@forEach
                val target = File(stagingDir, targetName)
                GZIPInputStream(compressed.inputStream().buffered()).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                compressed.delete()
            }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun buildDictionaryId(base: String, info: StarDictInfo): String {
        val safeBase = base.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_', '.', '-')
            .ifEmpty { "dictionary" }
        val hash = Integer.toHexString((info.bookname + ":" + info.wordcount).hashCode())
        return "$safeBase-$hash"
    }
}
