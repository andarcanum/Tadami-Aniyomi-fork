package eu.kanade.tachiyomi.ui.series.novel

import tachiyomi.domain.series.novel.model.NovelSeriesEntry

fun buildNovelSeriesEntries(
    seriesId: Long,
    novelIds: List<Long>,
    entryIdsByNovelId: Map<Long, Long>,
): List<NovelSeriesEntry> {
    return novelIds
        .mapNotNull { novelId ->
            entryIdsByNovelId[novelId]?.let { entryId ->
                novelId to entryId
            }
        }
        .mapIndexed { position, (novelId, entryId) ->
            NovelSeriesEntry(
                id = entryId,
                seriesId = seriesId,
                novelId = novelId,
                position = position,
            )
        }
}
