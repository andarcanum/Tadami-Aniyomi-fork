package tachiyomi.domain.storage.service

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import tachiyomi.core.common.storage.renameToOrCopy
import java.util.UUID

class StorageManager(
    private val context: Context,
    private val storagePreferences: StoragePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = getBaseDir(storagePreferences.baseStorageDirectory().get())

    /**
     * C-M12: baseDir was resolved once at init (and on pref change) behind an exists() check;
     * if the storage was unmounted at that moment (or got unmounted later), EVERY directory
     * getter returned null until a pref change - downloads threw on downloadsDir!! and the
     * download cache kept an empty snapshot. Re-resolve lazily when the cached value is missing
     * or no longer exists (remount heals without a pref toggle).
     */
    private fun baseDirectory(): UniFile? {
        baseDir?.takeIf { it.exists() }?.let { return it }
        return getBaseDir(storagePreferences.baseStorageDirectory().get()).also { baseDir = it }
    }

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        storagePreferences.baseStorageDirectory().changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = getBaseDir(uri)
                baseDir?.let { parent ->
                    parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
                    parent.createDirectory(LOCAL_SOURCE_PATH)
                    parent.createDirectory(LOCAL_ANIMESOURCE_PATH)
                    parent.createDirectory(LOCAL_NOVELSOURCE_PATH)
                    parent.createDirectory(DOWNLOADS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    parent.createDirectory(MPV_CONFIG_PATH)?.let { mpvDir ->
                        mpvDir.createDirectory(FONTS_PATH)
                        mpvDir.createDirectory(SCRIPTS_PATH)
                        mpvDir.createDirectory(SCRIPT_OPTS_PATH)
                        mpvDir.createDirectory(SHADERS_PATH)
                    }
                }
                _changes.send(Unit)
            }
            .launchIn(scope)
    }

    private fun getBaseDir(uri: String): UniFile? {
        return UniFile.fromUri(context, uri.toUri())
            .takeIf { it?.exists() == true }
    }

    /**
     * Runs an end-to-end write smoke test against [uri], mirroring the operations the
     * downloaders perform (create/write/rename/read/delete through SAF).
     *
     * @return null when [uri] is writable, otherwise a [StorageWriteTestFailure]
     * describing the first failed step.
     */
    fun canWriteTo(uri: Uri): StorageWriteTestFailure? {
        var testDir: UniFile? = null
        var cleanupSuccessful = false

        return try {
            val baseDir = storageTestStep("access the selected folder") {
                UniFile.fromUri(context, uri)
                    ?.takeIf { it.exists() && it.isDirectory }
            }

            val createdTestDir = storageTestStep("create the test directory") {
                baseDir.createDirectory("$STORAGE_TEST_DIR_PREFIX${UUID.randomUUID()}")
            }
            testDir = createdTestDir

            val downloadsDir = storageTestStep("create the downloads subdirectory") {
                createdTestDir.createDirectory(DOWNLOADS_PATH)
            }
            storageTestStep("re-open the downloads subdirectory") {
                createdTestDir.createDirectory(DOWNLOADS_PATH)
            }

            val sourceDir = storageTestStep("create the source subdirectory") {
                downloadsDir.createDirectory(STORAGE_TEST_SOURCE_DIR)
            }
            storageTestStep("re-open the source subdirectory") {
                downloadsDir.createDirectory(STORAGE_TEST_SOURCE_DIR)
            }

            val mangaDir = storageTestStep("create the manga subdirectory") {
                sourceDir.createDirectory(STORAGE_TEST_MANGA_DIR)
            }
            storageTestStep("re-open the manga subdirectory") {
                sourceDir.createDirectory(STORAGE_TEST_MANGA_DIR)
            }

            var chapterDir = storageTestStep("create the chapter subdirectory") {
                mangaDir.createDirectory(STORAGE_TEST_CHAPTER_DIR_TMP)
            }
            var pageFile = storageTestStep("create the page file") {
                chapterDir.createFile(STORAGE_TEST_PAGE_FILE_TMP)
            }

            storageTestStep("write the page file") {
                pageFile.openOutputStream().use { output ->
                    output.write(STORAGE_TEST_CONTENT)
                }
            }
            storageTestStep("rename the page file") {
                pageFile.renameToOrCopy(STORAGE_TEST_PAGE_FILE)
            }
            pageFile = storageTestStep("find the renamed page file") {
                chapterDir.findFile(STORAGE_TEST_PAGE_FILE)
            }

            storageTestStep("read the page file back") {
                pageFile.openInputStream().use { input ->
                    if (input.read() == STORAGE_TEST_CONTENT.first().toInt()) Unit else null
                }
            }
            storageTestStep("list the chapter directory") {
                if (chapterDir.listFiles().orEmpty().none { it.name == STORAGE_TEST_PAGE_FILE }) null else Unit
            }

            val comicInfoFile = storageTestStep("create the ComicInfo file") {
                chapterDir.createFile(STORAGE_TEST_COMIC_INFO_FILE)
            }
            storageTestStep("write the ComicInfo file") {
                comicInfoFile.openOutputStream().use { output ->
                    output.write(STORAGE_TEST_COMIC_INFO_CONTENT)
                }
            }

            val archiveFile = storageTestStep("create the archive file") {
                mangaDir.createFile(STORAGE_TEST_ARCHIVE_FILE_TMP)
            }
            storageTestStep("write the archive file") {
                archiveFile.openOutputStream().use { output ->
                    output.write(STORAGE_TEST_CONTENT)
                }
            }
            storageTestStep("rename the archive file") {
                archiveFile.renameToOrCopy(STORAGE_TEST_ARCHIVE_FILE)
            }
            storageTestStep("find the renamed archive file") {
                mangaDir.findFile(STORAGE_TEST_ARCHIVE_FILE)
            }

            storageTestStep("rename the chapter directory") {
                chapterDir.renameToOrCopy(STORAGE_TEST_CHAPTER_DIR)
            }
            chapterDir = storageTestStep("find the renamed chapter directory") {
                mangaDir.findFile(STORAGE_TEST_CHAPTER_DIR)
            }
            pageFile = storageTestStep("find the page file after rename") {
                chapterDir.findFile(STORAGE_TEST_PAGE_FILE)
            }
            storageTestStep("find the ComicInfo file after rename") {
                chapterDir.findFile(STORAGE_TEST_COMIC_INFO_FILE)
            }
            storageTestStep("list the chapter directory after rename") {
                if (chapterDir.listFiles().orEmpty().none { it.name == pageFile.name }) null else Unit
            }

            storageTestStep("create the .nomedia file") {
                DiskUtil.createNoMediaFile(chapterDir, context)
            }
            storageTestStep("delete the test directory") {
                if (createdTestDir.deleteTree()) Unit else null
            }
            cleanupSuccessful = true

            null
        } catch (e: StorageWriteTestStepException) {
            StorageWriteTestFailure(e.step, e.cause)
        } catch (e: Throwable) {
            StorageWriteTestFailure("unexpected error", e)
        } finally {
            if (!cleanupSuccessful) {
                testDir?.takeIf { it.exists() }?.deleteTree()
            }
        }
    }

    fun getAutomaticBackupsDirectory(): UniFile? {
        return baseDirectory()?.createDirectory(AUTOMATIC_BACKUPS_PATH)
    }

    fun getDownloadsDirectory(): UniFile? {
        return baseDirectory()?.createDirectory(DOWNLOADS_PATH)
    }

    fun getLocalMangaSourceDirectory(): UniFile? {
        return baseDirectory()?.createDirectory(LOCAL_SOURCE_PATH)
    }

    fun getLocalAnimeSourceDirectory(): UniFile? {
        return baseDirectory()?.createDirectory(LOCAL_ANIMESOURCE_PATH)
    }

    fun getLocalNovelSourceDirectory(): UniFile? {
        return baseDirectory()?.createDirectory(LOCAL_NOVELSOURCE_PATH)
    }

    fun getFontsDirectory(): UniFile? {
        return getMPVConfigDirectory()?.createDirectory(FONTS_PATH)
    }

    fun getScriptsDirectory(): UniFile? {
        return getMPVConfigDirectory()?.createDirectory(SCRIPTS_PATH)
    }

    fun getScriptOptsDirectory(): UniFile? {
        return getMPVConfigDirectory()?.createDirectory(SCRIPT_OPTS_PATH)
    }

    fun getShadersDirectory(): UniFile? {
        return getMPVConfigDirectory()?.createDirectory(SHADERS_PATH)
    }

    fun getMPVConfigDirectory(): UniFile? {
        return baseDirectory()?.createDirectory(MPV_CONFIG_PATH)
    }
}

