package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences

class DefaultChapterSortAscendingMigration : Migration {
    override val version = 136f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return false
        val setMangaDefaultChapterFlags = migrationContext.get<SetMangaDefaultChapterFlags>() ?: return false

        libraryPreferences.sortChapterByAscendingOrDescending().set(Manga.CHAPTER_SORT_ASC)
        setMangaDefaultChapterFlags.awaitAll()

        return true
    }
}
