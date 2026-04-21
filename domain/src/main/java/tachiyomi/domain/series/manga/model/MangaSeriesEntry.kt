package tachiyomi.domain.series.manga.model

import java.io.Serializable

data class MangaSeriesEntry(
    val id: Long,
    val seriesId: Long,
    val mangaId: Long,
    val position: Int,
) : Serializable
