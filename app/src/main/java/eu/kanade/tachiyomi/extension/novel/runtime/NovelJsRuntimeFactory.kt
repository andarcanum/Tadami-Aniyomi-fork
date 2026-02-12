package eu.kanade.tachiyomi.extension.novel.runtime

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import java.util.Base64

class NovelJsRuntimeFactory(
    private val context: Context,
    private val networkHelper: NetworkHelper,
    private val keyValueStore: NovelPluginKeyValueStore,
    private val json: Json,
    private val domainAliasResolver: NovelDomainAliasResolver,
) {
    private val assetLoader: (String) -> String = { path ->
        context.assets.open(path).bufferedReader().use { it.readText() }
    }

    fun create(pluginId: String): NovelJsRuntime {
        val nativeApi = NativeApiImpl(pluginId, networkHelper, keyValueStore, json, domainAliasResolver)
        val moduleRegistry = NovelJsModuleRegistry(assetLoader)
        return NovelJsRuntime(pluginId, nativeApi, moduleRegistry)
    }

    private class NativeApiImpl(
        private val pluginId: String,
        private val networkHelper: NetworkHelper,
        private val keyValueStore: NovelPluginKeyValueStore,
        private val json: Json,
        private val domainAliasResolver: NovelDomainAliasResolver,
    ) : NovelJsRuntime.NativeApi {

        private val domStore = NovelJsDomStore()

        override fun fetch(url: String, optionsJson: String?): String {
            val resolvedUrl = resolveAlias(url)
            val request = buildRequest(resolvedUrl, optionsJson)
            return runCatching {
                networkHelper.client.newCall(request).execute().use { response ->
                    val headers = response.headers.toMultimap()
                        .mapValues { (_, values) -> values.joinToString(",") }
                    val responseBody = response.body
                    val bodyBytes = responseBody?.bytes()
                    val bodyCharset = responseBody?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    val body = bodyBytes?.toString(bodyCharset)
                    val bodyBase64 = bodyBytes?.let { Base64.getEncoder().encodeToString(it) }
                    json.encodeToString(
                        JsFetchResponse(
                            status = response.code,
                            url = response.request.url.toString(),
                            headers = headers,
                            body = body,
                            bodyBase64 = bodyBase64,
                        ),
                    )
                }
            }.getOrElse { error ->
                json.encodeToString(
                    JsFetchResponse(
                        status = 0,
                        url = resolvedUrl,
                        headers = emptyMap(),
                        body = error.message,
                    ),
                )
            }
        }

        override fun fetchProto(url: String, configJson: String, optionsJson: String?): String {
            val resolvedUrl = resolveAlias(url)
            val config = json.decodeFromString<JsProtoFetchConfig>(configJson)
            val options = optionsJson?.let { json.decodeFromString<JsFetchRequest>(it) }
                ?: JsFetchRequest(method = "POST")

            val requestPayload = encodeProtoRequest(config)
            val responsePayload = executeGrpcWebRequest(
                url = resolvedUrl,
                headers = options.headers,
                requestPayload = requestPayload,
            )
            return decodeProtoResponse(config.responseType, responsePayload)
        }

        override fun storageGet(key: String): String? {
            return keyValueStore.get(pluginId, key)
        }

        override fun storageSet(key: String, value: String) {
            keyValueStore.set(pluginId, key, value)
        }

        override fun storageRemove(key: String) {
            keyValueStore.remove(pluginId, key)
        }

        override fun storageClear() {
            keyValueStore.clear(pluginId)
        }

        override fun storageKeys(): String {
            return json.encodeToString(keyValueStore.keys(pluginId).toList())
        }

        override fun resolveUrl(url: String, base: String?): String {
            val resolvedBase = base?.let { resolveAlias(it) }
            val resolved = eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl(url, resolvedBase)
            return resolveAlias(resolved)
        }

        override fun getPathname(url: String): String {
            return eu.kanade.tachiyomi.extension.novel.runtime.getPathname(url)
        }

        override fun select(html: String, selector: String): String {
            val nodes = runCatching {
                Jsoup.parseBodyFragment(html)
                    .select(selector)
                    .map { element ->
                        JsDomNode(
                            html = element.outerHtml(),
                            text = element.text(),
                            attrs = element.attributes().associate { it.key to it.value },
                        )
                    }
            }.getOrElse { emptyList() }
            return json.encodeToString(nodes)
        }

        // DOM Store methods
        override fun domLoad(html: String): Int = domStore.loadDocument(html)

        override fun domSelect(handle: Int, selector: String): String =
            json.encodeToString(domStore.select(handle, selector).toList())

        override fun domParent(handle: Int): Int = domStore.parent(handle)

        override fun domChildren(handle: Int, selector: String?): String =
            json.encodeToString(domStore.children(handle, selector).toList())

        override fun domNext(handle: Int, selector: String?): Int = domStore.next(handle, selector)

        override fun domPrev(handle: Int, selector: String?): Int = domStore.prev(handle, selector)

        override fun domNextAll(handle: Int, selector: String?): String =
            json.encodeToString(domStore.nextAll(handle, selector).toList())

        override fun domPrevAll(handle: Int, selector: String?): String =
            json.encodeToString(domStore.prevAll(handle, selector).toList())

        override fun domSiblings(handle: Int, selector: String?): String =
            json.encodeToString(domStore.siblings(handle, selector).toList())

        override fun domClosest(handle: Int, selector: String): Int = domStore.closest(handle, selector)

        override fun domContents(handle: Int): String =
            json.encodeToString(domStore.contents(handle).toList())

        override fun domIs(handle: Int, selector: String): Boolean = domStore.is_(handle, selector)

        override fun domHas(handle: Int, selector: String): Boolean = domStore.has(handle, selector)

        override fun domNot(handle: Int, selector: String): String =
            json.encodeToString(domStore.not(handle, selector).toList())

        override fun domHtml(handle: Int): String = domStore.getHtml(handle)

        override fun domOuterHtml(handle: Int): String = domStore.getOuterHtml(handle)

        override fun domText(handle: Int): String = domStore.getText(handle)

        override fun domAttr(handle: Int, name: String): String? = domStore.getAttr(handle, name)

        override fun domAttrs(handle: Int): String =
            json.encodeToString(domStore.getAllAttrs(handle))

        override fun domHasClass(handle: Int, className: String): Boolean =
            domStore.hasClass(handle, className)

        override fun domData(handle: Int, key: String): String? = domStore.getData(handle, key)

        override fun domVal(handle: Int): String? = domStore.getVal(handle)

        override fun domTagName(handle: Int): String = domStore.getTagName(handle)

        override fun domIsTextNode(handle: Int): Boolean = domStore.isTextNode(handle)

        override fun domReplaceWith(handle: Int, html: String) = domStore.replaceWith(handle, html)

        override fun domRemove(handle: Int) = domStore.remove(handle)

        override fun domAddClass(handle: Int, className: String) = domStore.addClass(handle, className)

        override fun domRemoveClass(handle: Int, className: String) = domStore.removeClass(handle, className)

        override fun domRelease(handle: Int) = domStore.release(handle)

        override fun domReleaseAll() = domStore.releaseAll()

        // Console methods
        override fun consoleLog(message: String) {
            Log.d("NovelPlugin/$pluginId", message)
        }

        override fun consoleError(message: String) {
            Log.e("NovelPlugin/$pluginId", message)
        }

        override fun consoleWarn(message: String) {
            Log.w("NovelPlugin/$pluginId", message)
        }

        private fun encodeProtoRequest(config: JsProtoFetchConfig): ByteArray {
            return when (config.requestType) {
                "GetNovelRequest" -> {
                    val slug = config.requestData.jsonObject.stringValue("slug")
                        ?: error("Missing slug for GetNovelRequest")
                    ProtoBuf.encodeToByteArray(
                        WuxiaGetNovelRequest.serializer(),
                        WuxiaGetNovelRequest(slug = slug),
                    )
                }
                "GetChapterListRequest" -> {
                    val novelId = config.requestData.jsonObject.intValue("novelId")
                        ?: error("Missing novelId for GetChapterListRequest")
                    ProtoBuf.encodeToByteArray(
                        WuxiaGetChapterListRequest.serializer(),
                        WuxiaGetChapterListRequest(novelId = novelId),
                    )
                }
                "GetChapterRequest" -> {
                    val requestData = config.requestData.jsonObject
                    val chapterProperty = requestData["chapterProperty"]?.jsonObject
                        ?: error("Missing chapterProperty for GetChapterRequest")
                    val slugs = chapterProperty["slugs"]?.jsonObject
                        ?: error("Missing chapterProperty.slugs for GetChapterRequest")
                    val novelSlug = slugs.stringValue("novelSlug")
                        ?: error("Missing chapterProperty.slugs.novelSlug")
                    val chapterSlug = slugs.stringValue("chapterSlug")
                        ?: error("Missing chapterProperty.slugs.chapterSlug")

                    ProtoBuf.encodeToByteArray(
                        WuxiaGetChapterRequest.serializer(),
                        WuxiaGetChapterRequest(
                            chapterProperty = WuxiaGetChapterByProperty(
                                slugs = WuxiaByNovelAndChapterSlug(
                                    novelSlug = novelSlug,
                                    chapterSlug = chapterSlug,
                                ),
                            ),
                        ),
                    )
                }
                else -> error("Unsupported proto request type: ${config.requestType}")
            }
        }

        private fun decodeProtoResponse(responseType: String, responsePayload: ByteArray): String {
            return when (responseType) {
                "GetNovelResponse" -> {
                    val decoded = decodeWuxiaGetNovelResponse(responsePayload)
                    json.encodeToString(WuxiaGetNovelResponse.serializer(), decoded)
                }
                "GetChapterListResponse" -> {
                    val decoded = if (responsePayload.isEmpty()) {
                        WuxiaGetChapterListResponse()
                    } else {
                        ProtoBuf.decodeFromByteArray(WuxiaGetChapterListResponse.serializer(), responsePayload)
                    }
                    json.encodeToString(WuxiaGetChapterListResponse.serializer(), decoded)
                }
                "GetChapterResponse" -> {
                    val decoded = if (responsePayload.isEmpty()) {
                        WuxiaGetChapterResponse()
                    } else {
                        ProtoBuf.decodeFromByteArray(WuxiaGetChapterResponse.serializer(), responsePayload)
                    }
                    json.encodeToString(WuxiaGetChapterResponse.serializer(), decoded)
                }
                else -> error("Unsupported proto response type: $responseType")
            }
        }

        private fun decodeWuxiaGetNovelResponse(responsePayload: ByteArray): WuxiaGetNovelResponse {
            if (responsePayload.isEmpty()) return WuxiaGetNovelResponse()

            val decoded = runCatching {
                ProtoBuf.decodeFromByteArray(WuxiaGetNovelResponse.serializer(), responsePayload)
            }
            if (decoded.isSuccess) return decoded.getOrThrow()

            val fallback = runCatching {
                ProtoBuf.decodeFromByteArray(
                    WuxiaGetNovelResponseWithoutKarma.serializer(),
                    responsePayload,
                )
            }
            if (fallback.isSuccess) {
                return WuxiaGetNovelResponse(
                    item = fallback.getOrThrow().item?.toWuxiaNovelItem(),
                )
            }

            throw decoded.exceptionOrNull() ?: fallback.exceptionOrNull() ?: error("Failed to decode GetNovelResponse")
        }

        private fun executeGrpcWebRequest(
            url: String,
            headers: Map<String, String>,
            requestPayload: ByteArray,
        ): ByteArray {
            val framedPayload = frameGrpcWebMessage(requestPayload)
            val requestBody = framedPayload.toRequestBody("application/grpc-web+proto".toMediaType())
            val builder = Request.Builder()
                .url(url)
                .method("POST", requestBody)

            headers.forEach { (name, value) ->
                builder.addHeader(name, value)
            }

            val headerNames = headers.keys.map { it.lowercase() }.toSet()
            if ("content-type" !in headerNames) {
                builder.addHeader("Content-Type", "application/grpc-web+proto")
            }
            if ("accept" !in headerNames) {
                builder.addHeader("Accept", "application/grpc-web+proto")
            }
            if ("x-grpc-web" !in headerNames) {
                builder.addHeader("X-Grpc-Web", "1")
            }
            if ("x-user-agent" !in headerNames) {
                builder.addHeader("X-User-Agent", "grpc-web-javascript/0.1")
            }

            val responseBytes = networkHelper.client.newCall(builder.build())
                .execute()
                .use { response: okhttp3.Response ->
                    if (!response.isSuccessful) {
                        error("gRPC-web request failed: HTTP ${response.code}")
                    }
                    response.body?.bytes() ?: ByteArray(0)
                }

            return extractGrpcWebPayload(responseBytes)
        }

        private fun frameGrpcWebMessage(message: ByteArray): ByteArray {
            val framed = ByteArray(5 + message.size)
            framed[0] = 0
            writeInt32BigEndian(framed, 1, message.size)
            message.copyInto(framed, destinationOffset = 5)
            return framed
        }

        private fun extractGrpcWebPayload(body: ByteArray): ByteArray {
            var offset = 0
            while (offset + 5 <= body.size) {
                val flag = body[offset].toInt() and 0xFF
                val frameLength = readInt32BigEndian(body, offset + 1)
                val start = offset + 5
                val end = start + frameLength
                if (frameLength < 0 || end > body.size) break
                if ((flag and 0x80) == 0) {
                    return body.copyOfRange(start, end)
                }
                offset = end
            }
            return ByteArray(0)
        }

        private fun readInt32BigEndian(bytes: ByteArray, offset: Int): Int {
            return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
        }

        private fun writeInt32BigEndian(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = ((value ushr 24) and 0xFF).toByte()
            bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
            bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
            bytes[offset + 3] = (value and 0xFF).toByte()
        }

        private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

        private fun JsonObject.intValue(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

        private fun buildRequest(url: String, optionsJson: String?): Request {
            val options = optionsJson?.let { json.decodeFromString<JsFetchRequest>(it) }
                ?: JsFetchRequest()
            val builder = Request.Builder().url(url)
            val presentHeaders = mutableSetOf<String>()
            options.headers.forEach { (name, value) ->
                presentHeaders += name.lowercase()
                val resolvedValue = when {
                    name.equals("referer", ignoreCase = true) -> resolveAlias(value)
                    name.equals("origin", ignoreCase = true) -> resolveAlias(value)
                    else -> value
                }
                builder.addHeader(name, resolvedValue)
            }
            if ("referer" !in presentHeaders && !options.referrer.isNullOrBlank()) {
                builder.addHeader("Referer", resolveAlias(options.referrer))
                presentHeaders += "referer"
            }
            if ("origin" !in presentHeaders && !options.origin.isNullOrBlank()) {
                builder.addHeader("Origin", resolveAlias(options.origin))
                presentHeaders += "origin"
            }
            val method = options.method.uppercase()
            addDefaultHeaders(builder, url, method, presentHeaders)
            val body = options.body
            return when (method) {
                "GET", "HEAD" -> builder.method(method, null).build()
                else -> {
                    val requestBody = when (options.bodyType) {
                        BodyType.Form -> {
                            val formBuilder = okhttp3.FormBody.Builder()
                            options.formEntries?.forEach { entry ->
                                formBuilder.add(entry.key, entry.value)
                            }
                            formBuilder.build()
                        }
                        BodyType.Text -> {
                            val contentType = options.headers["Content-Type"]?.toMediaType()
                                ?: "application/json; charset=utf-8".toMediaType()
                            (body ?: "").toRequestBody(contentType)
                        }
                        else -> null
                    }
                    val normalizedBody = when {
                        requestBody != null -> requestBody
                        method.requiresRequestBody() -> ByteArray(0).toRequestBody()
                        else -> null
                    }
                    builder.method(method, normalizedBody).build()
                }
            }
        }

        private fun addDefaultHeaders(
            builder: Request.Builder,
            url: String,
            method: String,
            presentHeaders: Set<String>,
        ) {
            val httpUrl = url.toHttpUrlOrNull()
            val origin = httpUrl?.let {
                val defaultPort = (it.scheme == "https" && it.port == 443) || (it.scheme == "http" && it.port == 80)
                if (defaultPort) {
                    "${it.scheme}://${it.host}"
                } else {
                    "${it.scheme}://${it.host}:${it.port}"
                }
            }

            if ("user-agent" !in presentHeaders) {
                builder.addHeader("User-Agent", DEFAULT_USER_AGENT)
            }
            if ("accept" !in presentHeaders) {
                builder.addHeader("Accept", DEFAULT_ACCEPT)
            }
            if ("accept-language" !in presentHeaders) {
                builder.addHeader("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
            }
            if ("referer" !in presentHeaders && origin != null) {
                builder.addHeader("Referer", "$origin/")
            }
            if (
                method != "GET" &&
                method != "HEAD" &&
                "origin" !in presentHeaders &&
                origin != null
            ) {
                builder.addHeader("Origin", origin)
            }
        }

        private fun resolveAlias(url: String): String {
            return domainAliasResolver.resolve(pluginId, url)
        }

        private fun String.requiresRequestBody(): Boolean {
            return this == "POST" ||
                this == "PUT" ||
                this == "PATCH" ||
                this == "PROPPATCH" ||
                this == "REPORT"
        }

        companion object {
            private const val DEFAULT_USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
            private const val DEFAULT_ACCEPT = "*/*"
            private const val DEFAULT_ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        }
    }

    @Serializable
    private data class JsFetchRequest(
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val bodyType: BodyType = BodyType.None,
        val body: String? = null,
        val formEntries: List<FormEntry>? = null,
        val referrer: String? = null,
        val origin: String? = null,
    )

    @Serializable
    private data class FormEntry(
        val key: String,
        val value: String,
    )

    @Serializable
    private enum class BodyType {
        @SerialName("none")
        None,
        @SerialName("text")
        Text,
        @SerialName("form")
        Form,
    }

    @Serializable
    private data class JsFetchResponse(
        val status: Int,
        val url: String,
        val headers: Map<String, String>,
        val body: String?,
        val bodyBase64: String? = null,
    )

    @Serializable
    private data class JsProtoFetchConfig(
        val proto: String? = null,
        val requestType: String,
        val responseType: String,
        val requestData: JsonElement = JsonObject(emptyMap()),
    )

    @Serializable
    private data class JsDomNode(
        val html: String,
        val text: String,
        val attrs: Map<String, String>,
    )

    @Serializable
    private data class WuxiaGetNovelRequest(
        @ProtoNumber(2) val slug: String,
    )

    @Serializable
    private data class WuxiaGetChapterListRequest(
        @ProtoNumber(1) val novelId: Int,
    )

    @Serializable
    private data class WuxiaGetChapterRequest(
        @ProtoNumber(1) val chapterProperty: WuxiaGetChapterByProperty? = null,
    )

    @Serializable
    private data class WuxiaGetChapterByProperty(
        @ProtoNumber(2) val slugs: WuxiaByNovelAndChapterSlug? = null,
    )

    @Serializable
    private data class WuxiaByNovelAndChapterSlug(
        @ProtoNumber(1) val novelSlug: String,
        @ProtoNumber(2) val chapterSlug: String,
    )

    @Serializable
    private data class WuxiaGetNovelResponse(
        @ProtoNumber(1) val item: WuxiaNovelItem? = null,
    )

    @Serializable
    private data class WuxiaGetNovelResponseWithoutKarma(
        @ProtoNumber(1) val item: WuxiaNovelItemWithoutKarma? = null,
    )

    @Serializable
    private data class WuxiaGetChapterListResponse(
        @ProtoNumber(1) val items: List<WuxiaChapterGroupItem> = emptyList(),
    )

    @Serializable
    private data class WuxiaGetChapterResponse(
        @ProtoNumber(1) val item: WuxiaChapterItem? = null,
    )

    @Serializable
    private data class WuxiaNovelItem(
        @ProtoNumber(1) val id: Int? = null,
        @ProtoNumber(2) val name: String? = null,
        @ProtoNumber(3) val slug: String? = null,
        @ProtoNumber(4) val status: Int? = null,
        @ProtoNumber(8) val description: WuxiaStringValue? = null,
        @ProtoNumber(9) val synopsis: WuxiaStringValue? = null,
        @ProtoNumber(10) val coverUrl: WuxiaStringValue? = null,
        @ProtoNumber(13) val authorName: WuxiaStringValue? = null,
        @ProtoNumber(14) val karmaInfo: WuxiaNovelKarmaInfo? = null,
        @ProtoNumber(16) val genres: List<String> = emptyList(),
    )

    @Serializable
    private data class WuxiaNovelItemWithoutKarma(
        @ProtoNumber(1) val id: Int? = null,
        @ProtoNumber(2) val name: String? = null,
        @ProtoNumber(3) val slug: String? = null,
        @ProtoNumber(4) val status: Int? = null,
        @ProtoNumber(8) val description: WuxiaStringValue? = null,
        @ProtoNumber(9) val synopsis: WuxiaStringValue? = null,
        @ProtoNumber(10) val coverUrl: WuxiaStringValue? = null,
        @ProtoNumber(13) val authorName: WuxiaStringValue? = null,
        @ProtoNumber(16) val genres: List<String> = emptyList(),
    ) {
        fun toWuxiaNovelItem(): WuxiaNovelItem {
            return WuxiaNovelItem(
                id = id,
                name = name,
                slug = slug,
                status = status,
                description = description,
                synopsis = synopsis,
                coverUrl = coverUrl,
                authorName = authorName,
                karmaInfo = null,
                genres = genres,
            )
        }
    }

    @Serializable
    private data class WuxiaNovelKarmaInfo(
        @ProtoNumber(3) val maxFreeChapter: WuxiaDecimalValue? = null,
    )

    @Serializable
    private data class WuxiaChapterGroupItem(
        @ProtoNumber(2) val title: String? = null,
        @ProtoNumber(6) val chapterList: List<WuxiaChapterItem> = emptyList(),
    )

    @Serializable
    private data class WuxiaChapterItem(
        @ProtoNumber(2) val name: String? = null,
        @ProtoNumber(3) val slug: String? = null,
        @ProtoNumber(4) val number: WuxiaDecimalValue? = null,
        @ProtoNumber(5) val content: WuxiaStringValue? = null,
        @ProtoNumber(16) val relatedUserInfo: WuxiaRelatedChapterUserInfo? = null,
        @ProtoNumber(17) val offset: Int? = null,
        @ProtoNumber(18) val publishedAt: WuxiaTimestamp? = null,
    )

    @Serializable
    private data class WuxiaRelatedChapterUserInfo(
        @ProtoNumber(1) val isChapterUnlocked: WuxiaBoolValue? = null,
    )

    @Serializable
    private data class WuxiaStringValue(
        @ProtoNumber(1) val value: String? = null,
    )

    @Serializable
    private data class WuxiaBoolValue(
        @ProtoNumber(1) val value: Boolean? = null,
    )

    @Serializable
    private data class WuxiaDecimalValue(
        @ProtoNumber(1) val units: Long? = null,
        @ProtoType(ProtoIntegerType.FIXED)
        @ProtoNumber(2) val nanos: Int? = null,
    )

    @Serializable
    private data class WuxiaTimestamp(
        @ProtoNumber(1) val seconds: Long? = null,
        @ProtoNumber(2) val nanos: Int? = null,
    )
}
