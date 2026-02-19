package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCategoriesBackupCreator(
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getNovelCategories.await()
            .filter { it.id > 0L }
            .map {
                BackupCategory(
                    id = it.id,
                    name = it.name,
                    order = it.order,
                    flags = it.flags,
                )
            }
    }
}
