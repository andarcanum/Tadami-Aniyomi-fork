package eu.kanade.tachiyomi.source

/**
 * Marker interface kept for compatibility with Tsundoku-style Kotlin novel extensions.
 */
@Deprecated("Detection is via Source.isNovelSource; fetchPageText is on Source")
interface NovelSource : Source

fun Source.isNovelSource(): Boolean = isNovelSource
