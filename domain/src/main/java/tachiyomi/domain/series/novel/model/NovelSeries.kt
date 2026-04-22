package tachiyomi.domain.series.novel.model

import tachiyomi.domain.series.model.SeriesCoverMode
import java.io.Serializable

data class NovelSeries(
    val id: Long,
    val title: String,
    val description: String?,
    val categoryId: Long,
    val sortOrder: Long,
    val dateAdded: Long,
    val coverLastModified: Long,
    val pinned: Boolean = false,
    val coverMode: SeriesCoverMode = SeriesCoverMode.AUTO,
    val coverEntryId: Long? = null,
) : Serializable
