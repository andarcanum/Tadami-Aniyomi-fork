package eu.kanade.domain.entries.novel.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.novel.NovelDatabaseHandler

class GetNovelExcludedScanlators(
    private val handler: NovelDatabaseHandler,
) {

    suspend fun await(novelId: Long): Set<String> {
        return handler.awaitList {
            novel_excluded_scanlatorsQueries.getExcludedScanlatorsByNovelId(novelId)
        }
            .toSet()
    }

    fun subscribe(novelId: Long): Flow<Set<String>> {
        return handler.subscribeToList {
            novel_excluded_scanlatorsQueries.getExcludedScanlatorsByNovelId(novelId)
        }
            .map { it.toSet() }
    }
}
