package tachiyomi.source.local.image.novel

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.novel.LocalNovelSourceFileSystem
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"

actual class LocalNovelCoverManager(
    private val context: Context,
    private val fileSystem: LocalNovelSourceFileSystem,
) {

    actual fun find(novelUrl: String): UniFile? {
        return fileSystem.getFilesInNovelDirectory(novelUrl)
            .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    actual fun update(
        novel: SNovel,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getNovelDirectory(novel.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = find(novel.url) ?: directory.createFile(DEFAULT_COVER_NAME)!!

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        novel.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }
}
