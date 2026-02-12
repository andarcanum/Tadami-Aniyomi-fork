package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle

interface NovelCatalogueSource : NovelSource {

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
