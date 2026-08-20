package eu.kanade.tachiyomi.novelsource.model

/**
 * Result of a combined novel update request: refreshed details and/or chapters.
 *
 * @since extensions-lib 1.6
 */
@Suppress("UNUSED")
class SNovelUpdate(val novel: SNovel, val chapters: List<SNovelChapter>)
