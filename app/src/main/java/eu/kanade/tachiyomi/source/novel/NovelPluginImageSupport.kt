package eu.kanade.tachiyomi.source.novel

import android.app.Application
import android.os.Looper
import eu.kanade.tachiyomi.extension.novel.NovelPluginId
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.net.URI
import java.net.URLDecoder

data class NovelPluginImage(
    val url: String,
) {
    companion object {
        fun isSupported(url: String): Boolean = NovelPluginImageUrlParser.parse(url) != null
    }
}

data class NovelPluginImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val cacheKey: String? = null,
)

interface NovelPluginImageSource {
    suspend fun fetchImage(imageRef: String): NovelPluginImagePayload?
}

internal data class NovelPluginImageRequest(
    val pluginId: String,
    val imageRef: String,
)

internal object NovelPluginImageUrlParser {
    private val supportedSchemes = setOf("novelimg", "heximg")

    fun parse(url: String): NovelPluginImageRequest? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in supportedSchemes) return null

        val pluginId = uri.host?.trim().orEmpty()
        if (pluginId.isBlank()) return null

        val reference = extractRef(uri)
        if (reference.isNullOrBlank()) return null

        return NovelPluginImageRequest(pluginId = pluginId, imageRef = reference)
    }

    private fun extractRef(uri: URI): String? {
        val queryValue = extractQueryParameter(uri.rawQuery, "ref")
            ?.let(::decodeComponent)
            ?.trim()
            .orEmpty()
        if (queryValue.isNotBlank()) return queryValue

        val pathValue = uri.rawPath
            ?.removePrefix("/")
            ?.takeIf { it.isNotBlank() }
            ?.let(::decodeComponent)
            ?.trim()
            .orEmpty()
        return pathValue.ifBlank { null }
    }

    private fun extractQueryParameter(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery
            .split('&')
            .firstNotNullOfOrNull { segment ->
                val parts = segment.split('=', limit = 2)
                val currentKey = parts.firstOrNull()?.let(::decodeComponent)?.trim()
                if (!currentKey.equals(key, ignoreCase = true)) return@firstNotNullOfOrNull null
                parts.getOrNull(1).orEmpty()
            }
    }

    private fun decodeComponent(value: String): String {
        return runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }
            .getOrDefault(value)
    }
}

internal object HexNovelImageOnDemandDecoder {
    private const val pluginId = "hexnovels"
    private const val maxSourceBytes = 20L * 1024L * 1024L
    private val parserJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    internal data class HexNovelImageRef(
        val imageUrl: String,
        val secretKey: String,
        val cacheKey: String?,
    )

    internal enum class HexImageEncryptionMode {
        XOR,
        SEC,
    }

    fun canHandle(request: NovelPluginImageRequest): Boolean {
        return request.pluginId.equals(pluginId, ignoreCase = true)
    }

    suspend fun decode(
        request: NovelPluginImageRequest,
        networkHelper: NetworkHelper,
    ): NovelPluginImagePayload? {
        if (!canHandle(request)) return null

        val parsedRef = parseRef(request.imageRef) ?: return null
        val mode = detectEncryptionMode(parsedRef.imageUrl) ?: return null
        val fetchUrl = when (mode) {
            HexImageEncryptionMode.XOR -> parsedRef.imageUrl
            HexImageEncryptionMode.SEC -> toXorVariantUrl(parsedRef.imageUrl)
        }

        val response = runCatching {
            networkHelper.client.newCall(GET(fetchUrl)).awaitSuccess()
        }.getOrNull() ?: return null

        response.use { safeResponse ->
            val sourceBytes = runCatching { safeResponse.body.bytes() }.getOrNull() ?: return null
            if (sourceBytes.isEmpty()) return null
            if (sourceBytes.size.toLong() > maxSourceBytes) return null

            val decodedBytes = xorDecode(sourceBytes, parsedRef.secretKey)
            if (decodedBytes.isEmpty()) return null

            return NovelPluginImagePayload(
                bytes = decodedBytes,
                mimeType = detectMimeType(decodedBytes, parsedRef.imageUrl),
                cacheKey = parsedRef.cacheKey,
            )
        }
    }

    internal fun parseRef(imageRef: String): HexNovelImageRef? {
        val normalized = imageRef.trim()
        if (normalized.isBlank()) return null

        val root = runCatching { parserJson.parseToJsonElement(normalized) as? JsonObject }
            .getOrNull()
            ?: return null

        val imageUrl = root.string("imageUrl").orEmpty().trim()
        val secretKey = root.string("secretKey").orEmpty().trim()
        if (imageUrl.isBlank() || secretKey.isBlank()) return null

        val cacheKey = root.string("cacheKey")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: imageUrl

        return HexNovelImageRef(
            imageUrl = imageUrl,
            secretKey = secretKey,
            cacheKey = cacheKey,
        )
    }

