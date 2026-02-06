package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.repository.NovelRepository

class ResetNovelViewerFlags(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(): Boolean {
        return novelRepository.resetNovelViewerFlags()
    }
}