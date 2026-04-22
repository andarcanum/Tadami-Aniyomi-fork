package tachiyomi.domain.series.manga.interactor

import tachiyomi.domain.series.manga.model.LibraryMangaSeries
import tachiyomi.domain.series.manga.model.MangaSeriesCover
import tachiyomi.domain.series.model.SeriesCoverMode
import java.io.File

class ResolveMangaSeriesCover(
    private val getCustomCoverFile: (seriesId: Long) -> File?,
    private val customCoverExists: (File) -> Boolean = { it.exists() },
) {
    operator fun invoke(series: LibraryMangaSeries): MangaSeriesCover {
        return when (series.series.coverMode) {
            SeriesCoverMode.AUTO -> MangaSeriesCover.Auto
            SeriesCoverMode.ENTRY -> {
                val selected = series.entries
                    .firstOrNull { it.manga.id == series.series.coverEntryId }
                    ?.manga
                if (selected != null) MangaSeriesCover.Entry(selected) else MangaSeriesCover.Auto
            }
            SeriesCoverMode.CUSTOM -> {
                val file = getCustomCoverFile(series.id)
                if (file != null && customCoverExists(file)) {
                    MangaSeriesCover.Custom(file, file.lastModified())
                } else {
                    MangaSeriesCover.Auto
                }
            }
        }
    }
}
