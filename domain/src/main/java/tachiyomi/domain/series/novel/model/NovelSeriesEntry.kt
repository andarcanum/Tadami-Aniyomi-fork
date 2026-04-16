package tachiyomi.domain.series.novel.model

import java.io.Serializable

data class NovelSeriesEntry(
    val id: Long,
    val seriesId: Long,
    val novelId: Long,
    val position: Int,
) : Serializable
