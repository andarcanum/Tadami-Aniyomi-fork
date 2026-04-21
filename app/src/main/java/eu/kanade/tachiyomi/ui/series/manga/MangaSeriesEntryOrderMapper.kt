package eu.kanade.tachiyomi.ui.series.manga

import tachiyomi.domain.series.manga.model.MangaSeriesEntry

fun buildMangaSeriesEntries(
    seriesId: Long,
    mangaIds: List<Long>,
    entryIdsByMangaId: Map<Long, Long>,
): List<MangaSeriesEntry> {
    return mangaIds
        .mapNotNull { mangaId ->
            entryIdsByMangaId[mangaId]?.let { entryId ->
                mangaId to entryId
            }
        }
        .mapIndexed { position, (mangaId, entryId) ->
            MangaSeriesEntry(
                id = entryId,
                seriesId = seriesId,
                mangaId = mangaId,
                position = position,
            )
        }
}
