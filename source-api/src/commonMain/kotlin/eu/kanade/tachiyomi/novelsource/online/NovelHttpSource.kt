package eu.kanade.tachiyomi.novelsource.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

/**
 * Marker and contract interface for HTTP-based novel sources.
 */
interface NovelHttpSource : NovelCatalogueSource {
    val baseUrl: String get() = ""
    val headers: Headers get() = Headers.Builder().build()
}

/**
 * A standard implementation for novel sources from a website.
 */
@Suppress("unused")
abstract class HttpNovelSource : NovelHttpSource {

    /**
     * Network service.
     */
    protected val network: NetworkHelper by injectLazy()

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract override val baseUrl: String

    /**
     * Returns the base (home) URL of the website as a string.
     *
     * Used in the browse screen to determine the URL opened when tapping "Open in WebView".
     *
     * @since extensions-lib 1.6
     * @return the website's home page URL. Defaults to [baseUrl].
     */
    open fun getHomeUrl(): String = baseUrl

    /**
     * Version id used to generate the source id.
     */
    open val versionId = 1

    /**
     * ID of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string `"${name.lowercase()}/$lang/$versionId"`.
     */
    override val id: Long by lazy { generateId(name, lang, versionId) }

    /**
     * Headers used for requests.
     */
    override val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient
        get() = runCatching { network.client }.getOrElse { OkHttpClient() }

    /**
     * Generates a unique ID for the source based on the provided [name], [lang] and [versionId].
     */
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    protected open fun headersBuilder() = Headers.Builder().apply {
        val userAgent = runCatching { network.defaultUserAgentProvider() }
            .getOrDefault("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        add("User-Agent", userAgent)
    }

    override fun toString() = "$name (${lang.uppercase()})"

    @Suppress("DEPRECATION")
    override suspend fun getPopularNovels(page: Int): NovelsPage {
        return client.newCall(popularNovelsRequest(page))
            .awaitSuccess()
            .use { response -> popularNovelsParse(response) }
    }

    open fun popularNovelsRequest(page: Int): Request = throw UnsupportedOperationException()

    open fun popularNovelsParse(response: Response): NovelsPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return client.newCall(searchNovelsRequest(page, query, filters))
            .awaitSuccess()
            .use { response -> searchNovelsParse(response) }
    }

    open fun searchNovelsRequest(page: Int, query: String, filters: NovelFilterList): Request =
        throw UnsupportedOperationException()

    open fun searchNovelsParse(response: Response): NovelsPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getLatestUpdates(page: Int): NovelsPage {
        return client.newCall(latestUpdatesRequest(page))
            .awaitSuccess()
            .use { response -> latestUpdatesParse(response) }
    }

    open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    open fun latestUpdatesParse(response: Response): NovelsPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        return client.newCall(novelDetailsRequest(novel))
            .awaitSuccess()
            .use { response -> novelDetailsParse(response).apply { initialized = true } }
    }

    open fun novelDetailsRequest(novel: SNovel): Request {
        return GET(baseUrl + novel.url, headers)
    }

    open fun novelDetailsParse(response: Response): SNovel = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        return client.newCall(chapterListRequest(novel))
            .awaitSuccess()
            .use { response -> chapterListParse(response) }
    }

    open fun chapterListRequest(novel: SNovel): Request {
        return GET(baseUrl + novel.url, headers)
    }

    open fun chapterListParse(response: Response): List<SNovelChapter> = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    override suspend fun getChapterText(chapter: SNovelChapter): String {
        return client.newCall(chapterTextRequest(chapter))
            .awaitSuccess()
            .use { response -> chapterTextParse(response) }
    }

    open fun chapterTextRequest(chapter: SNovelChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    open fun chapterTextParse(response: Response): String = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularNovels"))
    override fun fetchPopularNovels(page: Int): Observable<NovelsPage> {
        return client.newCall(popularNovelsRequest(page))
            .asObservableSuccess()
            .map { response -> popularNovelsParse(response) }
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchNovels"))
    override fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage> {
        return client.newCall(searchNovelsRequest(page, query, filters))
            .asObservableSuccess()
            .map { response -> searchNovelsParse(response) }
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<NovelsPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response -> latestUpdatesParse(response) }
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getNovelDetails"))
    override fun fetchNovelDetails(novel: SNovel): Observable<SNovel> {
        return client.newCall(novelDetailsRequest(novel))
            .asObservableSuccess()
            .map { response -> novelDetailsParse(response).apply { initialized = true } }
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getChapterList"))
    override fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> {
        return client.newCall(chapterListRequest(novel))
            .asObservableSuccess()
            .map { response -> chapterListParse(response) }
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getChapterText"))
    override fun fetchChapterText(chapter: SNovelChapter): Observable<String> {
        return client.newCall(chapterTextRequest(chapter))
            .asObservableSuccess()
            .map { response -> chapterTextParse(response) }
    }
}
