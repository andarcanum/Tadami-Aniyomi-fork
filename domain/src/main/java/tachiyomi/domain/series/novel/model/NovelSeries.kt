package tachiyomi.domain.series.novel.model

import java.io.Serializable

data class NovelSeries(
    val id: Long,
    val title: String,
    val description: String?,
    val categoryId: Long,
    val sortOrder: Long,
    val dateAdded: Long,
    val coverLastModified: Long,
) : Serializable
