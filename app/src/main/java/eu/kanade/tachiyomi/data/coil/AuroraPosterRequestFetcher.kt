package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.CoverRequestPolicy
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import java.io.IOException

data class AuroraPosterRequest(
    val primaryUrl: String?,
    val fallbackUrl: String? = null,
    val refererUrl: String? = null,
)

class AuroraPosterRequestKeyer : Keyer<AuroraPosterRequest> {
    override fun key(data: AuroraPosterRequest, options: Options): String {
        return buildString {
            append("aurora-poster;")
            append(data.primaryUrl.orEmpty())
            append(';')
            append(data.fallbackUrl.orEmpty())
        }
    }
}

class AuroraPosterRequestFetcher(
    private val data: AuroraPosterRequest,
    private val options: Options,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    private val diskCacheKey: String
        get() = buildString {
            append("aurora-poster;")
            append(data.primaryUrl.orEmpty())
            append(';')
            append(data.fallbackUrl.orEmpty())
        }

    override suspend fun fetch(): FetchResult {
        readFromDiskCache()?.let { return it }

        val fetched = loadAuroraPosterSource(
            callFactory = callFactoryLazy.value,
            fileSystem = options.fileSystem,
            request = data,
        )
        if (fetched !is SourceFetchResult) return fetched
        return writeToDiskCache(fetched) ?: fetched
    }

    private fun readFromDiskCache(): FetchResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val diskCache = imageLoader.diskCache ?: return null
        val snapshot = runCatching { diskCache.openSnapshot(diskCacheKey) }.getOrNull() ?: return null
        return SourceFetchResult(
            source = ImageSource(
                file = snapshot.data,
                fileSystem = diskCache.fileSystem,
                diskCacheKey = diskCacheKey,
                closeable = snapshot,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    /**
     * Persists the fetched poster bytes into Coil's disk cache so posters stay
     * available on offline starts. Returns null only before the source has
     * been consumed (caching disabled), so the caller can fall back to the
     * original result safely.
     */
    private fun writeToDiskCache(fetched: SourceFetchResult): FetchResult? {
        if (!options.diskCachePolicy.writeEnabled) return null
        val diskCache = imageLoader.diskCache ?: return null
        val bytes = fetched.source.use { it.source().readByteArray() }
        val snapshot = runCatching {
            val editor = diskCache.openEditor(diskCacheKey) ?: return@runCatching null
            try {
                diskCache.fileSystem.write(editor.data) { write(bytes) }
                editor.commitAndOpenSnapshot()
            } catch (e: Exception) {
                runCatching { editor.abort() }
                null
            }
        }.getOrNull()
        val source = if (snapshot != null) {
            ImageSource(
                file = snapshot.data,
                fileSystem = diskCache.fileSystem,
                diskCacheKey = diskCacheKey,
                closeable = snapshot,
            )
        } else {
            ImageSource(source = Buffer().write(bytes), fileSystem = options.fileSystem)
        }
        return SourceFetchResult(
            source = source,
            mimeType = fetched.mimeType,
            dataSource = fetched.dataSource,
        )
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<AuroraPosterRequest> {
        override fun create(
            data: AuroraPosterRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return AuroraPosterRequestFetcher(
                data = data,
                options = options,
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }
}

internal suspend fun loadAuroraPosterSource(
    callFactory: Call.Factory,
    fileSystem: FileSystem,
    request: AuroraPosterRequest,
): FetchResult {
    val candidates = buildList {
        request.primaryUrl?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        request.fallbackUrl?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
    }.distinct()

    var lastError: IOException? = null

    for ((attemptIndex, candidate) in candidates.withIndex()) {
        val httpUrl = candidate.toHttpUrlOrNull()
        if (httpUrl == null) {
            lastError = IOException("Invalid poster url: $candidate")
            continue
        }
        if (CoverRequestPolicy.isBlacklisted(httpUrl.host)) {
            lastError = IOException("Skipped blacklisted cover host: ${httpUrl.host}")
            continue
        }

        val requestBuilder = CoverRequestPolicy.markCoverRequest(
            Request.Builder().url(httpUrl),
            fallbackUrl = request.fallbackUrl,
            attempt = attemptIndex,
        )

        request.refererUrl?.trim()?.takeIf { it.isNotBlank() }?.let { referer ->
            requestBuilder.addHeader("Referer", referer.trimEnd('/') + "/")
        }

        val response = try {
            callFactory.newCall(requestBuilder.build()).await()
        } catch (e: IOException) {
            lastError = e
            continue
        }

        val body = response.body
        if (response.isSuccessful) {
            CoverRequestPolicy.clear(httpUrl.host)
            return SourceFetchResult(
                source = ImageSource(
                    source = body.source(),
                    fileSystem = fileSystem,
                ),
                mimeType = body.contentType()?.toString() ?: "image/*",
                dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
            )
        }

        lastError = IOException("HTTP ${response.code} for $candidate")
        response.close()
    }

    throw (lastError ?: IOException("Failed to load poster"))
}
