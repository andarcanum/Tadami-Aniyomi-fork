package eu.kanade.tachiyomi.data.coil

import androidx.core.net.toUri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.source
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

class NovelCoverFetcher(
    private val data: NovelCover,
    private val options: Options,
    private val sourceSiteUrlLazy: Lazy<String?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val url = data.url ?: error("No cover specified")
        return when (getResourceType(url)) {
            Type.URL -> httpLoader(url)
            Type.File -> fileLoader(File(url.substringAfter("file://")))
            Type.URI -> uniFileLoader(url)
            null -> error("Invalid image")
        }
    }

    private fun uniFileLoader(urlString: String): FetchResult {
        val uniFile = UniFile.fromUri(options.context, urlString.toUri())!!
        val tempFile = uniFile.openInputStream().source().buffer()
        return SourceFetchResult(
            source = ImageSource(source = tempFile, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(url: String): FetchResult {
        val response = executeNetworkRequest(url)
        val responseBody = checkNotNull(response.body) { "Null response source" }
        return SourceFetchResult(
            source = ImageSource(
                source = responseBody.source(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = "image/*",
            dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
        )
    }

    private suspend fun executeNetworkRequest(url: String): Response {
        val response = callFactoryLazy.value
            .newCall(
                buildNovelCoverRequest(
                    url = url,
                    siteUrl = sourceSiteUrlLazy.value,
                    readFromNetwork = options.networkCachePolicy.readEnabled,
                ),
            )
            .await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private fun getResourceType(cover: String?): Type? {
        return when {
            cover.isNullOrEmpty() -> null
            cover.startsWith("http", true) || cover.startsWith("Custom-", true) -> Type.URL
            cover.startsWith("/") || cover.startsWith("file://") -> Type.File
            cover.startsWith("content") -> Type.URI
            else -> null
        }
    }

    private enum class Type {
        File,
        URL,
        URI,
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<NovelCover> {

        private val sourceManager: NovelSourceManager by injectLazy()

        override fun create(data: NovelCover, options: Options, imageLoader: coil3.ImageLoader): Fetcher {
            return NovelCoverFetcher(
                data = data,
                options = options,
                sourceSiteUrlLazy = lazy { (sourceManager.get(data.sourceId) as? NovelSiteSource)?.siteUrl },
                callFactoryLazy = callFactoryLazy,
            )
        }
    }

    companion object {
        private const val HTTP_NOT_MODIFIED = 304
    }
}

internal fun buildNovelCoverRequest(
    url: String,
    siteUrl: String?,
    readFromNetwork: Boolean,
): Request {
    val normalizedSiteUrl = siteUrl
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.trimEnd('/')

    return Request.Builder()
        .url(url)
        .apply {
            if (normalizedSiteUrl != null) {
                addHeader("Referer", "$normalizedSiteUrl/")
                addHeader("Origin", normalizedSiteUrl)
            }
            if (readFromNetwork) {
                cacheControl(CACHE_CONTROL_NO_STORE)
            } else {
                cacheControl(CACHE_CONTROL_NO_NETWORK_NO_CACHE)
            }
        }
        .build()
}

private val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()
