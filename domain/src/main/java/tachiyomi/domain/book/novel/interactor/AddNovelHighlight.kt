package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

class AddNovelHighlight(
    private val repository: NovelHighlightRepository,
) {
    suspend fun await(highlight: NovelHighlight): Long = repository.add(highlight)
}
