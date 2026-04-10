package eu.kanade.tachiyomi.data.download.novel

import kotlinx.serialization.Serializable

@Serializable
data class NovelDiskCache(
    val data: Map<Long, Set<Long>> = emptyMap(),
)
