package eu.kanade.domain.items.episode.preview

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

abstract class BasePreviewSource : EpisodePreviewSource {

    private val networkHelper: NetworkHelper by injectLazy()
    protected val client get() = networkHelper.client

    protected val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    protected suspend fun getBody(url: String): String? = requestBody(GET(url))

    protected suspend fun requestBody(request: Request): String? {
        return try {
            client.newCall(request).awaitSuccess().use { it.body.string() }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "[$id] preview request failed: ${request.url}" }
            null
        }
    }
}
