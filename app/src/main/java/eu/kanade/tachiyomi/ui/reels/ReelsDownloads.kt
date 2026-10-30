package eu.kanade.tachiyomi.ui.reels

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.reels.player.reelsRequestHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

internal sealed interface ReelsDownloadResult {
    data class Saved(val uri: Uri, val bytes: Long) : ReelsDownloadResult

    /** Interrupted transfer left usable bytes behind: the caller can resume from them. */
    data class Partial(val uri: Uri, val bytes: Long) : ReelsDownloadResult
    data object Failed : ReelsDownloadResult
    data object Unsupported : ReelsDownloadResult
}

/** Copy aborted mid-body; [bytes] are durably in the destination and can be resumed from. */
private class PartialCopyException(val bytes: Long) : IOException("Interrupted reel download")

internal fun reelsDownloadClient(base: OkHttpClient): OkHttpClient =
    base.newBuilder()
        // A reel is one unbroken body: the shared whole-call cap aborts large
        // files that are transferring perfectly well.
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

internal class ReelsDownloads(
    context: Context,
    private val client: OkHttpClient = reelsDownloadClient(Injekt.get<NetworkHelper>().client),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val retryBaseDelayMs: Long = 1_000L,
) {
    private val resolver = context.contentResolver

    suspend fun download(
        item: ShortVideoItem,
        sourceId: Long,
        headers: Map<String, String> = emptyMap(),
        destination: Uri? = null,
        resumeFrom: Long = 0L,
        resumeUri: Uri? = null,
        keepPartial: Boolean = false,
        onProgress: (bytes: Long, total: Long?) -> Unit = { _, _ -> },
    ): ReelsDownloadResult {
        var target = destination ?: resumeUri
        var completed = false
        var retained = false
        val resuming = resumeUri != null && resumeFrom > 0L && destination == null
        try {
            val downloaded = withContext(ioDispatcher) {
                val coroutineContext = currentCoroutineContext()
                try {
                    coroutineContext.ensureActive()
                    if (destination == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        return@withContext ReelsDownloadResult.Failed
                    }
                    if (urlExtension(item.videoUrl) in ADAPTIVE_EXTENSIONS) {
                        return@withContext ReelsDownloadResult.Unsupported
                    }

                    fun writeInto(
                        uri: Uri,
                        mode: String,
                        input: InputStream,
                        expected: Long?,
                        offset: Long = 0L,
                    ): ReelsDownloadResult {
                        target = uri
                        val bytes = resolver.openOutputStream(uri, mode)?.use { output ->
                            // Progress stays in whole-file terms so the ring never jumps back.
                            copy(input, output, expected, coroutineContext) { written, total ->
                                onProgress(offset + written, total?.let { offset + it })
                            }
                        } ?: return ReelsDownloadResult.Failed
                        return ReelsDownloadResult.Saved(uri, bytes)
                    }

                    fun transfer(
                        input: InputStream,
                        mediaUrl: String,
                        contentType: String?,
                        total: Long?,
                    ): ReelsDownloadResult {
                        return input.buffered().use { buffered ->
                            if (urlExtension(mediaUrl) in ADAPTIVE_EXTENSIONS || contentType in MANIFEST_TYPES) {
                                return ReelsDownloadResult.Unsupported
                            }

                            // MIME and URL suffixes can lie: inspect a small prefix before creating an entry.
                            buffered.mark(SNIFF_BYTES)
                            val prefix = ByteArray(SNIFF_BYTES)
                            var prefixSize = 0
                            while (prefixSize < prefix.size) {
                                coroutineContext.ensureActive()
                                val read = buffered.read(prefix, prefixSize, prefix.size - prefixSize)
                                if (read == -1) break
                                prefixSize += read
                            }
                            buffered.reset()
                            if (prefixSize == 0) return ReelsDownloadResult.Failed
                            val text = String(prefix, 0, prefixSize, Charsets.UTF_8)
                                .trimStart { it.isWhitespace() || it == '\uFEFF' }
                            val dashManifest = text.startsWith("<MPD", ignoreCase = true) ||
                                (
                                    text.startsWith("<?xml", ignoreCase = true) &&
                                        text.contains("<MPD", ignoreCase = true)
                                    )
                            if (text.startsWith("#EXTM3U", ignoreCase = true) || dashManifest) {
                                return ReelsDownloadResult.Unsupported
                            }
                            if (text.isEmpty() || text.first() in setOf('<', '{', '[', '"')) {
                                return ReelsDownloadResult.Failed
                            }

                            val typeExtension = MEDIA_TYPES.entries.firstOrNull { it.value == contentType }?.key
                                ?: contentType?.let { MIME_ALIASES[it] }
                            if (contentType != null &&
                                contentType != "application/octet-stream" &&
                                typeExtension == null
                            ) {
                                return ReelsDownloadResult.Failed
                            }
                            // Opaque binary endpoints need a real media signature, not a guessed .mp4.
                            val data = prefix.toByteString(0, prefixSize)
                            val signatureExtension = when {
                                prefixSize >= 12 && data.substring(4, 8).utf8() == "ftyp" &&
                                    data.substring(8, 12).utf8() in MP4_BRANDS -> "mp4"
                                data.startsWith(EBML_SIGNATURE) && data.indexOf(WEBM_DOCTYPE) >= 0 -> "webm"
                                data.startsWith(JPEG_SIGNATURE) -> "jpg"
                                data.startsWith(PNG_SIGNATURE) -> "png"
                                else -> null
                            }
                            val urlSuffix = urlExtension(mediaUrl).takeIf { it in MEDIA_TYPES }
                                ?: urlExtension(item.videoUrl).takeIf { it in MEDIA_TYPES }
                            val extension = urlSuffix?.takeIf {
                                typeExtension == null || MEDIA_TYPES[it] == MEDIA_TYPES[typeExtension]
                            } ?: typeExtension ?: signatureExtension ?: return ReelsDownloadResult.Unsupported
                            val mime = contentType?.takeIf { typeExtension != null } ?: MEDIA_TYPES.getValue(extension)

                            coroutineContext.ensureActive()
                            val uri = destination ?: resumeUri ?: resolver.insert(
                                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                                ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName(item, sourceId, extension))
                                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                },
                            ) ?: return ReelsDownloadResult.Failed
                            // Reached only for a fresh start, or when the server ignored our Range and
                            // sent the whole body: either way the file is rewritten from zero.
                            writeInto(uri, "wt", buffered, total)
                        }
                    }

                    val result = if (Uri.parse(item.videoUrl).scheme == "file") {
                        val source = File(URI(item.videoUrl))
                        if (!source.isFile) return@withContext ReelsDownloadResult.Failed
                        transfer(source.inputStream(), item.videoUrl, null, source.length())
                    } else {
                        val request = Request.Builder()
                            .url(item.videoUrl)
                            .apply {
                                reelsRequestHeaders(headers, item.webUrl).forEach { (name, value) ->
                                    header(name, value)
                                }
                            }
                            .apply { if (resuming) header("Range", "bytes=$resumeFrom-") }
                            .build()
                        var outcome: ReelsDownloadResult? = null
                        var attempt = 0
                        // The edge throttles a full GET while the feed keeps its own streams on the
                        // same token. Retrying happens before any destination exists, so a throttle
                        // never leaves a partial file behind.
                        while (outcome == null && attempt < THROTTLE_ATTEMPTS) {
                            if (attempt > 0) delay(retryBackoffMs(attempt - 1))
                            attempt++
                            outcome = suspendCancellableCoroutine<ReelsDownloadResult?> { continuation ->
                                val call = client.newCall(request)
                                // The handler fires as cancellation starts, including while execute/read is blocked.
                                // A Job completion handler would run too late to abort the socket wait.
                                continuation.invokeOnCancellation { call.cancel() }
                                try {
                                    coroutineContext.ensureActive()
                                    val downloaded = call.execute().use { response ->
                                        val contentRange = response.header("Content-Range")
                                        when {
                                            response.code in THROTTLE_CODES -> null
                                            // An offset the edge won't serve means the partial is
                                            // stale: discard it so the next tap starts clean.
                                            resuming && response.code == 416 -> ReelsDownloadResult.Failed
                                            resuming && response.code == 206 &&
                                                contentRange.rangeStartOrNull() == resumeFrom -> {
                                                val body = response.body
                                                val appended = writeInto(
                                                    requireNotNull(resumeUri),
                                                    "wa",
                                                    body.byteStream(),
                                                    body.contentLength().takeIf { it >= 0L },
                                                    offset = resumeFrom,
                                                )
                                                if (appended is ReelsDownloadResult.Saved) {
                                                    ReelsDownloadResult.Saved(appended.uri, resumeFrom + appended.bytes)
                                                } else {
                                                    appended
                                                }
                                            }
                                            !response.isSuccessful || response.code == 206 ||
                                                contentRange != null -> ReelsDownloadResult.Failed
                                            else -> {
                                                val body = response.body
                                                val contentType = body.contentType()
                                                    ?.let { it.type + "/" + it.subtype }
                                                    ?.lowercase()
                                                transfer(
                                                    body.byteStream(),
                                                    response.request.url.toString(),
                                                    contentType,
                                                    body.contentLength().takeIf { it >= 0L },
                                                )
                                            }
                                        }
                                    }
                                    continuation.resume(downloaded)
                                } catch (e: Exception) {
                                    continuation.resumeWithException(e)
                                }
                            }
                        }
                        // A throttled resume keeps what is already on disk; a throttled fresh start
                        // has nothing worth keeping yet.
                        outcome ?: if (resuming) {
                            retained = true
                            ReelsDownloadResult.Partial(requireNotNull(resumeUri), resumeFrom)
                        } else {
                            ReelsDownloadResult.Failed
                        }
                    }
                    coroutineContext.ensureActive()
                    if (result is ReelsDownloadResult.Saved) {
                        if (destination == null) {
                            val published = resolver.update(
                                result.uri,
                                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                                null,
                                null,
                            )
                            if (published != 1) return@withContext ReelsDownloadResult.Failed
                        }
                    }
                    result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PartialCopyException) {
                    // An interrupted transfer, not a user cancel: the written bytes are valid and
                    // the entry stays pending so the next tap can resume it.
                    val cancelled = !coroutineContext.isActive
                    val uri = target
                    if (keepPartial && destination == null && uri != null && e.bytes > 0L && !cancelled) {
                        retained = true
                        ReelsDownloadResult.Partial(uri, e.bytes)
                    } else {
                        coroutineContext.ensureActive()
                        ReelsDownloadResult.Failed
                    }
                } catch (_: Exception) {
                    coroutineContext.ensureActive()
                    ReelsDownloadResult.Failed
                }
            }
            completed = downloaded is ReelsDownloadResult.Saved
            return downloaded
        } finally {
            if (!completed && !retained) {
                target?.let { uri ->
                    // Also runs when cancellation prevents entry into the IO transfer block.
                    withContext(NonCancellable + ioDispatcher) {
                        runCatching {
                            when {
                                uri.scheme == "file" -> File(requireNotNull(uri.path)).delete()
                                destination != null -> DocumentsContract.deleteDocument(resolver, uri)
                                else -> resolver.delete(uri, null, null)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes an abandoned partial entry. A pending row that is never published holds its bytes on
     * the user's storage, so every path that forgets a partial has to call this.
     */
    fun discard(uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    private fun String?.rangeStartOrNull(): Long? =
        this?.removePrefix("bytes ")?.substringBefore('-')?.toLongOrNull()

    private fun retryBackoffMs(retries: Int): Long =
        retryBaseDelayMs * (1L shl retries) + Random.nextLong(retryBaseDelayMs / 2 + 1)

    fun fileName(item: ShortVideoItem, sourceId: Long): String =
        fileName(item, sourceId, urlExtension(item.videoUrl).takeIf { it in MEDIA_TYPES } ?: "mp4")

    private fun fileName(item: ShortVideoItem, sourceId: Long, extension: String): String {
        val title = item.title.orEmpty().replace(UNSAFE_FILE_CHARS, "_")
            .trim(' ', '_', '-').take(48).trimEnd(' ', '_', '-').ifBlank { "reel" }
        val itemKey = item.id.encodeUtf8().sha256().hex().take(32)
        return title + "_" + sourceId + "_" + itemKey + "." + extension
    }

    private fun urlExtension(url: String): String = Uri.parse(url).lastPathSegment
        ?.substringAfterLast('.', "")?.lowercase().orEmpty()

    private fun copy(
        input: InputStream,
        output: OutputStream,
        total: Long?,
        coroutineContext: CoroutineContext,
        onProgress: (Long, Long?) -> Unit,
    ): Long {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var bytes = 0L
        var lastProgressBytes = 0L
        var lastProgressTime = SystemClock.elapsedRealtime()
        try {
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                // Flush per chunk so `bytes` is the size the destination really reached: a resume
                // offset taken from a still-buffered count would leave a hole in the file.
                output.flush()
                bytes += read
                if (total != null && bytes > total) throw IOException("Reel body exceeds its declared length")
                val now = SystemClock.elapsedRealtime()
                if (lastProgressBytes == 0L || now - lastProgressTime >= PROGRESS_MS) {
                    coroutineContext.ensureActive()
                    onProgress(bytes, total)
                    lastProgressBytes = bytes
                    lastProgressTime = now
                }
            }
            coroutineContext.ensureActive()
            if (bytes == 0L) throw IOException("Incomplete reel download")
            if (total != null && bytes != total) throw PartialCopyException(bytes)
            if (bytes != lastProgressBytes) onProgress(bytes, total)
            return bytes
        } catch (e: PartialCopyException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw PartialCopyException(bytes)
        }
    }

    private companion object {
        const val SNIFF_BYTES = 512
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val PROGRESS_MS = 250L
        const val THROTTLE_ATTEMPTS = 3
        val THROTTLE_CODES = setOf(429, 503)
        val MP4_BRANDS = setOf("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "dash")
        val EBML_SIGNATURE = "1a45dfa3".decodeHex()
        val WEBM_DOCTYPE = "4282847765626d".decodeHex()
        val JPEG_SIGNATURE = "ffd8ff".decodeHex()
        val PNG_SIGNATURE = "89504e470d0a1a0a".decodeHex()
        val UNSAFE_FILE_CHARS = Regex("[^\\p{L}\\p{N} _-]")
        val ADAPTIVE_EXTENSIONS = setOf("m3u8", "mpd")
        val MANIFEST_TYPES = setOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
            "application/dash+xml",
        )
        val MEDIA_TYPES = mapOf(
            "mp4" to "video/mp4",
            "webm" to "video/webm",
            "m4v" to "video/mp4",
            "mov" to "video/quicktime",
            "mkv" to "video/x-matroska",
            "3gp" to "video/3gpp",
            "avi" to "video/x-msvideo",
            "mpg" to "video/mpeg",
            "mpeg" to "video/mpeg",
            "ogv" to "video/ogg",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "avif" to "image/avif",
        )
        val MIME_ALIASES = mapOf(
            "application/mp4" to "mp4",
            "video/x-m4v" to "m4v",
            "video/matroska" to "mkv",
        )
    }
}