private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
private const val DOWNLOADS_PATH = "downloads"
private const val LOCAL_SOURCE_PATH = "local"
private const val LOCAL_ANIMESOURCE_PATH = "localanime"
private const val LOCAL_NOVELSOURCE_PATH = "localnovel"
private const val MPV_CONFIG_PATH = "mpv-config"
private const val FONTS_PATH = "fonts"
const val SCRIPTS_PATH = "scripts"
const val SCRIPT_OPTS_PATH = "script-opts"
private const val SHADERS_PATH = "shaders"
private const val STORAGE_TEST_DIR_PREFIX = "tadami_storage_test_"
private const val STORAGE_TEST_SOURCE_DIR = "source"
private const val STORAGE_TEST_MANGA_DIR = "manga"
private const val STORAGE_TEST_CHAPTER_DIR_TMP = "chapter_tmp"
private const val STORAGE_TEST_CHAPTER_DIR = "chapter"
private const val STORAGE_TEST_PAGE_FILE_TMP = "001.tmp"
private const val STORAGE_TEST_PAGE_FILE = "001.jpg"
private const val STORAGE_TEST_COMIC_INFO_FILE = "ComicInfo.xml"
private const val STORAGE_TEST_ARCHIVE_FILE_TMP = "chapter.cbz_tmp"
private const val STORAGE_TEST_ARCHIVE_FILE = "chapter.cbz"
private val STORAGE_TEST_CONTENT = byteArrayOf(0)
private val STORAGE_TEST_COMIC_INFO_CONTENT = "<ComicInfo />".toByteArray()

private fun UniFile.deleteTree(): Boolean {
    var childrenDeleted = true
    try {
        listFiles().orEmpty().forEach {
            childrenDeleted = it.deleteTree() && childrenDeleted
        }
    } catch (_: Throwable) {
        childrenDeleted = false
    }

    val selfDeleted = try {
        delete()
    } catch (_: Throwable) {
        false
    }
    return childrenDeleted && selfDeleted
}

/**
 * The step that failed while verifying a candidate storage location, plus its cause, if any.
 */
class StorageWriteTestFailure(
    val step: String,
    val cause: Throwable? = null,
)

private class StorageWriteTestStepException(val step: String, cause: Throwable?) :
    RuntimeException(step, cause)

private inline fun <T> storageTestStep(step: String, block: () -> T?): T {
    val result = try {
        block()
    } catch (e: Throwable) {
        throw StorageWriteTestStepException(step, e)
    }
    if (result == null) throw StorageWriteTestStepException(step, null)
    return result
}
