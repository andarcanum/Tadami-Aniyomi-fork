package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * A basic interface for creating a novel source. It could be an online source, a local source, etc.
 */
interface NovelSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Get the updated details for a novel.
     *
     * @since extensions-lib 1.5
     * @param novel the novel to update.
     * @return the updated novel.
     */
    @Suppress("DEPRECATION")
    suspend fun getNovelDetails(novel: SNovel): SNovel {
        return fetchNovelDetails(novel).awaitSingle()
    }

    /**
     * Get all the available chapters for a novel.
     *
     * @since extensions-lib 1.5
     * @param novel the novel to update.
     * @return the chapters for the novel.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        return fetchChapterList(novel).awaitSingle()
    }

    /**
     * Get chapter text for a novel chapter.
     *
     * @since extensions-lib 1.5
     * @param chapter the chapter.
     * @return the chapter HTML/text.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterText(chapter: SNovelChapter): String {
        return fetchChapterText(chapter).awaitSingle()
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getNovelDetails"),
    )
    fun fetchNovelDetails(novel: SNovel): Observable<SNovel> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getChapterList"),
    )
    fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getChapterText"),
    )
    fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
        throw IllegalStateException("Not used")
}
