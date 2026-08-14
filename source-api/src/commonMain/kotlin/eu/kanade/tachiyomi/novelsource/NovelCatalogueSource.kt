package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.model.SNovelUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle

interface NovelCatalogueSource : NovelSource {

    val supportsRelatedNovels: Boolean get() = false

    suspend fun getRelatedNovels(novel: SNovel): List<SNovel> {
        return emptyList()
    }

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Get a page with a list of novels.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    @Suppress("DEPRECATION")
    suspend fun getPopularNovels(page: Int): NovelsPage {
        return fetchPopularNovels(page).awaitSingle()
    }

    /**
     * Get a page with a list of novels while applying source filters.
     *
     * By default sources that don't support filtered popular/latest can ignore filters.
     */
    suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        return getPopularNovels(page)
    }

    /**
     * Get a page with a list of novels.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Suppress("DEPRECATION")
    suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return fetchSearchNovels(page, query, filters).awaitSingle()
    }

    /**
     * Get a page with a list of latest novel updates.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    @Suppress("DEPRECATION")
    suspend fun getLatestUpdates(page: Int): NovelsPage {
        return fetchLatestUpdates(page).awaitSingle()
    }

    /**
     * Get a page with a list of latest novel updates while applying source filters.
     *
     * By default sources that don't support filtered popular/latest can ignore filters.
     */
    suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage {
        return getLatestUpdates(page)
    }

    /**
     * Fetches updated information for a novel.
     *
     * @since extensions-lib 1.6
     * @param novel the novel to fetch updates for.
     * @param chapters existing chapters of the novel.
     * @param fetchDetails whether to fetch updated novel details.
     * @param fetchChapters whether to fetch available chapters.
     */
    suspend fun getNovelUpdate(
        novel: SNovel,
        chapters: List<SNovelChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SNovelUpdate = supervisorScope {
        val asyncNovel = if (fetchDetails) async { getNovelDetails(novel) } else null
        val asyncChapters = if (fetchChapters) async { getChapterList(novel) } else null
        SNovelUpdate(asyncNovel?.await() ?: novel, asyncChapters?.await() ?: chapters)
    }

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): NovelFilterList

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularNovels"),
    )
    fun fetchPopularNovels(page: Int): Observable<NovelsPage>

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchNovels"),
    )
    fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage>

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    fun fetchLatestUpdates(page: Int): Observable<NovelsPage>
}
