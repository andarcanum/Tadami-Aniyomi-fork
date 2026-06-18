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

private val DIRECTORY_COVER_NAMES = listOf("cover", "folder", "poster", "thumbnail")

actual class LocalNovelCoverManager(
    private val context: Context,
    private val fileSystem: LocalNovelSourceFileSystem,
) {

    actual fun find(novelUrl: String): UniFile? {
        val novelDir = fileSystem.getNovelDirectory(novelUrl)
        return if (novelDir != null) {
            novelDir.listFiles().orEmpty()
                .filter { it.isFile }
                .sortedBy { coverNamePriority(it.nameWithoutExtension.orEmpty()) }
                .firstOrNull {
                    isPreferredDirectoryCoverName(it.nameWithoutExtension.orEmpty()) &&
                        ImageUtil.isImage(it.name) { it.openInputStream() }
                }
        } else {
            val baseDir = fileSystem.getBaseDirectory() ?: return null
            val nameWithoutExt = novelUrl.substringBeforeLast('.')
            baseDir.listFiles().orEmpty()
                .filter {
                    it.isFile &&
                        !it.name.equals(novelUrl, ignoreCase = true) &&
                        it.nameWithoutExtension.equals(nameWithoutExt, ignoreCase = true)
                }
                .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
        }
    }

    private fun isPreferredDirectoryCoverName(nameWithoutExtension: String): Boolean {
        return coverNamePriority(nameWithoutExtension) != Int.MAX_VALUE
    }

    private fun coverNamePriority(nameWithoutExtension: String): Int {
        return DIRECTORY_COVER_NAMES.indexOfFirst { it.equals(nameWithoutExtension, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: Int.MAX_VALUE
    }

    actual fun update(
        novel: SNovel,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getNovelDirectory(novel.url)
        val targetFile = if (directory != null) {
            find(novel.url) ?: directory.createFile(DEFAULT_COVER_NAME)!!
        } else {
            val baseDir = fileSystem.getBaseDirectory() ?: return null
            val nameWithoutExt = novel.url.substringBeforeLast('.')
            find(novel.url) ?: baseDir.createFile("$nameWithoutExt.jpg")!!
        }

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (directory != null) {
            DiskUtil.createNoMediaFile(directory, context)
        }

        novel.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }
}
