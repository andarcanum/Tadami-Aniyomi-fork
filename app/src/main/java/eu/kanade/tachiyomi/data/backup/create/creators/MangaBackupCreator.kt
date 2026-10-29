package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.backupChapterWithMangaIdMapper
import eu.kanade.tachiyomi.data.backup.models.backupMangaTrackWithMangaIdMapper
import eu.kanade.tachiyomi.data.backup.models.toBackupMemoJson
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.entries.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaBackupCreator(
    private val handler: MangaDatabaseHandler = Injekt.get(),
) {

    suspend operator fun invoke(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        if (mangas.isEmpty()) return emptyList()
        val scopeIds = mangas.mapTo(hashSetOf()) { it.id }

        val excludedByManga = handler.awaitList { db ->
            db.excluded_scanlatorsQueries.getAll()
        }
            .filter { it.manga_id in scopeIds }
            .groupBy({ it.manga_id }, { it.scanlator })

        val chaptersByManga = if (!options.chapters) {
            emptyMap()
        } else {
            // Single-pass grouping: filter().groupBy() would allocate an intermediate list
            // of every chapter row (50-100k entries on a large library).
            val grouped = LinkedHashMap<Long, MutableList<BackupChapter>>()
            handler.awaitList { db ->
                db.chaptersQueries.getAllChaptersForBackup(backupChapterWithMangaIdMapper)
            }.forEach { (mangaId, chapter) ->
                if (mangaId in scopeIds) {
                    grouped.getOrPut(mangaId) { ArrayList() }.add(chapter)
                }
            }
            grouped
        }

        val categoryOrdersByManga = if (!options.categories) {
            emptyMap()
        } else {
            handler.awaitList { db -> db.categoriesQueries.getAllMangaCategoryOrders() }
                .filter { it.manga_id in scopeIds }
                .groupBy({ it.manga_id }, { it.`order` })
        }

        val tracksByManga = if (!options.tracking) {
            emptyMap()
        } else {
            handler.awaitList { db ->
                db.manga_syncQueries.getTracks(backupMangaTrackWithMangaIdMapper)
            }
                .filter { it.first in scopeIds }
                .groupBy({ it.first }, { it.second })
        }

        val historyByManga = if (!options.history) {
            emptyMap()
        } else {
            handler.awaitList { db -> db.historyQueries.getAllHistoryWithChapter() }
                .filter { it.manga_id in scopeIds }
                .groupBy { it.manga_id }
        }

        return mangas.map { manga ->
            manga.toBackupManga().apply {
                excludedScanlators = excludedByManga[manga.id].orEmpty()
                chaptersByManga[manga.id]?.takeUnless(List<BackupChapter>::isEmpty)
                    ?.let { chapters = it }
                categoryOrdersByManga[manga.id]?.takeUnless(List<Long>::isEmpty)
                    ?.let { categories = it }
                tracksByManga[manga.id]?.takeUnless(List<BackupTracking>::isEmpty)
                    ?.let { tracking = it }
                historyByManga[manga.id]?.map {
                    BackupHistory(it.url, it.last_read?.time ?: 0L, it.time_read)
                }?.takeUnless(List<BackupHistory>::isEmpty)
                    ?.let { history = it }
            }
        }
    }
}

private fun Manga.toBackupManga() =
    BackupManga(
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        notes = this.notes.takeIf { it.isNotBlank() },
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        rating = this.rating,
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        customTitle = this.customTitle,
        customArtist = this.customArtist,
        customAuthor = this.customAuthor,
        customDescription = this.customDescription,
        customGenre = this.customGenre,
        customStatus = this.customStatus,
        completedAt = this.completedAt,
        memoJson = toBackupMemoJson(this.memo),
    )
