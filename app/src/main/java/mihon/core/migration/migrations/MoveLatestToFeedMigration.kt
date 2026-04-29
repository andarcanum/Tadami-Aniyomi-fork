package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SourceType
import tachiyomi.domain.source.novel.service.NovelSourceManager

class MoveLatestToFeedMigration : Migration {
    override val version: Float = 138f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return false
        val insertFeedSavedSearch = migrationContext.get<InsertFeedSavedSearch>() ?: return false
        val mangaSourceManager = migrationContext.get<MangaSourceManager>()
        val animeSourceManager = migrationContext.get<AnimeSourceManager>()
        val novelSourceManager = migrationContext.get<NovelSourceManager>()

        val mangaFeedSources = sourcePreferences.mangaFeedSources().get()
        val animeFeedSources = sourcePreferences.animeFeedSources().get()
        val novelFeedSources = sourcePreferences.novelFeedSources().get()
        if (mangaFeedSources.isEmpty() && animeFeedSources.isEmpty() && novelFeedSources.isEmpty()) {
            return true
        }

        val entries = mapLegacyFeedEntries(
            mangaFeedSources = mangaFeedSources,
            animeFeedSources = animeFeedSources,
            novelFeedSources = novelFeedSources,
            mangaResolve = { id -> mangaSourceManager?.get(id) != null },
            animeResolve = { id -> animeSourceManager?.get(id) != null },
            novelResolve = { id -> novelSourceManager?.get(id) != null },
        )
        if (entries.isNotEmpty()) {
            insertFeedSavedSearch.awaitAll(entries)
        }

        sourcePreferences.mangaFeedSources().set(emptySet())
        sourcePreferences.animeFeedSources().set(emptySet())
        sourcePreferences.novelFeedSources().set(emptySet())
        return true
    }
}

internal fun mapLegacyFeedEntries(
    mangaFeedSources: Set<String>,
    animeFeedSources: Set<String>,
    novelFeedSources: Set<String>,
    mangaResolve: (Long) -> Boolean,
    animeResolve: (Long) -> Boolean,
    novelResolve: (Long) -> Boolean,
): List<FeedSavedSearch> {
    fun mapEntries(
        sources: Set<String>,
        sourceType: SourceType,
        resolve: (Long) -> Boolean,
    ): List<FeedSavedSearch> {
        return sources.mapNotNull { sourceId ->
            val id = sourceId.toLongOrNull() ?: return@mapNotNull null
            if (!resolve(id)) return@mapNotNull null
            FeedSavedSearch(
                id = -1,
                source = id,
                sourceType = sourceType,
                savedSearch = null,
                global = true,
                feedOrder = 0,
            )
        }
    }

    return buildList {
        addAll(mapEntries(mangaFeedSources, SourceType.MANGA, mangaResolve))
        addAll(mapEntries(animeFeedSources, SourceType.ANIME, animeResolve))
        addAll(mapEntries(novelFeedSources, SourceType.NOVEL, novelResolve))
    }
}
