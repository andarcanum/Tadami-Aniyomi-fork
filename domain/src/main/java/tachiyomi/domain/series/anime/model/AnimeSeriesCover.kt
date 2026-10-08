package tachiyomi.domain.series.anime.model

import tachiyomi.domain.entries.anime.model.Anime
import java.io.File

sealed interface AnimeSeriesCover {
    data class Entry(val anime: Anime) : AnimeSeriesCover
    data class Custom(val file: File, val lastModified: Long) : AnimeSeriesCover
    data object Auto : AnimeSeriesCover
}
