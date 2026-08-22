package tachiyomi.domain.book.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

class GetAllNovelHighlights(
    private val repository: NovelHighlightRepository,
) {
    fun subscribeAll(): Flow<List<NovelHighlightWithChapter>> = repository.subscribeAll()
}
