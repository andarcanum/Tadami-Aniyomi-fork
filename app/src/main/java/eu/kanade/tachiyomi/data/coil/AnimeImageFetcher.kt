package eu.kanade.tachiyomi.data.coil

import androidx.core.net.toUri
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.getOrDefault
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.coil.AnimeImageFetcher.Companion.USE_CUSTOM_COVER_KEY
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

/**
 * A [Fetcher] that fetches cover image for [Anime] object.
 *
 * It uses [Anime.thumbnailUrl] if custom cover is not set by the user.
 * Disk caching for library items is handled by [AnimeCoverCache], otherwise
 * handled by Coil's [DiskCache].
 *
 * Available request parameter:
 * - [USE_CUSTOM_COVER_KEY]: Use custom cover if set by user, default is true
 */
class AnimeImageFetcher(
    private val url: String?,
    private val isLibraryAnime: Boolean,
    private val options: Options,
    private val coverFileProvider: (String?) -> File?,
    private val customCoverFileLazy: Lazy<File>,
    private val diskCacheKeyProvider: (String?) -> String,
    private val metadataCoverUrlProvider: suspend () -> String? = { null },
    private val sourceLazy: Lazy<AnimeHttpSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Use custom cover if exists
        val useCustomCover = options.extras.getOrDefault(USE_CUSTOM_COVER_KEY)
        if (useCustomCover) {
            val customCoverFile = customCoverFileLazy.value
            if (customCoverFile.exists()) {
                val diskCacheKey = diskCacheKeyProvider(url)
                debugTitleCoverFlow(scope = "anime-fetcher", message = "custom-cover-hit file=${customCoverFile.name}")
                return fileLoader(customCoverFile, diskCacheKey)
            }
        }

        val effectiveUrl = metadataCoverUrlProvider()?.takeIf { it.isNotBlank() } ?: url
        val diskCacheKey = diskCacheKeyProvider(effectiveUrl)
        debugTitleCoverFlow(scope = "anime-fetcher") {
            "fetch url=${previewTitleCoverUrl(url)} effectiveUrl=${previewTitleCoverUrl(effectiveUrl)} " +
                "diskCacheKey=$diskCacheKey useCustomCover=$useCustomCover " +
                "isLibrary=$isLibraryAnime useBackground=${options.useBackground}"
        }

        if (effectiveUrl == null) error("No cover specified")
        return when (getResourceType(effectiveUrl)) {
            Type.URL -> httpLoader(effectiveUrl, diskCacheKey)
            Type.File -> fileLoader(File(effectiveUrl.substringAfter("file://")), diskCacheKey)
            Type.URI -> uniFileLoader(effectiveUrl)
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

    private fun fileLoader(file: File, diskCacheKey: String): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = diskCacheKey,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(url: String, diskCacheKey: String): FetchResult {
        // Only cache separately if it's a library item
        val libraryCoverCacheFile = if (isLibraryAnime) {
            coverFileProvider(url) ?: error("No cover specified")
        } else {
            null
        }
        if (libraryCoverCacheFile?.exists() == true && options.diskCachePolicy.readEnabled) {
            debugTitleCoverFlow(
                scope = "anime-fetcher",
                message = "library-cache-hit file=${libraryCoverCacheFile.name}",
            )
            return fileLoader(libraryCoverCacheFile, diskCacheKey)
        }

        var snapshot = readFromDiskCache(diskCacheKey)
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                debugTitleCoverFlow(scope = "anime-fetcher", message = "disk-cache-hit key=$diskCacheKey")
                val snapshotCoverCache = moveSnapshotToCoverCache(snapshot, libraryCoverCacheFile, diskCacheKey)
                if (snapshotCoverCache != null) {
                    // Read from cover cache after added to library
                    debugTitleCoverFlow(
                        scope = "anime-fetcher",
                        message = "snapshot-moved-to-library-cache file=${snapshotCoverCache.name}",
                    )
                    return fileLoader(snapshotCoverCache, diskCacheKey)
                }

                // Read from snapshot
                return SourceFetchResult(
                    source = snapshot.toImageSource(diskCacheKey),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            // Fetch from network
            debugTitleCoverFlow(
                scope = "anime-fetcher",
                message = "network-fetch url=${previewTitleCoverUrl(url)} key=$diskCacheKey",
            )
            val response = executeNetworkRequest(url)
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                // Read from cover cache after library manga cover updated
                val responseCoverCache = writeResponseToCoverCache(response, libraryCoverCacheFile)
                if (responseCoverCache != null) {
                    debugTitleCoverFlow(
                        scope = "anime-fetcher",
                        message = "network-response-written-to-library-cache file=${responseCoverCache.name}",
                    )
                    return fileLoader(responseCoverCache, diskCacheKey)
                }

                // Read from disk cache
                snapshot = writeToDiskCache(response, diskCacheKey)
                if (snapshot != null) {
                    debugTitleCoverFlow(
                        scope = "anime-fetcher",
                        message = "network-response-written-to-disk-cache key=$diskCacheKey",
                    )
                    return SourceFetchResult(
                        source = snapshot.toImageSource(diskCacheKey),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                // Read from response if cache is unused or unusable
                return SourceFetchResult(
                    source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.close()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(url: String): Response {
        val client = sourceLazy.value?.client ?: callFactoryLazy.value
        val response = client.newCall(newRequest(url)).await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private fun newRequest(url: String): Request {
        val request = Request.Builder().apply {
            url(url)

            val source = sourceLazy.value
            val sourceHeaders = source?.headers
            if (sourceHeaders != null) {
                headers(sourceHeaders)
            }
            if (source?.baseUrl != null && sourceHeaders?.get("Referer").isNullOrBlank()) {
                addHeader("Referer", source.baseUrl)
            }
        }

        when {
            options.networkCachePolicy.readEnabled -> {
                // don't take up okhttp cache
                request.cacheControl(CACHE_CONTROL_NO_STORE)
            }
            else -> {
                // This causes the request to fail with a 504 Unsatisfiable Request.
                request.cacheControl(CACHE_CONTROL_NO_NETWORK_NO_CACHE)
            }
        }

        return request.build()
    }

    private fun moveSnapshotToCoverCache(snapshot: DiskCache.Snapshot, cacheFile: File?, diskCacheKey: String): File? {
        if (cacheFile == null) return null
        return try {
            imageLoader.diskCache?.run {
                fileSystem.source(snapshot.data).use { input ->
                    writeSourceToCoverCache(input, cacheFile)
                }
                remove(diskCacheKey)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeResponseToCoverCache(response: Response, cacheFile: File?): File? {
        if (cacheFile == null || !options.diskCachePolicy.writeEnabled) return null
        return try {
            response.peekBody(Long.MAX_VALUE).source().use { input ->
                writeSourceToCoverCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write response data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeSourceToCoverCache(input: Source, cacheFile: File) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.delete()
        try {
            cacheFile.sink().buffer().use { output ->
                output.writeAll(input)
            }
        } catch (e: Exception) {
            cacheFile.delete()
            throw e
        }
    }

    private fun readFromDiskCache(diskCacheKey: String): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            imageLoader.diskCache?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun writeToDiskCache(
        response: Response,
        diskCacheKey: String,
    ): DiskCache.Snapshot? {
        val diskCache = imageLoader.diskCache
        val editor = diskCache?.openEditor(diskCacheKey) ?: return null
        try {
            diskCache.fileSystem.write(editor.data) {
                response.body.source().readAll(this)
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(diskCacheKey: String): ImageSource {
        return ImageSource(
            file = data,
            fileSystem = FileSystem.SYSTEM,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
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

    class AnimeFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Anime> {

        private val coverCache: AnimeCoverCache by injectLazy()
        private val backgroundCache: AnimeBackgroundCache by injectLazy()
        private val sourceManager: AnimeSourceManager by injectLazy()
        private val metadataCoverResolver: MetadataCoverResolver by injectLazy()

        override fun create(data: Anime, options: Options, imageLoader: ImageLoader): Fetcher {
            val isBackground = options.useBackground
            val url = if (isBackground) data.backgroundUrl else data.thumbnailUrl

            val customCoverCacheLazy = if (isBackground) {
                lazy { backgroundCache.getCustomBackgroundFile(data.id) }
            } else {
                lazy { coverCache.getCustomCoverFile(data.id) }
            }

            return AnimeImageFetcher(
                url = url,
                isLibraryAnime = data.favorite,
                options = options,
                coverFileProvider = { effectiveUrl ->
                    if (isBackground) {
                        backgroundCache.getBackgroundFile(
                            effectiveUrl,
                        )
                    } else {
                        coverCache.getCoverFile(effectiveUrl)
                    }
                },
                customCoverFileLazy = customCoverCacheLazy,
                diskCacheKeyProvider = { effectiveUrl ->
                    if (isBackground) {
                        "anime-bg;${data.id};$effectiveUrl;${data.backgroundLastModified}"
                    } else {
                        "anime;${data.id};$effectiveUrl;${data.coverLastModified}"
                    }
                },
                metadataCoverUrlProvider = {
                    if (isBackground) null else metadataCoverResolver.resolveAnimeCoverUrl(data.id)
                },
                sourceLazy = lazy { sourceManager.get(data.source) as? AnimeHttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    class AnimeCoverFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<AnimeCover> {

        private val coverCache: AnimeCoverCache by injectLazy()
        private val sourceManager: AnimeSourceManager by injectLazy()
        private val metadataCoverResolver: MetadataCoverResolver by injectLazy()

        override fun create(data: AnimeCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return AnimeImageFetcher(
                url = data.url,
                isLibraryAnime = data.isAnimeFavorite,
                options = options,
                coverFileProvider = coverCache::getCoverFile,
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.animeId) },
                diskCacheKeyProvider = { effectiveUrl -> "anime;${data.animeId};$effectiveUrl;${data.lastModified}" },
                metadataCoverUrlProvider = { metadataCoverResolver.resolveAnimeCoverUrl(data.animeId) },
                sourceLazy = lazy { sourceManager.get(data.sourceId) as? AnimeHttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    companion object {
        val USE_CUSTOM_COVER_KEY = Extras.Key(true)

        private val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
        private val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()

        private const val HTTP_NOT_MODIFIED = 304
    }
}
