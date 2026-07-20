package eu.kanade.tachiyomi.ui.browse.anime.migration.list

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import tachiyomi.domain.entries.anime.model.Anime

internal data class AnimeMigrationSearchCandidate(
    val sourceIndex: Int,
    val source: AnimeCatalogueSource,
    val anime: Anime,
    val episodeInfo: AnimeMigrationListScreenModel.EpisodeInfo,
)

internal fun buildAnimeMigrationSearchParams(
    anime: Anime,
    manualExtraSearchQuery: String?,
    useAutoMetadata: Boolean,
): String? {
    val parts = mutableListOf<String>()

    manualExtraSearchQuery
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(parts::add)

    if (useAutoMetadata) {
        buildAnimeMigrationAutoSearchParams(anime)?.let(parts::add)
    }

    return parts.joinToString(" ").trim().ifBlank { null }
}

private fun buildAnimeMigrationAutoSearchParams(anime: Anime): String? {
    val parts = buildList {
        anime.author?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        anime.artist?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        anime.genre
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.let(::add)
    }

    return parts.joinToString(" ").trim().ifBlank { null }
}

internal fun shouldIncludeAnimeMigrationEntry(
    item: AnimeMigrationListScreenModel.MigratingAnime,
    hideNotFound: Boolean,
    onlyNewEpisodes: Boolean,
): Boolean {
    return when (val result = item.searchResult) {
        AnimeMigrationListScreenModel.SearchResult.Searching -> true
        AnimeMigrationListScreenModel.SearchResult.NotFound -> !hideNotFound
        is AnimeMigrationListScreenModel.SearchResult.Success -> {
            !onlyNewEpisodes || hasNewEpisodes(item.latestEpisode, result.latestEpisode)
        }
    }
}

internal fun visibleAnimeMigrationItems(
    items: List<AnimeMigrationListScreenModel.MigratingAnime>,
    hideNotFound: Boolean,
    onlyNewEpisodes: Boolean,
): List<AnimeMigrationListScreenModel.MigratingAnime> {
    return items.filter { shouldIncludeAnimeMigrationEntry(it, hideNotFound, onlyNewEpisodes) }
}

internal fun animeMigrationSkippedCount(
    items: List<AnimeMigrationListScreenModel.MigratingAnime>,
): Int {
    return items.count { it.searchResult !is AnimeMigrationListScreenModel.SearchResult.Success }
}

internal fun isAnimeMigrationSearchComplete(
    items: List<AnimeMigrationListScreenModel.MigratingAnime>,
): Boolean {
    return items.none { it.searchResult == AnimeMigrationListScreenModel.SearchResult.Searching }
}

internal fun hasNewEpisodes(
    oldLatestEpisode: Double?,
    newLatestEpisode: Double?,
): Boolean {
    return newLatestEpisode != null &&
        (oldLatestEpisode == null || newLatestEpisode > oldLatestEpisode)
}

internal fun selectAnimeMigrationSearchCandidate(
    candidates: List<AnimeMigrationSearchCandidate>,
    strategy: SourcePreferences.MigrationStrategy,
): AnimeMigrationSearchCandidate? {
    return when (strategy) {
        SourcePreferences.MigrationStrategy.FIRST_SOURCE -> {
            candidates.minByOrNull { it.sourceIndex }
        }
        SourcePreferences.MigrationStrategy.MOST_CHAPTERS -> {
            candidates.maxWithOrNull(
                compareBy<AnimeMigrationSearchCandidate> { it.episodeInfo.episodeCount }
                    .thenBy { -it.sourceIndex },
            )
        }
    }
}