    internal fun detectEncryptionMode(imageUrl: String): HexImageEncryptionMode? {
        val fileName = imageUrl
            .substringBefore('?')
            .substringAfterLast('/')
            .substringBefore('.')
        if (fileName.length != 36) return null

        return when (fileName[14].lowercaseChar()) {
            'x' -> HexImageEncryptionMode.XOR
            's' -> HexImageEncryptionMode.SEC
            else -> null
        }
    }

    internal fun toXorVariantUrl(imageUrl: String): String {
        val queryIndex = imageUrl.indexOf('?')
        val base = if (queryIndex >= 0) imageUrl.substring(0, queryIndex) else imageUrl
        val query = if (queryIndex >= 0) imageUrl.substring(queryIndex) else ""

        val slashIndex = base.lastIndexOf('/')
        if (slashIndex < 0 || slashIndex == base.lastIndex) return imageUrl

        val prefix = base.substring(0, slashIndex + 1)
        val fileName = base.substring(slashIndex + 1)
        val dotIndex = fileName.lastIndexOf('.')
        val nameWithoutExt = if (dotIndex >= 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex >= 0) fileName.substring(dotIndex) else ""
        if (nameWithoutExt.length < 15) return imageUrl

        val patchedName = buildString(nameWithoutExt.length) {
            append(nameWithoutExt.substring(0, 14))
            append('x')
            append(nameWithoutExt.substring(15))
        }
        return "$prefix$patchedName$extension$query"
    }

    internal fun xorDecode(sourceBytes: ByteArray, secretKey: String): ByteArray {
        val keyBytes = secretKey.toByteArray(Charsets.UTF_8)
        if (keyBytes.isEmpty()) return sourceBytes

        val decoded = ByteArray(sourceBytes.size)
        for (index in sourceBytes.indices) {
            decoded[index] = (sourceBytes[index].toInt() xor keyBytes[index % keyBytes.size].toInt()).toByte()
        }
        return decoded
    }

