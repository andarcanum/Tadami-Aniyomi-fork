package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class UpdateNovelCategoryFlags(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(flags: Long) {
        repository.updateAllFlags(flags)
    }
}
