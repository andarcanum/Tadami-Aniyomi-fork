package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelBookState
import eu.kanade.tachiyomi.data.backup.models.BackupNovelHighlight
import tachiyomi.data.book.novel.NovelBookStateRepositoryImpl
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.book.novel.interactor.GetNovelBookState
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelBackupCreator(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getNovelBookState: GetNovelBookState = Injekt.get(),
) {

    suspend operator fun invoke(novels: List<Novel>, options: BackupOptions): List<BackupNovel> {
        if (novels.isEmpty()) return emptyList()
        val scopeIds = novels.mapTo(hashSetOf()) { it.id }

        val excludedByNovel = handler.awaitList { db ->
            db.novel_excluded_scanlatorsQueries.getAll()
        }
            .filter { it.novel_id in scopeIds }
            .groupBy({ it.novel_id }, { it.scanlator })

        val chapterRows = if (!options.chapters) {
            emptyList()
        } else {
            handler.awaitList { db ->
                db.novel_chaptersQueries.getAllChaptersForBackup(novelChapterRowMapper)
            }
        }
        // Single-pass grouping for the chapters table: filter().groupBy() would allocate an
        // intermediate list of every row.
        val chaptersByNovel = LinkedHashMap<Long, MutableList<BackupChapter>>()
        chapterRows.forEach { row ->
            if (row.novelId in scopeIds) {
                chaptersByNovel.getOrPut(row.novelId) { ArrayList() }.add(row.backup)
            }
        }
        // One fetch serves both the payload and bookState lastChapterUrl lookups.
        val chapterUrlById = chapterRows.associate { it.chapterId to it.backup.url }

        val highlightsByNovel = if (!options.chapters) {
            emptyMap()
        } else {
            handler.awaitList { db -> db.novel_highlightsQueries.getAllHighlightsWithChapterUrl() }
                .filter { it.novel_id in scopeIds }
                .groupBy { it.novel_id }
        }

        val categoryOrdersByNovel = if (!options.categories) {
            emptyMap()
        } else {
            handler.awaitList { db -> db.novel_categoriesQueries.getAllNovelCategoryOrders() }
                .filter { it.novel_id in scopeIds }
                .groupBy({ it.novel_id }, { it.`order` })
        }

        val historyByNovel = if (!options.history) {
            emptyMap()
        } else {
            handler.awaitList { db -> db.novel_historyQueries.getAllHistoryWithChapter() }
                .filter { it.novel_id in scopeIds }
                .groupBy { it.novel_id }
        }

        // novel_books.novel_id is the primary key: a novel has at most one book state.
        val bookStateByNovel = getNovelBookState.awaitAll().associateBy { it.novelId }

        return novels.map { novel ->
            novel.toBackupNovel().apply {
                excludedScanlators = excludedByNovel[novel.id].orEmpty()
                chaptersByNovel[novel.id]?.takeUnless(List<BackupChapter>::isEmpty)
                    ?.let { chapters = it }
                highlightsByNovel[novel.id]?.map { row ->
                    BackupNovelHighlight(
                        chapterUrl = row.chapter_url.orEmpty(),
                        blockIndex = row.block_index.toInt(),
                        charStart = row.char_start.toInt(),
                        charEndExclusive = row.char_end_exclusive.toInt(),
                        normalizedText = row.normalized_text,
                        colorArgb = row.color_argb,
                        note = row.note,
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                        pageIndex = row.page_index.toInt(),
                        pageCount = row.page_count.toInt(),
                    )
                }?.takeUnless(List<BackupNovelHighlight>::isEmpty)
                    ?.let { highlights = it }
                categoryOrdersByNovel[novel.id]?.takeUnless(List<Long>::isEmpty)
                    ?.let { categories = it }
                historyByNovel[novel.id]?.map {
                    BackupHistory(it.url, it.last_read?.time ?: 0L, it.time_read)
                }?.takeUnless(List<BackupHistory>::isEmpty)
                    ?.let { history = it }
                // Book state is NOT gated by the chapters option (unchanged behavior); it is
                // exported on every backup. lastChapterUrl prefers the in-memory chapter map
                // and falls back to a per-novel lookup for chapters outside the fetched scope.
                bookStateByNovel[novel.id]?.let { state ->
                    bookState = BackupNovelBookState(
                        enabled = state.enabled,
                        bookVersion = state.bookVersion,
                        sourceId = state.sourceId,
                        chapterSetHash = state.chapterSetHash,
                        totalChars = state.totalChars,
                        chapterCount = state.chapterCount,
                        charOffset = state.charOffset,
                        lastChapterUrl = state.lastChapterId?.let { chapterUrlById[it] }
                            ?: state.lastChapterId?.let { chapterId ->
                                runCatching {
                                    handler.awaitOne { db ->
                                        db.novel_chaptersQueries.getChapterById(chapterId)
                                    }.url
                                }.getOrNull()
                            },
                        complete = state.complete,
                        builtAt = state.builtAt,
                        updatedAt = state.updatedAt,
                        blockIndex = state.blockIndex,
                        chapterCharOffset = state.chapterCharOffset,
                        progressMigrated = state.progressMigrated,
                    )
                }
            }
        }
    }
}

private data class NovelChapterRow(
    val novelId: Long,
    val chapterId: Long,
    val backup: BackupChapter,
)

// Batched variant for getAllChaptersForBackup: novel_id (column 2) and the chapter _id
// (column 1) are bound so one query feeds the chapter payload, the highlight chapter URLs
// and the bookState lastChapterUrl lookup.
private val novelChapterRowMapper: (
    Long,
    Long,
    String,
    String,
    String?,
    Boolean,
    Boolean,
    Long,
    Double,
    Long,
    Long,
    Long,
    String?,
    Long,
    Long,
    Long,
    kotlinx.serialization.json.JsonObject,
) -> NovelChapterRow = {
        _id: Long,
        novelId: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        chapterNumber: Double,
        source_order: Long,
        dateFetch: Long,
        dateUpload: Long,
        dateUploadRaw: String?,
        lastModifiedAt: Long,
        version: Long,
        _: Long,
        _: kotlinx.serialization.json.JsonObject,
    ->
    NovelChapterRow(
        novelId = novelId,
        chapterId = _id,
        backup = BackupChapter(
            url = url,
            name = name,
            chapterNumber = chapterNumber.toFloat(),
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            dateFetch = dateFetch,
            dateUpload = dateUpload,
            dateUploadRaw = dateUploadRaw,
            sourceOrder = source_order,
            lastModifiedAt = lastModifiedAt,
            version = version,
        ),
    )
}

private fun Novel.toBackupNovel() =
    BackupNovel(
        url = this.url,
        title = this.title,
        author = this.author,
        description = this.description,
        notes = this.notes.takeIf { it.isNotBlank() },
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewerFlags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        customTitle = this.customTitle,
        customAuthor = this.customAuthor,
        customDescription = this.customDescription,
        customGenre = this.customGenre,
        customStatus = this.customStatus,
        completedAt = this.completedAt,
    )
