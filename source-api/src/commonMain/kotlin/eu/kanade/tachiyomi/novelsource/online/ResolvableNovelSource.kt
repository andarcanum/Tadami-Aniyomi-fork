package eu.kanade.tachiyomi.novelsource.online

import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter

/**
 * A source that may handle opening an SNovel or SNovelChapter for a given URI.
 *
 * @since extensions-lib 1.5
 */
interface ResolvableNovelSource : NovelSource {

    /**
     * Returns what the given URI may open.
     * Returns [UriType.Unknown] if the source is not able to resolve the URI.
     *
     * @since extensions-lib 1.5
     */
    fun getUriType(uri: String): UriType

    /**
     * Called if [getUriType] is [UriType.Novel].
     *
     * @since extensions-lib 1.5
     */
    suspend fun getNovel(uri: String): SNovel?

    /**
     * Called if [getUriType] is [UriType.Chapter].
     *
     * @since extensions-lib 1.5
     */
    suspend fun getChapter(uri: String): SNovelChapter?
}

sealed interface UriType {
    data object Novel : UriType
    data object Chapter : UriType
    data object Unknown : UriType
}
