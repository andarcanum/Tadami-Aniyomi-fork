@file:Suppress("ktlint:standard:filename")

package eu.kanade.tachiyomi.ui.library

import eu.kanade.presentation.library.novel.NovelLibraryItem
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.source.local.entries.anime.isLocal
import tachiyomi.source.local.entries.manga.isLocal
import tachiyomi.source.local.entries.novel.isLocal

internal data class BulkUpdateSelection(
    val updatableIds: LongArray,
    val skippedLocalCount: Int,
) {
    val updatableCount: Int get() = updatableIds.size
    val hasUpdatable: Boolean get() = updatableIds.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BulkUpdateSelection) return false
        return skippedLocalCount == other.skippedLocalCount && updatableIds.contentEquals(other.updatableIds)
    }

    override fun hashCode(): Int = 31 * updatableIds.contentHashCode() + skippedLocalCount
}

internal fun resolveMangaBulkUpdateSelection(
    selection: List<MangaLibraryItem>,
): BulkUpdateSelection {
    val updatableIds = LinkedHashSet<Long>()
    val localIds = LinkedHashSet<Long>()
    selection.forEach { item ->
        when (item) {
            is MangaLibraryItem.Single -> {
                val manga = item.libraryManga.manga
                if (manga.isLocal()) localIds.add(manga.id) else updatableIds.add(manga.id)
            }
            is MangaLibraryItem.Series -> {
                item.librarySeries.entries.forEach { entry ->
                    if (entry.manga.isLocal()) localIds.add(entry.manga.id) else updatableIds.add(entry.manga.id)
                }
            }
        }
    }
    return BulkUpdateSelection(
        updatableIds = updatableIds.toLongArray(),
        skippedLocalCount = localIds.size,
    )
}

internal fun resolveAnimeBulkUpdateSelection(
    selection: List<AnimeLibraryItem>,
): BulkUpdateSelection {
    val updatableIds = LinkedHashSet<Long>()
    val localIds = LinkedHashSet<Long>()
    selection.forEach { item ->
        when (item) {
            is AnimeLibraryItem.Single -> {
                val anime = item.libraryAnime.anime
                if (anime.isLocal()) localIds.add(anime.id) else updatableIds.add(anime.id)
            }
            is AnimeLibraryItem.Series -> {
                item.librarySeries.entries.forEach { entry ->
                    if (entry.anime.isLocal()) localIds.add(entry.anime.id) else updatableIds.add(entry.anime.id)
                }
            }
        }
    }
    return BulkUpdateSelection(
        updatableIds = updatableIds.toLongArray(),
        skippedLocalCount = localIds.size,
    )
}

internal fun resolveNovelBulkUpdateSelection(
    selection: List<NovelLibraryItem>,
): BulkUpdateSelection {
    val updatableIds = LinkedHashSet<Long>()
    val localIds = LinkedHashSet<Long>()
    selection.forEach { item ->
        when (item) {
            is NovelLibraryItem.Single -> {
                val novel = item.libraryNovel.novel
                if (novel.isLocal()) localIds.add(novel.id) else updatableIds.add(novel.id)
            }
            is NovelLibraryItem.Series -> {
                item.librarySeries.entries.forEach { entry ->
                    if (entry.novel.isLocal()) localIds.add(entry.novel.id) else updatableIds.add(entry.novel.id)
                }
            }
        }
    }
    return BulkUpdateSelection(
        updatableIds = updatableIds.toLongArray(),
        skippedLocalCount = localIds.size,
    )
}
