package tachiyomi.domain.series.manga.model

import java.io.Serializable

data class MangaSeries(
    val id: Long,
    val title: String,
    val description: String?,
    val categoryId: Long,
    val sortOrder: Long,
    val dateAdded: Long,
    val coverLastModified: Long,
) : Serializable
