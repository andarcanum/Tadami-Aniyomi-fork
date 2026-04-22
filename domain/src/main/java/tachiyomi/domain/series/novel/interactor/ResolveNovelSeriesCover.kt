package tachiyomi.domain.series.novel.interactor

import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.series.novel.model.NovelSeriesCover
import java.io.File

class ResolveNovelSeriesCover(
    private val getCustomCoverFile: (seriesId: Long) -> File?,
    private val customCoverExists: (File) -> Boolean = { it.exists() },
) {
    operator fun invoke(series: LibraryNovelSeries): NovelSeriesCover {
        return when (series.series.coverMode) {
            SeriesCoverMode.AUTO -> NovelSeriesCover.Auto
            SeriesCoverMode.ENTRY -> {
                val selected = series.entries
                    .firstOrNull { it.novel.id == series.series.coverEntryId }
                    ?.novel
                if (selected != null) NovelSeriesCover.Entry(selected) else NovelSeriesCover.Auto
            }
            SeriesCoverMode.CUSTOM -> {
                val file = getCustomCoverFile(series.id)
                if (file != null && customCoverExists(file)) {
                    NovelSeriesCover.Custom(file, file.lastModified())
                } else {
                    NovelSeriesCover.Auto
                }
            }
        }
    }
}
