package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.library.service.LibraryPreferences

internal suspend fun applyDefaultChapterEpisodeSortToNumber(
    episodeSortPreference: Preference<Long>,
    chapterSortPreference: Preference<Long>,
    applyToExistingAnime: suspend () -> Unit,
    applyToExistingManga: suspend () -> Unit,
) {
    episodeSortPreference.set(Anime.EPISODE_SORTING_NUMBER)
    chapterSortPreference.set(Manga.CHAPTER_SORTING_NUMBER)
    applyToExistingAnime()
    applyToExistingManga()
}

class DefaultChapterEpisodeSortNumberMigration : Migration {
    override val version = 135f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return false
        val setAnimeDefaultEpisodeFlags = migrationContext.get<SetAnimeDefaultEpisodeFlags>() ?: return false
        val setMangaDefaultChapterFlags = migrationContext.get<SetMangaDefaultChapterFlags>() ?: return false

        applyDefaultChapterEpisodeSortToNumber(
            episodeSortPreference = libraryPreferences.sortEpisodeBySourceOrNumber(),
            chapterSortPreference = libraryPreferences.sortChapterBySourceOrNumber(),
            applyToExistingAnime = { setAnimeDefaultEpisodeFlags.awaitAll() },
            applyToExistingManga = { setMangaDefaultChapterFlags.awaitAll() },
        )

        return true
    }
}
