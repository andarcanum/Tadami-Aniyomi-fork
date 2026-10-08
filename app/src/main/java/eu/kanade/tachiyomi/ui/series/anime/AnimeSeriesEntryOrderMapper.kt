package eu.kanade.tachiyomi.ui.series.anime

import tachiyomi.domain.series.anime.model.AnimeSeriesEntry

fun buildAnimeSeriesEntries(
    seriesId: Long,
    animeIds: List<Long>,
    entryIdsByAnimeId: Map<Long, Long>,
): List<AnimeSeriesEntry> {
    return animeIds
        .mapNotNull { animeId ->
            entryIdsByAnimeId[animeId]?.let { entryId ->
                animeId to entryId
            }
        }
        .mapIndexed { position, (animeId, entryId) ->
            AnimeSeriesEntry(
                id = entryId,
                seriesId = seriesId,
                animeId = animeId,
                position = position,
            )
        }
}
