package eu.kanade.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository

class UpdateNovel(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(novelUpdate: NovelUpdate): Boolean {
        return novelRepository.updateNovel(novelUpdate)
    }

    suspend fun awaitAll(novelUpdates: List<NovelUpdate>): Boolean {
        return novelRepository.updateAllNovel(novelUpdates)
    }
}