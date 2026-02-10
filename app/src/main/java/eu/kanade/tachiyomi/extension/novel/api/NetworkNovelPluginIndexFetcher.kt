package eu.kanade.tachiyomi.extension.novel.api

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class NetworkNovelPluginIndexFetcher(
    private val client: OkHttpClient,
) : NovelPluginIndexFetcher {
    override suspend fun fetch(repoUrl: String): String {
        return withContext(Dispatchers.IO) {
            val baseUrl = repoUrl.trimEnd('/')
            val candidates = if (baseUrl.endsWith(".json", ignoreCase = true)) {
                listOf(baseUrl)
            } else {
                listOf(
                    "$baseUrl/plugins.min.json",
                    "$baseUrl/index.min.json",
                )
            }

            var lastError: Exception? = null

            for (candidate in candidates) {
                try {
                    val response = client.newCall(GET(candidate)).awaitSuccess()
                    return@withContext response.body?.string().orEmpty()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                }
            }

            throw checkNotNull(lastError)
        }
    }
}
