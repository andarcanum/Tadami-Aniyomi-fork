package eu.kanade.domain.entries.novel.interactor

import tachiyomi.data.handlers.novel.NovelDatabaseHandler

class SetNovelExcludedScanlators(
    private val handler: NovelDatabaseHandler,
) {

    suspend fun await(novelId: Long, excludedScanlators: Set<String>) {
        handler.await(inTransaction = true) { db ->
            val currentExcluded = handler.awaitList { db ->
                db.novel_excluded_scanlatorsQueries.getExcludedScanlatorsByNovelId(novelId)
            }.toSet()
            val toAdd = excludedScanlators.minus(currentExcluded)
            for (scanlator in toAdd) {
                db.novel_excluded_scanlatorsQueries.insert(novelId, scanlator)
            }
            val toRemove = currentExcluded.minus(excludedScanlators)
            db.novel_excluded_scanlatorsQueries.remove(novelId, toRemove)
        }
    }
}
