package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga> [<mangaId>]/<chapter> [<chapterId>]
 *
 * DECISION-6: manga and chapter directory names carry their database ids (mirrors the novel
 * download layout). The historical title/name-only scheme collided after sanitization
 * ("A:B" == "A_B", >240-char truncation, duplicate chapter names, same-title manga of one
 * source sharing a directory), which produced cross-deletes, silent CBZ overwrites and
 * eternal-ERROR chapters. DECISION-7: all pre-suffix (and pre-0.9.2) name variants remain
 * readable through the lookup lists - legacy downloads keep working, are counted by the cache,
 * are found by the reader and are deletable; nothing is renamed or bulk-moved on disk.
 *
 * @param context the application context.
 */
class MangaDownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Returns the download directory for a manga (scoped, id-suffixed). For internal use only.
     *
     * @param mangaTitle the title of the manga to query.
     * @param mangaId the database id of the manga.
     * @param source the source of the manga.
     */
    internal fun getMangaDir(mangaTitle: String, mangaId: Long, source: MangaSource): UniFile {
        try {
            return downloadsDir!!
                .createDirectory(getSourceDirName(source))!!
                .createDirectory(getMangaDirName(mangaTitle, mangaId))!!
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Invalid download directory" }
            throw Exception(
                context.stringResource(
                    MR.strings.invalid_location,
                    downloadsDir?.displayablePath ?: "",
                ),
            )
        }
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: MangaSource): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    /**
     * Returns the scoped (id-suffixed) download directory for a manga if it exists.
     */
    fun findScopedMangaDir(mangaTitle: String, mangaId: Long, source: MangaSource): UniFile? {
        return findSourceDir(source)?.findFile(getMangaDirName(mangaTitle, mangaId))
    }

    /**
     * Returns the legacy title-only download directory for a manga if it exists. It can be
     * SHARED by same-title manga of one source - callers must never delete it wholesale.
     */
    fun findLegacyMangaDir(mangaTitle: String, source: MangaSource): UniFile? {
        return findSourceDir(source)?.findFile(getLegacyMangaDirName(mangaTitle))
    }

    /**
     * Returns the download directory for a manga if it exists (scoped first, legacy fallback).
     *
     * @param mangaTitle the title of the manga to query.
     * @param mangaId the database id of the manga (null falls back to the legacy name only).
     * @param source the source of the manga.
     */
    fun findMangaDir(mangaTitle: String, mangaId: Long?, source: MangaSource): UniFile? {
        val sourceDir = findSourceDir(source) ?: return null
        return mangaId?.let { sourceDir.findFile(getMangaDirName(mangaTitle, it)) }
            ?: sourceDir.findFile(getLegacyMangaDirName(mangaTitle))
    }

    /**
     * Returns the download directory for a chapter if it exists. Chapters may live in the
     * scoped manga dir (new downloads) or the legacy one (pre-DECISION-6 downloads); both are
     * searched, each against every valid name variant.
     */
    fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        mangaId: Long?,
        chapterId: Long?,
        source: MangaSource,
    ): UniFile? {
        val sourceDir = findSourceDir(source) ?: return null
        val names = getValidChapterDirNames(chapterName, chapterScanlator, chapterId)
        val mangaDirNames = listOfNotNull(
            mangaId?.let { getMangaDirName(mangaTitle, it) },
            getLegacyMangaDirName(mangaTitle),
        ).distinct()
        return mangaDirNames.asSequence()
            .mapNotNull { sourceDir.findFile(it) }
            .flatMap { mangaDir -> names.asSequence().mapNotNull { mangaDir.findFile(it) } }
            .firstOrNull()
    }

    /**
     * Returns the manga directories (scoped + legacy) and the downloaded directories of the
     * given chapters across them.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    fun findChapterDirs(
        chapters: List<Chapter>,
        manga: Manga,
        source: MangaSource,
    ): Pair<List<UniFile>, List<UniFile>> {
        val sourceDir = findSourceDir(source) ?: return emptyList<UniFile>() to emptyList<UniFile>()
        val mangaDirs = listOfNotNull(
            sourceDir.findFile(getMangaDirName(manga.title, manga.id)),
            sourceDir.findFile(getLegacyMangaDirName(manga.title)),
        ).distinctBy { it.name }
        val chapterDirs = mangaDirs.flatMap { mangaDir ->
            chapters.mapNotNull { chapter ->
                getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.id).asSequence()
                    .mapNotNull { mangaDir.findFile(it) }
                    .firstOrNull()
            }
        }
        return mangaDirs to chapterDirs
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: MangaSource): String {
        return DiskUtil.buildValidFilename(source.toString())
    }

    /**
     * Returns the download directory name for a manga. With [mangaId] this is the scoped
     * "<title> [<id>]" form; without it, the legacy title-only form.
     *
     * @param mangaTitle the title of the manga to query.
     * @param mangaId the database id of the manga.
     */
    fun getMangaDirName(mangaTitle: String, mangaId: Long? = null): String {
        return if (mangaId != null) {
            DiskUtil.buildValidFilename("$mangaTitle [$mangaId]")
        } else {
            getLegacyMangaDirName(mangaTitle)
        }
    }

    /**
     * Returns the legacy (pre-DECISION-6, title-only) download directory name for a manga.
     */
    fun getLegacyMangaDirName(mangaTitle: String): String {
        return DiskUtil.buildValidFilename(mangaTitle)
    }

    /**
     * Returns the chapter directory name for a chapter. With [chapterId] this is the scoped
     * "<name> [<id>]" form that kills sanitization collisions; without it, the legacy form.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterId the database id of the chapter.
     */
    fun getChapterDirName(chapterName: String, chapterScanlator: String?, chapterId: Long? = null): String {
        val newChapterName = sanitizeChapterName(chapterName)
        val base = when {
            !chapterScanlator.isNullOrBlank() -> "${chapterScanlator}_$newChapterName"
            else -> newChapterName
        }
        return DiskUtil.buildValidFilename(if (chapterId != null) "$base [$chapterId]" else base)
    }

    /**
     * Return the new name for the chapter (in case it's empty or blank)
     *
     * @param chapterName the name of the chapter
     */
    private fun sanitizeChapterName(chapterName: String): String {
        return chapterName.ifBlank {
            "Chapter"
        }
    }

    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        return oldChapter.name != newChapter.name ||
            oldChapter.scanlator?.takeIf { it.isNotBlank() } != newChapter.scanlator?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns valid downloaded chapter directory names: the scoped (id-suffixed) pair, the
     * legacy pair, and - per DECISION-7 - the baseline variants removed by the upstream cleanup
     * (Aniyomi 5419d987f): the pre-0.9.2 scanlator-less name and the "_name" form a blank
     * (non-null) scanlator produced. Read-only compatibility: nothing is written under the
     * legacy names anymore.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterId the database id of the chapter (null omits the scoped pair).
     */
    fun getValidChapterDirNames(
        chapterName: String,
        chapterScanlator: String?,
        chapterId: Long? = null,
    ): List<String> {
        return buildList(8) {
            if (chapterId != null) {
                val scopedName = getChapterDirName(chapterName, chapterScanlator, chapterId)
                // Folder of images
                add(scopedName)
                // Archived chapters
                add("$scopedName.cbz")
            }

            val legacyName = getChapterDirName(chapterName, chapterScanlator)
            add(legacyName)
            add("$legacyName.cbz")

            val plainName = DiskUtil.buildValidFilename(sanitizeChapterName(chapterName))
            if (!chapterScanlator.isNullOrBlank()) {
                add(plainName)
                add("$plainName.cbz")
            } else if (chapterScanlator != null) {
                val blankScanlatorName = DiskUtil.buildValidFilename("_" + sanitizeChapterName(chapterName))
                add(blankScanlatorName)
                add("$blankScanlatorName.cbz")
            }
        }.distinct()
    }
}
