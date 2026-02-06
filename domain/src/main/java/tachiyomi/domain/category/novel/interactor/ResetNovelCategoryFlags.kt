package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class ResetNovelCategoryFlags(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(flags: Long) {
        repository.updateAllFlags(flags)
    }
}