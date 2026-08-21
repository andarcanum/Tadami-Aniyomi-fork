package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

class UpdateNovelHighlight(
    private val repository: NovelHighlightRepository,
) {
    suspend fun await(highlightId: Long, note: String, colorArgb: Long, updatedAt: Long) {
        repository.updateNoteAndColor(highlightId, note, colorArgb, updatedAt)
    }
}
