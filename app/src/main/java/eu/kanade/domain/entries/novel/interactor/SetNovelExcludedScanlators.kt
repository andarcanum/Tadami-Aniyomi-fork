package eu.kanade.domain.entries.novel.interactor

import tachiyomi.data.handlers.novel.NovelDatabaseHandler

class SetNovelExcludedScanlators(
    private val handler: NovelDatabaseHandler,
) {

    suspend fun await(novelId: Long, excludedScanlators: Set<String>) {
        handler.await(inTransaction = true) {
            val currentExcluded = handler.awaitList {
                novel_excluded_scanlatorsQueries.getExcludedScanlatorsByNovelId(novelId)
            }.toSet()
            val toAdd = excludedScanlators.minus(currentExcluded)
            for (scanlator in toAdd) {
                novel_excluded_scanlatorsQueries.insert(novelId, scanlator)
            }
            val toRemove = currentExcluded.minus(excludedScanlators)
            novel_excluded_scanlatorsQueries.remove(novelId, toRemove)
        }
    }
}
