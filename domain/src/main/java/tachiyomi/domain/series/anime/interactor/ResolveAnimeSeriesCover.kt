package tachiyomi.domain.series.anime.interactor

import tachiyomi.domain.series.anime.model.AnimeSeriesCover
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import tachiyomi.domain.series.model.SeriesCoverMode
import java.io.File

class ResolveAnimeSeriesCover(
    private val getCustomCoverFile: (seriesId: Long) -> File?,
    private val customCoverExists: (File) -> Boolean = { it.exists() },
) {
    operator fun invoke(series: LibraryAnimeSeries): AnimeSeriesCover {
        return when (series.series.coverMode) {
            SeriesCoverMode.AUTO -> AnimeSeriesCover.Auto
            SeriesCoverMode.ENTRY -> {
                val selected = series.entries
                    .firstOrNull { it.anime.id == series.series.coverEntryId }
                    ?.anime
                if (selected != null) AnimeSeriesCover.Entry(selected) else AnimeSeriesCover.Auto
            }
            SeriesCoverMode.CUSTOM -> {
                val file = getCustomCoverFile(series.id)
                if (file != null && customCoverExists(file)) {
                    AnimeSeriesCover.Custom(file, file.lastModified())
                } else {
                    AnimeSeriesCover.Auto
                }
            }
        }
    }
}
