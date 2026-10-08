package tachiyomi.domain.series.anime.model

import java.io.Serializable

data class AnimeSeriesEntry(
    val id: Long,
    val seriesId: Long,
    val animeId: Long,
    val position: Int,
) : Serializable
