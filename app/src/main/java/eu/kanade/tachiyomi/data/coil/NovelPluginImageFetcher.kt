package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import okio.Buffer
import java.io.IOException

class NovelPluginImageFetcher(
    private val data: NovelPluginImage,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val resolved = NovelPluginImageResolver.resolve(data.url)
            ?: throw IOException("Failed to resolve plugin image: ${data.url}")
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(resolved.bytes),
                fileSystem = options.fileSystem,
            ),
            mimeType = resolved.mimeType,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<NovelPluginImage> {
        override fun create(
            data: NovelPluginImage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return NovelPluginImageFetcher(data, options)
        }
    }
}
