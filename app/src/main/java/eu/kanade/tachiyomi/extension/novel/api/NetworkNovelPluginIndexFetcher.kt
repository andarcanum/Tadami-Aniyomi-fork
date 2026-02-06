package eu.kanade.tachiyomi.extension.novel.api

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class NetworkNovelPluginIndexFetcher(
    private val client: OkHttpClient,
) : NovelPluginIndexFetcher {
    override suspend fun fetch(repoUrl: String): String {
        return withContext(Dispatchers.IO) {
            val baseUrl = repoUrl.trimEnd('/')
            val targetUrl = if (baseUrl.endsWith(".json")) {
                baseUrl
            } else {
                "$baseUrl/plugins.min.json"
            }
            val response = client.newCall(GET(targetUrl)).awaitSuccess()
            response.body?.string().orEmpty()
        }
    }
}
