package tachiyomi.data.items.novelchapter

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository

class NovelChapterRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelChapterRepository {

    override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> {
        return try {
            handler.await(inTransaction = true) {
                chapters.map { chapter ->
                    novel_chaptersQueries.insert(
                        chapter.novelId,
                        chapter.url,
                        chapter.name,
                        chapter.scanlator,
                        chapter.read,
                        chapter.bookmark,
                        chapter.lastPageRead,
                        chapter.chapterNumber,
                        chapter.sourceOrder,
                        chapter.dateFetch,
                        chapter.dateUpload,
                        chapter.version,
                    )
                    val lastInsertId = novel_chaptersQueries.selectLastInsertedRowId().executeAsOne()
                    chapter.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg chapterUpdates: NovelChapterUpdate) {
        handler.await(inTransaction = true) {
            chapterUpdates.forEach { chapterUpdate ->
                novel_chaptersQueries.update(
                    novelId = chapterUpdate.novelId,
                    url = chapterUpdate.url,
                    name = chapterUpdate.name,
                    scanlator = chapterUpdate.scanlator,
                    read = chapterUpdate.read,
                    bookmark = chapterUpdate.bookmark,
                    lastPageRead = chapterUpdate.lastPageRead,
                    chapterNumber = chapterUpdate.chapterNumber,
                    sourceOrder = chapterUpdate.sourceOrder,
                    dateFetch = chapterUpdate.dateFetch,
                    dateUpload = chapterUpdate.dateUpload,
                    chapterId = chapterUpdate.id,
                    version = chapterUpdate.version,
                    isSyncing = 0,
                )
            }
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
        try {
            handler.await { novel_chaptersQueries.removeChaptersWithIds(chapterIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByNovelId(novelId: Long, applyScanlatorFilter: Boolean): List<NovelChapter> {
        return handler.awaitList {
            novel_chaptersQueries.getChaptersByNovelId(novelId, applyScanlatorFilter.toLong(), ::mapChapter)
        }
    }

    override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> {
        return handler.awaitList {
            novel_chaptersQueries.getScanlatorsByNovelId(novelId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            novel_chaptersQueries.getScanlatorsByNovelId(novelId) { it.orEmpty() }
        }
    }

    override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> {
        return handler.awaitList {
            novel_chaptersQueries.getBookmarkedChaptersByNovelId(novelId, ::mapChapter)
        }
    }

    override suspend fun getChapterById(id: Long): NovelChapter? {
        return handler.awaitOneOrNull { novel_chaptersQueries.getChapterById(id, ::mapChapter) }
    }

    override suspend fun getChapterByNovelIdAsFlow(
        novelId: Long,
        applyScanlatorFilter: Boolean,
    ): Flow<List<NovelChapter>> {
        return handler.subscribeToList {
            novel_chaptersQueries.getChaptersByNovelId(novelId, applyScanlatorFilter.toLong(), ::mapChapter)
        }
    }

    override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? {
        return handler.awaitOneOrNull {
            novel_chaptersQueries.getChapterByUrlAndNovelId(
                url,
                novelId,
                ::mapChapter,
            )
        }
    }

    private fun mapChapter(
        id: Long,
        novelId: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
    ): NovelChapter = NovelChapter(
        id = id,
        novelId = novelId,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}
