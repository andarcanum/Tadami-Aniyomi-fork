package eu.kanade.domain.entries.manga.interactor

import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class SetExcludedScanlators(
    private val handler: MangaDatabaseHandler,
) {

    suspend fun await(mangaId: Long, excludedScanlators: Set<String>) {
        handler.await(inTransaction = true) { db ->
            val currentExcluded = handler.awaitList { db ->
                db.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
            }.toSet()
            val toAdd = excludedScanlators.minus(currentExcluded)
            for (scanlator in toAdd) {
                db.excluded_scanlatorsQueries.insert(mangaId, scanlator)
            }
            val toRemove = currentExcluded.minus(excludedScanlators)
            db.excluded_scanlatorsQueries.remove(mangaId, toRemove)
        }
    }
}
