package tachiyomi.data.updates.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.updates.novel.model.NovelUpdatesWithRelations
import tachiyomi.domain.updates.novel.repository.NovelUpdatesRepository

class NovelUpdatesRepositoryImpl(
    private val databaseHandler: NovelDatabaseHandler,
) : NovelUpdatesRepository {

    override suspend fun awaitWithRead(read: Boolean, after: Long, limit: Long): List<NovelUpdatesWithRelations> {
        return databaseHandler.awaitList {
            novelupdatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        }
    }

    override fun subscribeAllNovelUpdates(after: Long, limit: Long): Flow<List<NovelUpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            novelupdatesViewQueries.getRecentUpdates(after, limit, ::mapUpdatesWithRelations)
        }
    }

    override fun subscribeWithRead(read: Boolean, after: Long, limit: Long): Flow<List<NovelUpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            novelupdatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        }
    }

    private fun mapUpdatesWithRelations(
        novelId: Long,
        novelTitle: String,
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
        dateUpload: Long,
        dateFetch: Long,
    ): NovelUpdatesWithRelations = NovelUpdatesWithRelations(
        novelId = novelId,
        novelTitle = novelTitle,
        chapterId = chapterId,
        chapterName = chapterName,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = NovelCover(
            novelId = novelId,
            sourceId = sourceId,
            isNovelFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
