package eu.kanade.tachiyomi.data.coil

import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.novel.NovelImageRequestSource
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

class NovelCoverFetcher(
    private val data: NovelCover,
    private val options: Options,
    private val sourceSiteUrlLazy: Lazy<String?>,
    private val coverFileLazy: Lazy<File?>,
    private val customCoverFileLazy: Lazy<File>,
    private val diskCacheKeyProvider: (String?, Long?) -> String,
    private val dbCoverProvider: suspend () -> Pair<String?, Long>? = { null },
    private val pluginHeadersProvider: suspend () -> Map<String, String>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
    private val pluginImageResolver: suspend (String) -> eu.kanade.tachiyomi.source.novel.NovelPluginImagePayload? =
        NovelPluginImageResolver::resolve,
) : Fetcher {

    private lateinit var diskCacheKey: String

    override suspend fun fetch(): FetchResult {
        val customCoverFile = customCoverFileLazy.value
        if (customCoverFile.exists()) {
            debugTitleCoverFlow(
                scope = "novel-fetcher",
                message = "custom-cover-hit file=${customCoverFile.name}",
            )
            diskCacheKey = diskCacheKeyProvider(data.url, null)
            return fileLoader(customCoverFile)
        }
        var rawUrl = data.url?.takeIf { it.isNotBlank() }
        var lastModified: Long? = null

        if (rawUrl.isNullOrBlank()) {
            val dbResult = dbCoverProvider()
            if (dbResult != null) {
                rawUrl = dbResult.first
                lastModified = dbResult.second
            }
        }

        diskCacheKey = diskCacheKeyProvider(rawUrl, lastModified)
        debugTitleCoverFlow(
            scope = "novel-fetcher",
            message = "fetch url=${previewTitleCoverUrl(
                rawUrl,
            )} diskCacheKey=$diskCacheKey isLibrary=${data.isNovelFavorite}",
        )
        if (rawUrl.isNullOrBlank()) throw IOException("No cover URL specified for novel ${data.novelId}")
        return when (getResourceType(rawUrl)) {
            Type.URL -> httpLoader(rawUrl)
            Type.PLUGIN_IMAGE -> pluginImageLoader(rawUrl)
            Type.RELATIVE -> {
                val siteUrl = sourceSiteUrlLazy.value?.trimEnd('/')
                if (siteUrl != null) {
                    httpLoader("$siteUrl$rawUrl")
                } else {
                    // No base URL available; best-effort fallback – treat as HTTP
                    httpLoader(rawUrl)
                }
            }
            Type.File -> fileLoader(File(rawUrl.substringAfter("file://")))
            Type.URI -> uniFileLoader(rawUrl)
            null -> error("Invalid image")
        }
    }

    private fun uniFileLoader(urlString: String): FetchResult {
        val uniFile = UniFile.fromUri(options.context, urlString.toUri())
            ?: throw IOException("Unable to resolve image uri: $urlString")
        val inputStream = uniFile.openInputStream()
        val tempFile = inputStream.source().buffer()
        return SourceFetchResult(
            source = ImageSource(source = tempFile, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun pluginImageLoader(url: String): FetchResult {
        readPluginImageFromDiskCache(imageLoader, options, url)?.let {
            debugTitleCoverFlow(
                scope = "novel-fetcher",
                message = "plugin-image-disk-cache-hit url=${previewTitleCoverUrl(url)}",
            )
            return it
        }
        debugTitleCoverFlow(scope = "novel-fetcher", message = "plugin-image-fetch url=${previewTitleCoverUrl(url)}")
        val resolved = resolveNovelPluginImagePayload(url, resolver = pluginImageResolver)
            ?: throw IOException("Failed to resolve plugin image: $url")
        writePluginImageToDiskCache(imageLoader, options, url, resolved.bytes, resolved.mimeType)?.let { return it }
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(resolved.bytes),
                fileSystem = options.fileSystem,
            ),
            mimeType = resolved.mimeType,
            dataSource = DataSource.NETWORK,
        )
    }

    private fun fileLoader(file: File): FetchResult {
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

    private suspend fun httpLoader(url: String): FetchResult {
        // The cover cache file may exist from an earlier favorite state or an
        // older install, so it is readable for any entry, but it is only
        // written for library items.
        val coverCacheFile = coverFileLazy.value
        if (data.isNovelFavorite && coverCacheFile == null) error("No cover specified")
        if (coverCacheFile?.exists() == true && options.diskCachePolicy.readEnabled) {
            debugTitleCoverFlow(
                scope = "novel-fetcher",
                message = "cover-cache-hit file=${coverCacheFile.name}",
            )
            return fileLoader(coverCacheFile)
        }
        val libraryCoverCacheFile = coverCacheFile.takeIf { data.isNovelFavorite }

        var snapshot = readFromDiskCache()
        try {
            if (snapshot != null) {
                debugTitleCoverFlow(scope = "novel-fetcher", message = "disk-cache-hit key=$diskCacheKey")
                val snapshotCoverCache = moveSnapshotToCoverCache(snapshot, libraryCoverCacheFile)
                if (snapshotCoverCache != null) {
                    debugTitleCoverFlow(
                        scope = "novel-fetcher",
                        message = "snapshot-moved-to-library-cache file=${snapshotCoverCache.name}",
                    )
                    return fileLoader(snapshotCoverCache)
                }

                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            debugTitleCoverFlow(
                scope = "novel-fetcher",
                message = "network-fetch url=${previewTitleCoverUrl(url)} key=$diskCacheKey",
            )
            val response = executeNetworkRequest(url)
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                val responseCoverCache = writeResponseToCoverCache(response, libraryCoverCacheFile)
                if (responseCoverCache != null) {
                    debugTitleCoverFlow(
                        scope = "novel-fetcher",
                        message = "network-response-written-to-library-cache file=${responseCoverCache.name}",
                    )
                    // Mirror the response into Coil's disk cache as well so the
                    // cover survives library cover cache invalidation.
                    runCatching { writeToDiskCache(response)?.close() }
                    return fileLoader(responseCoverCache)
                }

                snapshot = writeToDiskCache(response)
                if (snapshot != null) {
                    debugTitleCoverFlow(
                        scope = "novel-fetcher",
                        message = "network-response-written-to-disk-cache key=$diskCacheKey",
                    )
                    return SourceFetchResult(
                        source = snapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                return SourceFetchResult(
                    source = ImageSource(
                        source = responseBody.source(),
                        fileSystem = FileSystem.SYSTEM,
                    ),
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
        val pluginHeaders = pluginHeadersProvider()
        val response = callFactoryLazy.value
            .newCall(
                buildNovelCoverRequest(
                    url = url,
                    siteUrl = sourceSiteUrlLazy.value,
                    pluginHeaders = pluginHeaders,
                    readFromNetwork = options.networkCachePolicy.readEnabled,
                ),
            )
            .await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException("HTTP ${response.code}: ${response.message.ifBlank { "No response message" }}")
        }
        return response
    }

    private fun moveSnapshotToCoverCache(snapshot: DiskCache.Snapshot, cacheFile: File?): File? {
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
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to novel cover cache ${cacheFile.name}" }
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
            logcat(LogPriority.ERROR, e) { "Failed to write response data to novel cover cache ${cacheFile.name}" }
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

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            imageLoader.diskCache?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun writeToDiskCache(response: Response): DiskCache.Snapshot? {
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
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
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
            NovelPluginImage.isSupported(cover) -> Type.PLUGIN_IMAGE
            cover.startsWith("http", true) || cover.startsWith("Custom-", true) -> Type.URL
            cover.startsWith("file://") -> Type.File
            cover.startsWith("/") -> Type.RELATIVE
            cover.startsWith("content") -> Type.URI
            else -> null
        }
    }

    private enum class Type {
        File,
        URL,
        URI,
        PLUGIN_IMAGE,

        /** A path starting with '/' that should be resolved against the source's site URL. */
        RELATIVE,
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<NovelCover> {

        private val coverCache: NovelCoverCache by injectLazy()
        private val sourceManager: NovelSourceManager by injectLazy()
        private val getNovel: GetNovel by injectLazy()

        override fun create(data: NovelCover, options: Options, imageLoader: ImageLoader): Fetcher? {
            return NovelCoverFetcher(
                data = data,
                options = options,
                sourceSiteUrlLazy = lazy { (sourceManager.get(data.sourceId) as? NovelSiteSource)?.siteUrl },
                coverFileLazy = lazy { coverCache.getCoverFile(data.url) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.novelId) },
                diskCacheKeyProvider = { effectiveUrl, lastModified ->
                    "novel;${data.novelId};$effectiveUrl;${lastModified ?: data.lastModified}"
                },
                dbCoverProvider = { getNovel.await(data.novelId)?.let { it.thumbnailUrl to it.coverLastModified } },
                pluginHeadersProvider = {
                    (sourceManager.get(data.sourceId) as? NovelImageRequestSource)
                        ?.getImageRequestHeaders()
                        .orEmpty()
                },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
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
    pluginHeaders: Map<String, String> = emptyMap(),
    readFromNetwork: Boolean,
): Request {
    val normalizedSiteUrl = siteUrl
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.trimEnd('/')
    val normalizedPluginHeaders = pluginHeaders
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isEmpty() || normalizedValue.isEmpty()) {
                null
            } else {
                normalizedKey to normalizedValue
            }
        }
        .toMap()

    return Request.Builder()
        .url(url)
        .apply {
            normalizedPluginHeaders.forEach { (key, value) ->
                addHeader(key, value)
            }
            if (normalizedSiteUrl != null) {
                if (normalizedPluginHeaders.keys.none { it.equals("Referer", ignoreCase = true) }) {
                    addHeader("Referer", "$normalizedSiteUrl/")
                }
                if (normalizedPluginHeaders.keys.none { it.equals("Origin", ignoreCase = true) }) {
                    addHeader("Origin", normalizedSiteUrl)
                }
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
