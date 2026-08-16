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
import eu.kanade.tachiyomi.network.toAsciiUrl
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

data class AuroraPosterRequest(
    val primaryUrl: String?,
    val fallbackUrl: String? = null,
    val refererUrl: String? = null,
    /**
     * Extra request headers (e.g. the source's User-Agent) forwarded verbatim.
     * Some cover proxies (anilist via wp.youtube-anime.com) answer 403 when the
     * request does not carry the source's headers, while the same URL succeeds
     * through the source's own client in [eu.kanade.tachiyomi.data.coil.AnimeImageFetcher].
     */
    val headers: Map<String, String>? = null,
    /**
     * Source's own OkHttp client, when available. It carries the source's
     * interceptors (Cloudflare/WebView bypass, User-Agent) that the generic
     * poster client does not, so viewer-hosted covers (e.g. animepahe behind
     * Cloudflare) are fetched the same way the entry thumbnail is.
     */
    val client: Call.Factory? = null,
    /**
     * User-set custom cover file for the entry, if any. When the file exists
     * it always wins over [primaryUrl]/[fallbackUrl] (issue #154).
     */
    val customCoverFile: File? = null,
    /** Entry cover timestamp so cache keys change after cover edits. */
    val coverLastModified: Long = 0L,
)

class AuroraPosterRequestKeyer : Keyer<AuroraPosterRequest> {
    override fun key(data: AuroraPosterRequest, options: Options): String {
        return buildString {
            append("aurora-poster;")
            append(data.primaryUrl.orEmpty())
            append(';')
            append(data.fallbackUrl.orEmpty())
            append(';')
            append(data.coverLastModified)
            if (data.customCoverFile?.exists() == true) {
                append(";custom")
            }
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
        // Issue #154: a user-set custom cover must always win over URL-resolved
        // posters and over the URL-keyed poster disk cache.
        data.customCoverFile?.takeIf { it.exists() }?.let { file ->
            return customCoverLoader(file)
        }
        readFromDiskCache()?.let { return it }

        val fetched = loadAuroraPosterSource(
            callFactory = data.client ?: callFactoryLazy.value,
            fileSystem = options.fileSystem,
            request = data,
        )
        if (fetched !is SourceFetchResult) return fetched
        return writeToDiskCache(fetched) ?: fetched
    }

    private fun customCoverLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
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

        request.headers?.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        request.refererUrl?.trim()?.takeIf { it.isNotBlank() }?.let { referer ->
            requestBuilder.header("Referer", referer.toAsciiUrl().trimEnd('/') + "/")
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
