package eu.kanade.tachiyomi.data.library

import androidx.work.Data
import androidx.work.workDataOf
import java.io.File
import java.util.UUID

internal object LibraryUpdateEntryIds {
    const val KEY_ENTRY_IDS = "entryIds"
    const val KEY_ENTRY_IDS_FILE = "entryIdsFile"
    const val INLINE_ENTRY_IDS_LIMIT = 500
    private const val SPILL_DIR_NAME = "library_update_ids"

    fun buildInputData(
        cacheDir: File,
        mediaTag: String,
        entryIds: LongArray,
        inlineLimit: Int = INLINE_ENTRY_IDS_LIMIT,
    ): Data {
        val uniqueIds = entryIds.distinct().toLongArray()
        if (uniqueIds.isEmpty()) return workDataOf()
        if (uniqueIds.size <= inlineLimit) {
            return workDataOf(KEY_ENTRY_IDS to uniqueIds)
        }

        val spillDir = File(cacheDir, SPILL_DIR_NAME).apply { mkdirs() }
        val safeTag = mediaTag.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "entries" }
        val spillFile = File(spillDir, "${safeTag}_${UUID.randomUUID()}.txt")
        spillFile.bufferedWriter().use { writer ->
            uniqueIds.forEach { id ->
                writer.write(id.toString())
                writer.newLine()
            }
        }
        return workDataOf(KEY_ENTRY_IDS_FILE to spillFile.absolutePath)
    }

    fun hasTargetedEntryIds(inputData: Data): Boolean {
        val inlineIds = inputData.getLongArray(KEY_ENTRY_IDS)
        if (inlineIds != null && inlineIds.isNotEmpty()) return true
        return !inputData.getString(KEY_ENTRY_IDS_FILE).isNullOrBlank()
    }

    fun consumeEntryIds(
        inputData: Data,
        cacheDir: File,
    ): Set<Long>? {
        val inlineIds = inputData.getLongArray(KEY_ENTRY_IDS)
            ?.takeIf { it.isNotEmpty() }
            ?.toSet()
        if (inlineIds != null) return inlineIds

        val filePath = inputData.getString(KEY_ENTRY_IDS_FILE)?.takeIf { it.isNotBlank() } ?: return null
        val file = resolveSafeSpillFile(filePath, cacheDir) ?: return null
        return try {
            if (!file.isFile) {
                null
            } else {
                file.useLines { lines ->
                    lines.mapNotNull { it.trim().toLongOrNull() }.toSet()
                }.takeIf { it.isNotEmpty() }
            }
        } finally {
            runCatching { file.delete() }
        }
    }

    fun cleanupSpillFile(
        inputData: Data,
        cacheDir: File,
    ) {
        val filePath = inputData.getString(KEY_ENTRY_IDS_FILE)?.takeIf { it.isNotBlank() } ?: return
        val file = resolveSafeSpillFile(filePath, cacheDir) ?: return
        runCatching { file.delete() }
    }

    private fun resolveSafeSpillFile(
        filePath: String,
        cacheDir: File,
    ): File? {
        return runCatching {
            val allowedDir = File(cacheDir, SPILL_DIR_NAME).canonicalFile
            val candidate = File(filePath).canonicalFile
            if (candidate.parentFile == allowedDir) candidate else null
        }.getOrNull()
    }
}

internal fun shouldExpandAnimeSeasonsForUpdate(
    hasTargetedEntryIds: Boolean,
    updateSeasonOnLibraryUpdate: Boolean,
): Boolean = hasTargetedEntryIds || updateSeasonOnLibraryUpdate
