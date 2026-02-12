package tachiyomi.domain.source.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.novel.model.NovelSourceWithCount
import tachiyomi.domain.source.novel.repository.NovelSourceRepository

class GetNovelSourcesWithNonLibraryNovels(
    private val repository: NovelSourceRepository,
) {

    fun subscribe(): Flow<List<NovelSourceWithCount>> {
        return repository.getNovelSourcesWithNonLibraryNovels()
    }
}
