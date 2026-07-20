package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImagePayload
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import kotlinx.coroutines.delay
import okio.Buffer
import java.io.IOException

internal suspend fun resolveNovelPluginImagePayload(
    url: String,
    attempts: Int = 3,
    retryDelayMillis: Long = 150L,
    resolver: suspend (String) -> NovelPluginImagePayload? = NovelPluginImageResolver::resolve,
): NovelPluginImagePayload? {
    repeat(attempts.coerceAtLeast(1)) { attemptIndex ->
        resolver(url)?.let { return it }
        if (attemptIndex < attempts - 1) {
            delay(retryDelayMillis)
        }
    }
    return null
}

class NovelPluginImageFetcher(
    private val data: NovelPluginImage,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        readPluginImageFromDiskCache(imageLoader, options, data.url)?.let { return it }
        val resolved = resolveNovelPluginImagePayload(data.url)
            ?: throw IOException("Failed to resolve plugin image: ${data.url}")
        writePluginImageToDiskCache(imageLoader, options, data.url, resolved.bytes, resolved.mimeType)?.let {
            return it
        }
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
            return NovelPluginImageFetcher(data, options, imageLoader)
        }
    }
}

/**
 * Plugin images are resolved through the plugin runtime (JS + network), which
 * is unavailable offline, so the resolved bytes are persisted in Coil's disk
 * cache keyed by the raw plugin image URL. NovelPluginImageFetcher and
 * NovelCoverFetcher share these entries.
 */
internal fun readPluginImageFromDiskCache(
    imageLoader: ImageLoader,
    options: Options,
    key: String,
): SourceFetchResult? {
    if (!options.diskCachePolicy.readEnabled) return null
    val diskCache = imageLoader.diskCache ?: return null
    val snapshot = runCatching { diskCache.openSnapshot(key) }.getOrNull() ?: return null
    return SourceFetchResult(
        source = ImageSource(
            file = snapshot.data,
            fileSystem = diskCache.fileSystem,
            diskCacheKey = key,
            closeable = snapshot,
        ),
        mimeType = "image/*",
        dataSource = DataSource.DISK,
    )
}

internal fun writePluginImageToDiskCache(
    imageLoader: ImageLoader,
    options: Options,
    key: String,
    bytes: ByteArray,
    mimeType: String?,
): SourceFetchResult? {
    if (!options.diskCachePolicy.writeEnabled) return null
    val diskCache = imageLoader.diskCache ?: return null
    val snapshot = runCatching {
        val editor = diskCache.openEditor(key) ?: return@runCatching null
        try {
            diskCache.fileSystem.write(editor.data) { write(bytes) }
            editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            runCatching { editor.abort() }
            null
        }
    }.getOrNull() ?: return null
    return SourceFetchResult(
        source = ImageSource(
            file = snapshot.data,
            fileSystem = diskCache.fileSystem,
            diskCacheKey = key,
            closeable = snapshot,
        ),
        mimeType = mimeType,
        dataSource = DataSource.NETWORK,
    )
}
