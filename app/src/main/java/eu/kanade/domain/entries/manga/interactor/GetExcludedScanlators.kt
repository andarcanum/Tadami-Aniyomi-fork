package eu.kanade.domain.entries.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class GetExcludedScanlators(
    private val handler: MangaDatabaseHandler,
) {

    suspend fun await(mangaId: Long): Set<String> {
        return handler.awaitList { db ->
            db.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
        }
            .toSet()
    }

    fun subscribe(mangaId: Long): Flow<Set<String>> {
        return handler.subscribeToList { db ->
            db.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
        }
            .map { it.toSet() }
    }
}
