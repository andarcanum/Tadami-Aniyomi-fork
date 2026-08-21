package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

class GetNovelHighlights(
    private val repository: NovelHighlightRepository,
) {
    suspend fun await(novelId: Long): List<NovelHighlight> = repository.getForNovel(novelId)
}
