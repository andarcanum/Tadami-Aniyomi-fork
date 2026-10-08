package tachiyomi.data.series.anime

import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.AnimeSeriesEntry
import tachiyomi.domain.series.model.SeriesCoverMode

val animeSeriesMapper: (Long, String, String?, Long, Long, Long, Long, Boolean, Long, Long?) -> AnimeSeries =
    { id, title, description, categoryId, sortOrder, dateAdded, coverLastModified, pinned, coverMode, coverEntryId ->
        AnimeSeries(
            id = id,
            title = title,
            description = description,
            categoryId = categoryId,
            sortOrder = sortOrder,
            dateAdded = dateAdded,
            coverLastModified = coverLastModified,
            pinned = pinned,
            coverMode = SeriesCoverMode.from(coverMode),
            coverEntryId = coverEntryId,
        )
    }

val animeSeriesEntryMapper: (Long, Long, Long, Long) -> AnimeSeriesEntry =
    { id, seriesId, animeId, position ->
        AnimeSeriesEntry(
            id = id,
            seriesId = seriesId,
            animeId = animeId,
            position = position.toInt(),
        )
    }
