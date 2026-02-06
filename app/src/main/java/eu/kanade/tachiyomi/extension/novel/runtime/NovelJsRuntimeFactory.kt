package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

class NovelJsRuntimeFactory(
    private val networkHelper: NetworkHelper,
    private val keyValueStore: NovelPluginKeyValueStore,
    private val json: Json,
) {
    fun create(pluginId: String): NovelJsRuntime {
        val nativeApi = NativeApiImpl(pluginId, networkHelper, keyValueStore, json)
        return NovelJsRuntime(pluginId, nativeApi)
    }

    private class NativeApiImpl(
        private val pluginId: String,
        private val networkHelper: NetworkHelper,
        private val keyValueStore: NovelPluginKeyValueStore,
        private val json: Json,
    ) : NovelJsRuntime.NativeApi {

        override fun fetch(url: String, optionsJson: String?): String {
            val request = buildRequest(url, optionsJson)
            return runCatching {
                networkHelper.client.newCall(request).execute().use { response ->
                    val headers = response.headers.toMultimap()
                        .mapValues { (_, values) -> values.joinToString(",") }
                    val body = response.body?.string()
                    json.encodeToString(
                        JsFetchResponse(
                            status = response.code,
                            url = response.request.url.toString(),
                            headers = headers,
                            body = body,
                        ),
                    )
                }
            }.getOrElse { error ->
                json.encodeToString(
                    JsFetchResponse(
                        status = 0,
                        url = url,
                        headers = emptyMap(),
                        body = error.message,
                    ),
                )
            }
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
            return eu.kanade.tachiyomi.extension.novel.runtime.resolveUrl(url, base)
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

        private fun buildRequest(url: String, optionsJson: String?): Request {
            val options = optionsJson?.let { json.decodeFromString<JsFetchRequest>(it) }
                ?: JsFetchRequest()
            val builder = Request.Builder().url(url)
            options.headers.forEach { (name, value) ->
                builder.addHeader(name, value)
            }
            val method = options.method.uppercase()
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
                    builder.method(method, requestBody).build()
                }
            }
        }
    }

    @Serializable
    private data class JsFetchRequest(
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val bodyType: BodyType = BodyType.None,
        val body: String? = null,
        val formEntries: List<FormEntry>? = null,
    )

    @Serializable
    private data class FormEntry(
        val key: String,
        val value: String,
    )

    @Serializable
    private enum class BodyType {
        None,
        Text,
        Form,
    }

    @Serializable
    private data class JsFetchResponse(
        val status: Int,
        val url: String,
        val headers: Map<String, String>,
        val body: String?,
    )

    @Serializable
    private data class JsDomNode(
        val html: String,
        val text: String,
        val attrs: Map<String, String>,
    )
}
