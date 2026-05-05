package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.SizeResolver
import okhttp3.Call
import okhttp3.Request
import java.io.IOException

data class NovelReaderRefererImage(
    val url: String,
    val referer: String,
)

class NovelReaderRefererImageKeyer : Keyer<NovelReaderRefererImage> {
    override fun key(data: NovelReaderRefererImage, options: Options): String {
        return "novel-reader-img;${data.url};${data.referer}"
    }
}

class NovelReaderRefererImageFetcher(
    private val data: NovelReaderRefererImage,
    private val options: Options,
    private val callFactory: Lazy<Call.Factory>,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val request = Request.Builder()
            .url(data.url)
            .header("Referer", data.referer.trimEnd('/') + "/")
            .build()

        val response = callFactory.value.newCall(request).execute()
        val body = response.body ?: throw IOException("Empty body")
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

        return SourceFetchResult(
            source = ImageSource(
                source = body.source(),
                fileSystem = options.fileSystem,
            ),
            mimeType = body.contentType()?.toString() ?: "image/*",
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<NovelReaderRefererImage> {
        override fun create(
            data: NovelReaderRefererImage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return NovelReaderRefererImageFetcher(
                data = data,
                options = options,
                callFactory = callFactoryLazy,
            )
        }
    }
}
