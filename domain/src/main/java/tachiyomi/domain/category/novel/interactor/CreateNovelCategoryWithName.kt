package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class CreateNovelCategoryWithName(
    private val repository: NovelCategoryRepository,
) {
    suspend fun await(name: String, order: Long, flags: Long): Long? {
        return repository.insertCategory(
            NovelCategory(
                id = 0,
                name = name,
                order = order,
                flags = flags,
                hidden = false,
            ),
        )
    }
}
