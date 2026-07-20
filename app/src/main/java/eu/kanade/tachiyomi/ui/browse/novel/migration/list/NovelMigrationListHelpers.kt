package eu.kanade.tachiyomi.ui.browse.novel.migration.list

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import tachiyomi.domain.entries.novel.model.Novel

internal data class NovelMigrationSearchCandidate(
    val sourceIndex: Int,
    val source: NovelCatalogueSource,
    val novel: Novel,
    val chapterInfo: NovelMigrationListScreenModel.ChapterInfo,
)

internal fun buildNovelMigrationSearchParams(
    novel: Novel,
    manualExtraSearchQuery: String?,
    useAutoMetadata: Boolean,
): String? {
    val parts = mutableListOf<String>()

    manualExtraSearchQuery
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(parts::add)

    if (useAutoMetadata) {
        buildNovelMigrationAutoSearchParams(novel)?.let(parts::add)
    }

    return parts.joinToString(" ").trim().ifBlank { null }
}

private fun buildNovelMigrationAutoSearchParams(novel: Novel): String? {
    val parts = buildList {
        novel.author?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        novel.genre
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.let(::add)
    }

    return parts.joinToString(" ").trim().ifBlank { null }
}

internal fun shouldIncludeNovelMigrationEntry(
    item: NovelMigrationListScreenModel.MigratingNovel,
    hideNotFound: Boolean,
    onlyNewChapters: Boolean,
): Boolean {
    return when (val result = item.searchResult) {
        NovelMigrationListScreenModel.SearchResult.Searching -> true
        NovelMigrationListScreenModel.SearchResult.NotFound -> !hideNotFound
        is NovelMigrationListScreenModel.SearchResult.Success -> {
            !onlyNewChapters || hasNewNovelChapters(item.latestChapter, result.latestChapter)
        }
    }
}

internal fun visibleNovelMigrationItems(
    items: List<NovelMigrationListScreenModel.MigratingNovel>,
    hideNotFound: Boolean,
    onlyNewChapters: Boolean,
): List<NovelMigrationListScreenModel.MigratingNovel> {
    return items.filter { shouldIncludeNovelMigrationEntry(it, hideNotFound, onlyNewChapters) }
}

internal fun novelMigrationSkippedCount(
    items: List<NovelMigrationListScreenModel.MigratingNovel>,
): Int {
    return items.count { it.searchResult !is NovelMigrationListScreenModel.SearchResult.Success }
}

internal fun isNovelMigrationSearchComplete(
    items: List<NovelMigrationListScreenModel.MigratingNovel>,
): Boolean {
    return items.none { it.searchResult == NovelMigrationListScreenModel.SearchResult.Searching }
}

internal fun hasNewNovelChapters(
    oldLatestChapter: Double?,
    newLatestChapter: Double?,
): Boolean {
    return newLatestChapter != null &&
        (oldLatestChapter == null || newLatestChapter > oldLatestChapter)
}

internal fun selectNovelMigrationSearchCandidate(
    candidates: List<NovelMigrationSearchCandidate>,
    strategy: SourcePreferences.MigrationStrategy,
): NovelMigrationSearchCandidate? {
    return when (strategy) {
        SourcePreferences.MigrationStrategy.FIRST_SOURCE -> {
            candidates.minByOrNull { it.sourceIndex }
        }
        SourcePreferences.MigrationStrategy.MOST_CHAPTERS -> {
            candidates.maxWithOrNull(
                compareBy<NovelMigrationSearchCandidate> { it.chapterInfo.chapterCount }
                    .thenBy { -it.sourceIndex },
            )
        }
    }
}
