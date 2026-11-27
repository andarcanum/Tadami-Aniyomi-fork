package eu.kanade.tachiyomi.ui.reels

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream

/**
 * Lightweight HLS (.m3u8) playlist parser and segment stitcher used for:
 * 1. Prewarming HLS manifest + first segment into [androidx.media3.datasource.cache.SimpleCache].
 * 2. Exporting/saving clear (unencrypted) HLS reels into a single playable `.ts` or `.mp4` file
 *    for [ReelsDownloads] and [ReelsOfflineStoreImpl].
 *
 * Encrypted playlists (`#EXT-X-KEY` with `METHOD != NONE`) and live/empty playlists are declined
 * cleanly as unsupported.
 */
internal object ReelsHlsDownloader {

    internal sealed interface ParsedPlaylist {
        data class Master(val bestVariantUrl: String) : ParsedPlaylist
        data class Media(
            val initSegmentUrl: String?,
            val segmentUrls: List<String>,
            val isFmp4: Boolean,
        ) : ParsedPlaylist
        data object Unsupported : ParsedPlaylist
    }

    internal data class ResolvedMediaPlaylist(
        val mediaPlaylistUrl: String,
        val initSegmentUrl: String?,
        val segmentUrls: List<String>,
        val isFmp4: Boolean,
    ) {
        val fileExtension: String get() = if (isFmp4) "mp4" else "ts"
        val mimeType: String get() = if (isFmp4) "video/mp4" else "video/mp2t"
    }

    /** Parses an M3U8 body against [baseUrl] into a master variant pick or a flat media segment list. */
    fun parsePlaylist(baseUrl: String, body: String): ParsedPlaylist {
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty() || !lines.first().startsWith("#EXTM3U", ignoreCase = true)) {
            return ParsedPlaylist.Unsupported
        }

        // Reject encrypted playlists (AES-128 / SAMPLE-AES / DRM) — raw stitching would be unplayable.
        val hasEncryption = lines.any { line ->
            line.startsWith("#EXT-X-KEY:", ignoreCase = true) &&
                !line.contains("METHOD=NONE", ignoreCase = true)
        }
        if (hasEncryption) return ParsedPlaylist.Unsupported

        // Master playlist?
        var bestBandwidth = -1L
        var bestVariant: String? = null
        var pendingBandwidth: Long? = null
        for (line in lines) {
            if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                pendingBandwidth = BANDWIDTH_REGEX.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            } else if (!line.startsWith("#") && pendingBandwidth != null) {
                val resolved = resolveUrl(baseUrl, line)
                if (resolved != null && pendingBandwidth > bestBandwidth) {
                    bestBandwidth = pendingBandwidth
                    bestVariant = resolved
                }
                pendingBandwidth = null
            }
        }
        if (bestVariant != null) {
            return ParsedPlaylist.Master(bestVariant)
        }

        // Media playlist
        var initUrl: String? = null
        val segments = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith("#EXT-X-MAP:", ignoreCase = true)) {
                val rawUri = URI_ATTR_REGEX.find(line)?.groupValues?.get(1)
                if (!rawUri.isNullOrBlank()) {
                    initUrl = resolveUrl(baseUrl, rawUri)
                }
            } else if (!line.startsWith("#")) {
                resolveUrl(baseUrl, line)?.let { segments += it }
            }
        }
        if (segments.isEmpty()) return ParsedPlaylist.Unsupported

        val isFmp4 = initUrl != null || segments.first().substringBefore('?').endsWith(".m4s", ignoreCase = true)
        return ParsedPlaylist.Media(
            initSegmentUrl = initUrl,
            segmentUrls = segments,
            isFmp4 = isFmp4,
        )
    }

    /**
     * Resolves an HLS URL (following at most one master -> variant hop) into its concrete media
     * segment list. Returns null if the playlist is unsupported (DRM/empty) or unreachable.
     */
    suspend fun resolveMediaPlaylist(
        client: OkHttpClient,
        manifestUrl: String,
        headers: Map<String, String>,
    ): ResolvedMediaPlaylist? {
        val text = fetchText(client, manifestUrl, headers) ?: return null
        return when (val parsed = parsePlaylist(manifestUrl, text)) {
            ParsedPlaylist.Unsupported -> null
            is ParsedPlaylist.Media -> ResolvedMediaPlaylist(
                mediaPlaylistUrl = manifestUrl,
                initSegmentUrl = parsed.initSegmentUrl,
                segmentUrls = parsed.segmentUrls,
                isFmp4 = parsed.isFmp4,
            )
            is ParsedPlaylist.Master -> {
                val variantText = fetchText(client, parsed.bestVariantUrl, headers) ?: return null
                when (val variantParsed = parsePlaylist(parsed.bestVariantUrl, variantText)) {
                    is ParsedPlaylist.Media -> ResolvedMediaPlaylist(
                        mediaPlaylistUrl = parsed.bestVariantUrl,
                        initSegmentUrl = variantParsed.initSegmentUrl,
                        segmentUrls = variantParsed.segmentUrls,
                        isFmp4 = variantParsed.isFmp4,
                    )
                    else -> null
                }
            }
        }
    }

    /**
     * Downloads every segment of [playlist] sequentially into [output], reporting cumulative byte
     * progress and checking [maxBytes] if a storage quota is active.
     */
    suspend fun stitchSegments(
        client: OkHttpClient,
        playlist: ResolvedMediaPlaylist,
        headers: Map<String, String>,
        output: OutputStream,
        maxBytes: Long = Long.MAX_VALUE,
        onProgress: (writtenBytes: Long, estimatedTotal: Long?) -> Unit = { _, _ -> },
    ): Long {
        val allUrls = buildList {
            playlist.initSegmentUrl?.let { add(it) }
            addAll(playlist.segmentUrls)
        }
        val totalSegments = allUrls.size.coerceAtLeast(1)
        val buffer = ByteArray(64 * 1024)
        var totalWritten = 0L

        for ((index, segUrl) in allUrls.withIndex()) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder()
                .url(segUrl)
                .apply { headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) } }
                .build()
            val call = client.newCall(request)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HLS segment HTTP ${response.code}")
                    }
                    response.body.byteStream().use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            totalWritten += read
                            if (totalWritten > maxBytes) {
                                throw HlsQuotaExceededException()
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            }
            output.flush()
            val avgPerSeg = totalWritten / (index + 1)
            val estimatedTotal = if (index + 1 == totalSegments) totalWritten else avgPerSeg * totalSegments
            onProgress(totalWritten, estimatedTotal)
        }
        return totalWritten
    }

    private suspend fun fetchText(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
    ): String? {
        currentCoroutineContext().ensureActive()
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) } }
            .build()
        val call = client.newCall(request)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (_: Exception) {
            null
        }
    }

    internal fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        return base.resolve(relativeOrAbsolute)?.toString()
    }

    internal class HlsQuotaExceededException : IOException("HLS download exceeded quota")

    private val BANDWIDTH_REGEX = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
    private val URI_ATTR_REGEX = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
}
