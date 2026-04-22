package tachiyomi.data.series.novel

import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.domain.series.novel.model.NovelSeries
import tachiyomi.domain.series.novel.model.NovelSeriesEntry

val novelSeriesMapper: (Long, String, String?, Long, Long, Long, Long, Boolean, Long, Long?) -> NovelSeries =
    { id, title, description, categoryId, sortOrder, dateAdded, coverLastModified, pinned, coverMode, coverEntryId ->
        NovelSeries(
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

val novelSeriesEntryMapper: (Long, Long, Long, Long) -> NovelSeriesEntry =
    { id, seriesId, novelId, position ->
        NovelSeriesEntry(
            id = id,
            seriesId = seriesId,
            novelId = novelId,
            position = position.toInt(),
        )
    }
