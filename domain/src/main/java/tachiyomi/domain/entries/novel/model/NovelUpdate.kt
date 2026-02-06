package tachiyomi.domain.entries.novel.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy

data class NovelUpdate(
    val id: Long,
    val source: Long? = null,
    val favorite: Boolean? = null,
    val lastUpdate: Long? = null,
    val nextUpdate: Long? = null,
    val fetchInterval: Int? = null,
    val dateAdded: Long? = null,
    val viewerFlags: Long? = null,
    val chapterFlags: Long? = null,
    val coverLastModified: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long? = null,
    val thumbnailUrl: String? = null,
    val updateStrategy: UpdateStrategy? = null,
    val initialized: Boolean? = null,
    val version: Long? = null,
)

fun Novel.toNovelUpdate(): NovelUpdate {
    return NovelUpdate(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate,
        nextUpdate = nextUpdate,
        fetchInterval = fetchInterval,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        chapterFlags = chapterFlags,
        coverLastModified = coverLastModified,
        url = url,
        title = title,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
        updateStrategy = updateStrategy,
        initialized = initialized,
        version = version,
    )
}