    internal fun detectMimeType(bytes: ByteArray, sourceUrl: String): String {
        if (
            bytes.size > 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return "image/jpeg"
        }
        if (
            bytes.size > 12 &&
            bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte()
        ) {
            return "image/webp"
        }
        if (bytes.size > 4 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) {
            return "image/gif"
        }

        val lowerPath = sourceUrl.substringBefore('?').lowercase()
        return when {
            lowerPath.endsWith(".png") -> "image/png"
            lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") -> "image/jpeg"
            lowerPath.endsWith(".webp") -> "image/webp"
            lowerPath.endsWith(".gif") -> "image/gif"
            lowerPath.endsWith(".bmp") -> "image/bmp"
            lowerPath.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    private fun JsonObject.string(key: String): String? {
        return (this[key] as? JsonPrimitive)
            ?.contentOrNull
    }
}

object NovelPluginImageResolver {
    private const val timeoutMs = 20_000L
    private const val maxParallelDecodes = 3
    private const val maxMemoryBytes = 24L * 1024L * 1024L
    private const val maxDiskBytes = 96L * 1024L * 1024L
    private const val diskCacheDirName = "novel_plugin_image_cache"
    private const val defaultMimeType = "application/octet-stream"

    private val sourceManager by lazy { Injekt.get<NovelSourceManager>() }
    private val networkHelper by lazy { Injekt.get<NetworkHelper>() }
    private val app by lazy { Injekt.get<Application>() }
    private val decodeLimiter = Semaphore(maxParallelDecodes)
    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<NovelPluginImagePayload?>>()

    private val memoryCacheLock = Any()
    private val memoryCache = LinkedHashMap<String, NovelPluginImagePayload>(0, 0.75f, true)
    private var memoryCacheBytes = 0L

    private val diskCacheLock = Any()
    private val diskCacheDir by lazy {
        File(app.cacheDir, diskCacheDirName).apply { mkdirs() }
    }

    suspend fun resolve(url: String): NovelPluginImagePayload? {
        val request = NovelPluginImageUrlParser.parse(url) ?: return null
        val requestKey = buildCacheKey(request.pluginId, request.imageRef)

        readFromMemoryCache(requestKey)?.let { return it }
        readFromDiskCache(requestKey)?.let { cached ->
            writeToMemoryCache(requestKey, cached)
            return cached
        }

        val inFlightJob = inFlightMutex.withLock {
            inFlight[requestKey] ?: resolverScope.async {
                fetchAndCache(request = request, requestKey = requestKey)
            }.also { deferred ->
                inFlight[requestKey] = deferred
                deferred.invokeOnCompletion {
                    resolverScope.launch {
                        inFlightMutex.withLock {
                            if (inFlight[requestKey] === deferred) {
                                inFlight.remove(requestKey)
                            }
                        }
                    }
                }
            }
        }

        return withTimeoutOrNull(timeoutMs) {
            inFlightJob.await()
        }
    }

    fun resolveBlocking(url: String): NovelPluginImagePayload? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        return runBlocking(Dispatchers.IO) { resolve(url) }
    }

    private suspend fun fetchAndCache(
        request: NovelPluginImageRequest,
        requestKey: String,
    ): NovelPluginImagePayload? {
        val sourceId = NovelPluginId.toSourceId(request.pluginId)
        val source = sourceManager.get(sourceId) as? NovelPluginImageSource

        val payload = withTimeoutOrNull(timeoutMs) {
            decodeLimiter.withPermit {
                HexNovelImageOnDemandDecoder.decode(request, networkHelper)
                    ?: source?.fetchImage(request.imageRef)
            }
        } ?: return null

        val normalized = payload.copy(
            mimeType = payload.mimeType.trim().ifBlank { defaultMimeType },
        )

        val keys = linkedSetOf(requestKey)
        payload.cacheKey
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { pluginKey -> keys.add(buildCacheKey(request.pluginId, pluginKey)) }

        keys.forEach { key ->
            writeToMemoryCache(key, normalized)
            writeToDiskCache(key, normalized)
        }

        return normalized
    }

    private fun buildCacheKey(pluginId: String, value: String): String {
        return "${pluginId.lowercase()}:${value.trim()}"
    }

    private fun readFromMemoryCache(key: String): NovelPluginImagePayload? {
        return synchronized(memoryCacheLock) {
            memoryCache[key]
        }
    }

    private fun writeToMemoryCache(key: String, payload: NovelPluginImagePayload) {
        val payloadSize = payload.bytes.size.toLong()
        if (payloadSize <= 0L || payloadSize > maxMemoryBytes) return

        synchronized(memoryCacheLock) {
            val previous = memoryCache.put(key, payload)
            memoryCacheBytes += payloadSize - (previous?.bytes?.size?.toLong() ?: 0L)
            trimMemoryCacheLocked()
        }
    }

    private fun trimMemoryCacheLocked() {
        val iterator = memoryCache.entries.iterator()
        while (memoryCacheBytes > maxMemoryBytes && iterator.hasNext()) {
            val entry = iterator.next()
            memoryCacheBytes -= entry.value.bytes.size.toLong()
            iterator.remove()
        }
        if (memoryCacheBytes < 0L) {
            memoryCacheBytes = 0L
        }
    }

    private fun readFromDiskCache(key: String): NovelPluginImagePayload? {
        return synchronized(diskCacheLock) {
            val payloadFile = payloadFileForKey(key)
            if (!payloadFile.exists()) return@synchronized null
            val bytes = runCatching { payloadFile.readBytes() }
                .getOrNull()
                ?: run {
                    payloadFile.delete()
                    mimeFileForKey(key).delete()
                    return@synchronized null
                }

            val mimeType = runCatching {
                mimeFileForKey(key).takeIf { it.exists() }?.readText(Charsets.UTF_8).orEmpty()
            }.getOrNull()
                ?.trim()
                .orEmpty()
                .ifBlank { defaultMimeType }

            val now = System.currentTimeMillis()
            payloadFile.setLastModified(now)
            mimeFileForKey(key).setLastModified(now)
            NovelPluginImagePayload(
                bytes = bytes,
                mimeType = mimeType,
                cacheKey = null,
            )
        }
    }

    private fun writeToDiskCache(key: String, payload: NovelPluginImagePayload) {
        if (payload.bytes.isEmpty()) return

        synchronized(diskCacheLock) {
            runCatching {
                val payloadFile = payloadFileForKey(key)
                val mimeFile = mimeFileForKey(key)
                payloadFile.parentFile?.mkdirs()
                payloadFile.writeBytes(payload.bytes)
                mimeFile.writeText(payload.mimeType, Charsets.UTF_8)
                val now = System.currentTimeMillis()
                payloadFile.setLastModified(now)
                mimeFile.setLastModified(now)
                trimDiskCacheLocked()
            }
        }
    }

    private fun trimDiskCacheLocked() {
        val files = diskCacheDir.listFiles { file -> file.extension.equals("bin", ignoreCase = true) }
            ?.toMutableList()
            ?: return

        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxDiskBytes) return

        files.sortBy { it.lastModified() }
        for (file in files) {
            if (!file.exists()) continue
            val deletedSize = file.length()
            file.delete()
            File(file.parentFile, "${file.nameWithoutExtension}.mime").delete()
            totalSize -= deletedSize
            if (totalSize <= maxDiskBytes) break
        }
    }

    private fun payloadFileForKey(key: String): File {
        val hash = DiskUtil.hashKeyForDisk(key)
        return File(diskCacheDir, "$hash.bin")
    }

    private fun mimeFileForKey(key: String): File {
        val hash = DiskUtil.hashKeyForDisk(key)
        return File(diskCacheDir, "$hash.mime")
    }
}
