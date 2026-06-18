package eu.kanade.tachiyomi.ui.browse.manga.migration.list

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import tachiyomi.domain.entries.manga.model.Manga

internal data class MigrationSearchCandidate(
    val sourceIndex: Int,
    val source: CatalogueSource,
    val manga: Manga,
    val chapterInfo: MigrationListScreenModel.ChapterInfo,
)

internal fun buildMigrationSearchParams(
    manga: Manga,
    manualExtraSearchQuery: String?,
    useAutoMetadata: Boolean,
): String? {
    val parts = mutableListOf<String>()

    manualExtraSearchQuery
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(parts::add)

    if (useAutoMetadata) {
        buildMigrationAutoSearchParams(manga)?.let(parts::add)
    }

    return parts.joinToString(" ").trim().ifBlank { null }
}

private fun buildMigrationAutoSearchParams(manga: Manga): String? {
    val parts = buildList {
        manga.author?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        manga.artist?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        manga.genre
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.let(::add)
    }

    return parts.joinToString(" ").trim().ifBlank { null }
}

internal fun shouldIncludeMigrationEntry(
    item: MigrationListScreenModel.MigratingManga,
    hideNotFound: Boolean,
    onlyNewChapters: Boolean,
): Boolean {
    return when (val result = item.searchResult) {
        MigrationListScreenModel.SearchResult.Searching -> true
        MigrationListScreenModel.SearchResult.NotFound -> !hideNotFound
        is MigrationListScreenModel.SearchResult.Success -> {
            !onlyNewChapters || hasNewChapters(item.latestChapter, result.latestChapter)
        }
    }
}

internal fun visibleMigrationItems(
    items: List<MigrationListScreenModel.MigratingManga>,
    hideNotFound: Boolean,
    onlyNewChapters: Boolean,
): List<MigrationListScreenModel.MigratingManga> {
    return items.filter { shouldIncludeMigrationEntry(it, hideNotFound, onlyNewChapters) }
}

internal fun migrationSkippedCount(
    items: List<MigrationListScreenModel.MigratingManga>,
): Int {
    return items.count { it.searchResult !is MigrationListScreenModel.SearchResult.Success }
}

internal fun isMigrationSearchComplete(
    items: List<MigrationListScreenModel.MigratingManga>,
): Boolean {
    return items.none { it.searchResult == MigrationListScreenModel.SearchResult.Searching }
}

internal fun hasNewChapters(
    oldLatestChapter: Double?,
    newLatestChapter: Double?,
): Boolean {
    return newLatestChapter != null &&
        (oldLatestChapter == null || newLatestChapter > oldLatestChapter)
}

internal fun selectMigrationSearchCandidate(
    candidates: List<MigrationSearchCandidate>,
    strategy: SourcePreferences.MigrationStrategy,
): MigrationSearchCandidate? {
    return when (strategy) {
        SourcePreferences.MigrationStrategy.FIRST_SOURCE -> {
            candidates.minByOrNull { it.sourceIndex }
        }
        SourcePreferences.MigrationStrategy.MOST_CHAPTERS -> {
            candidates.maxWithOrNull(
                compareBy<MigrationSearchCandidate> { it.chapterInfo.chapterCount }
                    .thenBy { -it.sourceIndex },
            )
        }
    }
}
