package tachiyomi.source.local.io.novel

import com.hippo.unifile.UniFile
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager

actual class LocalNovelSourceFileSystem(
    private val storageManager: StorageManager,
) {

    actual fun getBaseDirectory(): UniFile? {
        val dir = storageManager.getLocalNovelSourceDirectory()
        logcat(LogPriority.DEBUG) { "LocalNovelFileSystem: baseDir=$dir uri=${dir?.uri}" }
        return dir
    }

    actual fun getFilesInBaseDirectory(): List<UniFile> {
        val files = getBaseDirectory()?.listFiles()
        logcat(LogPriority.DEBUG) { "LocalNovelFileSystem: listFiles direct=${files?.map { it.name }}" }
        return files.orEmpty().toList()
    }

    actual fun getNovelDirectory(name: String): UniFile? {
        return getBaseDirectory()
            ?.findFile(name)
            ?.takeIf { it.isDirectory }
    }

    actual fun getFilesInNovelDirectory(name: String): List<UniFile> {
        return getNovelDirectory(name)?.listFiles().orEmpty().toList()
    }
}
