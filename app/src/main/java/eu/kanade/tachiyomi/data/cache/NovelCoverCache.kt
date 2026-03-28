package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.entries.novel.model.Novel
import java.io.File

class NovelCoverCache private constructor(
    private val cacheDir: File,
) {

    companion object {
        private const val COVERS_DIR = "novelcovers"
    }

    constructor(context: Context) : this(
        context.getExternalFilesDir(COVERS_DIR)
            ?: File(context.filesDir, COVERS_DIR).also { it.mkdirs() },
    )

    internal constructor(rootDir: File, createDir: Boolean) : this(
        if (createDir) rootDir.also { it.mkdirs() } else rootDir,
    )

    fun getCoverFile(novelThumbnailUrl: String?): File? {
        return novelThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    fun deleteFromCache(novel: Novel): Int {
        var deleted = 0

        getCoverFile(novel.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        return deleted
    }
}
