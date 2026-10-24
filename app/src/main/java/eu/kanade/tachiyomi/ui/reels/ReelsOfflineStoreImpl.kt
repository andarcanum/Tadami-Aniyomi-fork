package eu.kanade.tachiyomi.ui.reels

import android.app.Application
import android.net.Uri
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ReelsOfflineStoreImpl(
    private val app: Application = Injekt.get(),
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = ReelsOfflineStore.MAX_OFFLINE_BYTES,
    private val minFreeBytes: Long = MIN_FREE_BYTES,
) : ReelsOfflineStore {

    // Writers are serialized: the quota verdict and the final publish must observe a stable
    // directory, otherwise two downloads both pass a stale size check and both publish.
    private val writeMutex = Mutex()

    private val dir: File
        get() = File(app.filesDir, "reels_offline")

    override fun isStored(sourceId: Long, videoId: String): Boolean = storedFile(sourceId, videoId) != null

    override fun localUrl(sourceId: Long, videoId: String): String? {
        val file = storedFile(sourceId, videoId) ?: return null
        return Uri.fromFile(file).toString()
    }

    override fun storedPairs(): Set<Pair<Long, String>> {
        val files = dir.listFiles() ?: return emptySet()
        return files.asSequence()
            // In-flight transfers are not stored copies.
            .filterNot { it.isTransferFile() }
            .mapNotNull { file -> parseName(file.name) }
            .toSet()
    }

    override suspend fun download(item: ShortVideoItem, sourceId: Long): ReelsOfflineStore.DownloadResult =
        withContext(ioDispatcher) {
            writeMutex.withLock { downloadLocked(item, sourceId) }
        }

    private suspend fun downloadLocked(
        item: ShortVideoItem,
        sourceId: Long,
    ): ReelsOfflineStore.DownloadResult {
        if (storedFile(sourceId, item.id) != null) return ReelsOfflineStore.DownloadResult.SAVED
        // A manifest is not a standalone copy: saving just the playlist file would yield an
        // "offline" item that cannot play in airplane mode.
        if (isAdaptiveReelUrl(item.videoUrl)) return ReelsOfflineStore.DownloadResult.UNSUPPORTED
        dir.mkdirs()
        sweepStaleTransfers()
        val request = Request.Builder()
            .url(item.videoUrl)
            .apply { sameOriginReferer(item.webUrl)?.let { header("Referer", it) } }
            .build()
        val call = client.newCall(request)
        // A cancelled coroutine must abort the socket wait, not just return after it.
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return ReelsOfflineStore.DownloadResult.NETWORK
                val body = response.body
                val contentType = body.contentType()
                if (isAdaptiveContentType(contentType)) {
                    return ReelsOfflineStore.DownloadResult.UNSUPPORTED
                }
                val expected = body.contentLength()
                val used = usedBytes()
                if (expected > 0 && used + expected > maxBytes) {
                    return ReelsOfflineStore.DownloadResult.QUOTA
                }
                // The name carries the real media type: an opaque videoId gives no extension,
                // and a still reel must keep an image suffix for the player's image branch.
                val extension = resolveExtension(item.videoUrl, contentType)
                val target = File(dir, fileNameFor(sourceId, item.id, extension))
                // Unique part name per attempt: a stale part from process death is swept,
                // while a live part is never shared between writers.
                val part = File(dir, "${target.name}.${System.nanoTime()}.$PART_SUFFIX")
                val budget = (maxBytes - used).coerceAtLeast(0L)
                val copied = try {
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            copyWithinBudget(input, output, budget)
                        }
                    }
                } catch (e: Exception) {
                    // Transport failure or cancellation: never leave an orphan part eating quota.
                    part.delete()
                    currentCoroutineContext().ensureActive()
                    throw e
                }
                if (copied == null) {
                    part.delete()
                    return ReelsOfflineStore.DownloadResult.QUOTA
                }
                if (copied == 0L) {
                    part.delete()
                    return ReelsOfflineStore.DownloadResult.NETWORK
                }
                if (!publish(part, target)) return ReelsOfflineStore.DownloadResult.NETWORK
            }
        } catch (e: CancellationException) {
            // The caller went away: report cancellation as such, never as a network verdict.
            throw e
        } catch (_: Exception) {
            // Transport failure (disconnect mid-body, timeout, disk write error): the part is
            // already gone, the contract maps this to NETWORK.
            return ReelsOfflineStore.DownloadResult.NETWORK
        } finally {
            cancellationHandle?.dispose()
        }
        return if (storedFile(sourceId, item.id) != null) {
            ReelsOfflineStore.DownloadResult.SAVED
        } else {
            ReelsOfflineStore.DownloadResult.NETWORK
        }
    }

    override suspend fun delete(sourceId: Long, videoId: String): Boolean = withContext(ioDispatcher) {
        writeMutex.withLock {
            val copies = dir.listFiles()
                ?.filter { file -> parseName(file.name) == (sourceId to videoId) }
                .orEmpty()
            copies.all { it.delete() }
        }
    }

    override fun usedBytes(): Long = (dir.listFiles() ?: emptyArray()).sumOf { it.length() }

    override suspend fun deleteBySource(sourceId: Long): Int = withContext(ioDispatcher) {
        writeMutex.withLock {
            val prefix = "${sourceId}_"
            (dir.listFiles() ?: emptyArray())
                .filter { it.name.startsWith(prefix) }
                .count { it.delete() }
        }
    }

    override suspend fun clearAll(): Long = withContext(ioDispatcher) {
        writeMutex.withLock {
            val files = dir.listFiles() ?: return@withLock 0L
            files.sumOf { file ->
                val size = file.length()
                if (file.delete()) size else 0L
            }
        }
    }

    /**
     * Copies [input] into [output] while enforcing both the store quota and a free-disk
     * reserve per chunk. Returning null stops a chunked or lying Content-Length response at
     * the budget instead of after the whole body has already consumed the filesystem.
     */
    private suspend fun copyWithinBudget(
        input: InputStream,
        output: OutputStream,
        budget: Long,
    ): Long? {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read == -1) return copied
            copied += read
            if (copied > budget) return null
            if (dir.usableSpace < minFreeBytes + read) return null
            output.write(buffer, 0, read)
        }
    }

    /**
     * The file is visible under its final name only after it is complete. A failed rename is
     * retried through a second same-directory temp, never by copying straight into [target].
     */
    private fun publish(part: File, target: File): Boolean {
        if (part.renameTo(target)) return true
        val staged = File(dir, "${target.name}.${System.nanoTime()}.$STAGED_SUFFIX")
        val stagedCopy = runCatching { part.copyTo(staged, overwrite = true) }.isSuccess
        if (stagedCopy && staged.renameTo(target)) {
            part.delete()
            return true
        }
        staged.delete()
        part.delete()
        return false
    }

    private fun storedFile(sourceId: Long, videoId: String): File? {
        val identity = sourceId to videoId
        return dir.listFiles()?.firstOrNull { file ->
            !file.isTransferFile() && parseName(file.name) == identity
        }
    }

    private fun fileNameFor(sourceId: Long, videoId: String, extension: String): String =
        "${sourceId}_${Uri.encode(videoId)}.$extension"

    private fun parseName(name: String): Pair<Long, String>? {
        // "sourceId_encodedVideoId.ext" — the encoded id is reversible, the ext is dropped.
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: return null
        val core = name.substring(0, dot)
        val underscore = core.indexOf('_')
        if (underscore <= 0) return null
        val sourceId = core.substring(0, underscore).toLongOrNull() ?: return null
        val videoId = Uri.decode(core.substring(underscore + 1)) ?: return null
        return sourceId to videoId
    }

    /** Same-origin Referer, mirroring the player: referer-protected CDNs 403 bare requests. */
    private fun sameOriginReferer(webUrl: String?): String? {
        if (webUrl.isNullOrBlank()) return null
        return runCatching {
            val page = Uri.parse(webUrl)
            val host = page.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
            if (page.scheme != "http" && page.scheme != "https") return@runCatching null
            val port = if (page.port != -1) ":${page.port}" else ""
            "${page.scheme}://$host$port/"
        }.getOrNull()
    }

    /** Orphan transfers (process death mid-download) must not eat quota forever. */
    private fun sweepStaleTransfers() {
        val cutoff = System.currentTimeMillis() - STALE_TRANSFER_MS
        (dir.listFiles() ?: return).forEach { file ->
            if (file.isTransferFile() && file.lastModified() < cutoff) file.delete()
        }
    }

    private fun File.isTransferFile(): Boolean =
        name.endsWith(".$PART_SUFFIX") || name.endsWith(".$STAGED_SUFFIX")

    private fun resolveExtension(url: String, contentType: MediaType?): String {
        val fromUrl = Uri.parse(url).lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        if (fromUrl in STORED_EXTENSIONS) return fromUrl
        return when (contentType?.subtype?.lowercase()?.substringAfterLast('+')) {
            "webm" -> "webm"
            "matroska", "x-matroska" -> "mkv"
            "quicktime" -> "mov"
            "jpeg" -> "jpg"
            "png" -> "png"
            "gif" -> "gif"
            "webp" -> "webp"
            "avif" -> "avif"
            else -> "mp4"
        }
    }

    private fun isAdaptiveReelUrl(url: String): Boolean =
        Uri.parse(url).lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase() in ADAPTIVE_EXTENSIONS

    private fun isAdaptiveContentType(contentType: MediaType?): Boolean {
        val mime = contentType?.let { "${it.type}/${it.subtype}".lowercase() } ?: return false
        return mime in ADAPTIVE_CONTENT_TYPES
    }

    private companion object {
        const val STALE_TRANSFER_MS = 60 * 60 * 1000L
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MIN_FREE_BYTES = 16L * 1024 * 1024
        const val PART_SUFFIX = "part"
        const val STAGED_SUFFIX = "staged"

        val ADAPTIVE_EXTENSIONS = setOf("m3u8", "mpd")
        val ADAPTIVE_CONTENT_TYPES = setOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "application/dash+xml",
        )

        // Known progressive containers (video and stills) keep their URL extension; anything
        // else falls back to the response content type.
        val STORED_EXTENSIONS = setOf(
            "mp4",
            "webm",
            "m4v",
            "mov",
            "mkv",
            "gif",
            "jpg",
            "jpeg",
            "png",
            "webp",
            "avif",
        )
    }
}
