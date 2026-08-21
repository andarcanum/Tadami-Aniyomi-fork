package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

class DeleteNovelHighlight(
    private val repository: NovelHighlightRepository,
) {
    suspend fun await(highlightId: Long) {
        repository.delete(highlightId)
    }
}
