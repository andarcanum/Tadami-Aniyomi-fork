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
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher.Companion.USE_CUSTOM_COVER_KEY
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
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
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

/**
 * A [Fetcher] that fetches cover image for [Manga] object.
 *
 * It uses [Manga.thumbnailUrl] if custom cover is not set by the user.
 * Disk caching for library items is handled by [MangaCoverCache], otherwise
 * handled by Coil's [DiskCache].
 *
 * Available request parameter:
 * - [USE_CUSTOM_COVER_KEY]: Use custom cover if set by user, default is true
 */
class MangaCoverFetcher(
    private val url: String?,
    private val isLibraryManga: Boolean,
    private val options: Options,
    private val coverFileProvider: (String?) -> File?,
    private val customCoverFileLazy: Lazy<File>,
    private val diskCacheKeyProvider: (String?, Long?) -> String,
    private val metadataCoverUrlProvider: suspend () -> String? = { null },
    private val dbCoverProvider: suspend () -> Pair<String?, Long>? = { null },
    private val sourceLazy: Lazy<HttpSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Use custom cover if exists
        val useCustomCover = options.extras.getOrDefault(USE_CUSTOM_COVER_KEY)
        if (useCustomCover) {
            val customCoverFile = customCoverFileLazy.value
            if (customCoverFile.exists()) {
                val diskCacheKey = diskCacheKeyProvider(url, null)
                debugTitleCoverFlow(scope = "manga-fetcher", message = "custom-cover-hit file=${customCoverFile.name}")
                return fileLoader(customCoverFile, diskCacheKey)
            }
        }

        // Metadata covers are only maintained for library entries; skip the
        // per-cover metadata DB lookup for browse/search items.
        val metadataCoverUrl = if (isLibraryManga) {
            metadataCoverUrlProvider()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        var effectiveUrl = metadataCoverUrl ?: url
        var lastModified: Long? = null

        if (effectiveUrl.isNullOrBlank()) {
            val dbResult = dbCoverProvider()
            if (dbResult != null) {
                effectiveUrl = dbResult.first
                lastModified = dbResult.second
            }
        }

        val diskCacheKey = diskCacheKeyProvider(effectiveUrl, lastModified)
        debugTitleCoverFlow(scope = "manga-fetcher") {
            "fetch url=${previewTitleCoverUrl(url)} effectiveUrl=${previewTitleCoverUrl(effectiveUrl)} " +
                "diskCacheKey=$diskCacheKey useCustomCover=$useCustomCover isLibrary=$isLibraryManga"
        }

        if (effectiveUrl.isNullOrBlank()) error("No cover specified")
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
        // The cover cache file may exist from an earlier favorite state or an
        // older install, so it is readable for any entry, but it is only
        // written for library items.
        val coverCacheFile = coverFileProvider(url)
        if (isLibraryManga && coverCacheFile == null) error("No cover specified")
        if (coverCacheFile?.exists() == true && options.diskCachePolicy.readEnabled) {
            debugTitleCoverFlow(
                scope = "manga-fetcher",
                message = "cover-cache-hit file=${coverCacheFile.name}",
            )
            return fileLoader(coverCacheFile, diskCacheKey)
        }
        val libraryCoverCacheFile = coverCacheFile.takeIf { isLibraryManga }

        var snapshot = readFromDiskCache(diskCacheKey)
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                debugTitleCoverFlow(scope = "manga-fetcher", message = "disk-cache-hit key=$diskCacheKey")
                val snapshotCoverCache = moveSnapshotToCoverCache(snapshot, libraryCoverCacheFile, diskCacheKey)
                if (snapshotCoverCache != null) {
                    // Read from cover cache after added to library
                    debugTitleCoverFlow(
                        scope = "manga-fetcher",
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
                scope = "manga-fetcher",
                message = "network-fetch url=${previewTitleCoverUrl(url)} key=$diskCacheKey",
            )
            val response = executeNetworkRequest(url)
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                // Read from cover cache after library manga cover updated
                val responseCoverCache = writeResponseToCoverCache(response, libraryCoverCacheFile)
                if (responseCoverCache != null) {
                    debugTitleCoverFlow(
                        scope = "manga-fetcher",
                        message = "network-response-written-to-library-cache file=${responseCoverCache.name}",
                    )
                    // Mirror the response into Coil's disk cache as well so the
                    // cover survives library cover cache invalidation.
                    runCatching { writeToDiskCache(response, diskCacheKey)?.close() }
                    return fileLoader(responseCoverCache, diskCacheKey)
                }

                // Read from disk cache
                snapshot = writeToDiskCache(response, diskCacheKey)
                if (snapshot != null) {
                    debugTitleCoverFlow(
                        scope = "manga-fetcher",
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
            throw IOException("HTTP ${response.code}: ${response.message.ifBlank { "No response message" }}")
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
                // Keep the disk cache entry too; removing it made covers vanish
                // on offline starts whenever the request later stopped resolving
                // as a library item.
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

    class MangaFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Manga> {

        private val coverCache: MangaCoverCache by injectLazy()
        private val sourceManager: MangaSourceManager by injectLazy()
        private val metadataCoverResolver: MetadataCoverResolver by injectLazy()
        private val getManga: GetManga by injectLazy()

        override fun create(data: Manga, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(
                url = data.thumbnailUrl,
                isLibraryManga = data.favorite,
                options = options,
                coverFileProvider = coverCache::getCoverFile,
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.id) },
                diskCacheKeyProvider = { effectiveUrl, lastModified ->
                    "manga;${data.id};$effectiveUrl;${lastModified ?: data.coverLastModified}"
                },
                metadataCoverUrlProvider = { metadataCoverResolver.resolveMangaCoverUrl(data.id) },
                dbCoverProvider = { getManga.await(data.id)?.let { it.thumbnailUrl to it.coverLastModified } },
                sourceLazy = lazy { sourceManager.get(data.source) as? HttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    class MangaCoverFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<MangaCover> {

        private val coverCache: MangaCoverCache by injectLazy()
        private val sourceManager: MangaSourceManager by injectLazy()
        private val metadataCoverResolver: MetadataCoverResolver by injectLazy()
        private val getManga: GetManga by injectLazy()

        override fun create(data: MangaCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(
                url = data.url,
                isLibraryManga = data.isMangaFavorite,
                options = options,
                coverFileProvider = coverCache::getCoverFile,
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.mangaId) },
                diskCacheKeyProvider = { effectiveUrl, lastModified ->
                    "manga;${data.mangaId};$effectiveUrl;${lastModified ?: data.lastModified}"
                },
                metadataCoverUrlProvider = { metadataCoverResolver.resolveMangaCoverUrl(data.mangaId) },
                dbCoverProvider = { getManga.await(data.mangaId)?.let { it.thumbnailUrl to it.coverLastModified } },
                sourceLazy = lazy { sourceManager.get(data.sourceId) as? HttpSource },
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
