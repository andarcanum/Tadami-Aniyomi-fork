package tachiyomi.domain.series.manga.model

import tachiyomi.domain.entries.manga.model.Manga
import java.io.File

sealed interface MangaSeriesCover {
    data class Entry(val manga: Manga) : MangaSeriesCover
    data class Custom(val file: File, val lastModified: Long) : MangaSeriesCover
    data object Auto : MangaSeriesCover
}
