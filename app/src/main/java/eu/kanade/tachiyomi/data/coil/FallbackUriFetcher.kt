package eu.kanade.tachiyomi.data.coil

import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.network.await
import okhttp3.Call
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException

/**
 * Fallback fetcher for basic URI models to guard against missing default Coil fetchers.
 */
class FallbackUriFetcher(
    private val data: Uri,
    private val options: Options,
    private val callFactoryLazy: Lazy<Call.Factory>,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val uri = data.toString().trim()
        return when {
            uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) ->
                httpLoader(uri)
            uri.startsWith("file://", ignoreCase = true) ->
                fileLoader(File(uri.substringAfter("file://")))
            uri.startsWith("content://", ignoreCase = true) ->
                contentLoader(uri)
            uri.startsWith("/") ->
                fileLoader(File(uri))
            else -> error("Unsupported URI: $uri")
        }
    }

    private suspend fun httpLoader(url: String): FetchResult {
        val response = callFactoryLazy.value.newCall(Request.Builder().url(url).build()).await()
        if (!response.isSuccessful) {
            response.close()
            throw IOException(response.message)
        }
        val body = checkNotNull(response.body) { "Null response source" }
        return SourceFetchResult(
            source = ImageSource(
                source = body.source(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = response.header("Content-Type"),
            dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
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

    private fun contentLoader(uri: String): FetchResult {
        val uniFile = UniFile.fromUri(options.context, uri.toUri())!!
        return SourceFetchResult(
            source = ImageSource(
                source = uniFile.openInputStream().source().buffer(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Uri> {

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uri = data.toString().trim()
            val isSupported = uri.startsWith("http://", ignoreCase = true) ||
                uri.startsWith("https://", ignoreCase = true) ||
                uri.startsWith("file://", ignoreCase = true) ||
                uri.startsWith("content://", ignoreCase = true) ||
                uri.startsWith("/")
            if (!isSupported) return null
            return FallbackUriFetcher(
                data = data,
                options = options,
                callFactoryLazy = callFactoryLazy,
            )
        }
    }
}
