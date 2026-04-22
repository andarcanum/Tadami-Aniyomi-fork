package tachiyomi.domain.series.novel.model

import tachiyomi.domain.entries.novel.model.Novel
import java.io.File

sealed interface NovelSeriesCover {
    data class Entry(val novel: Novel) : NovelSeriesCover
    data class Custom(val file: File, val lastModified: Long) : NovelSeriesCover
    data object Auto : NovelSeriesCover
}
