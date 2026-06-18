package tachiyomi.data.extension.novel

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class NetworkNovelPluginDownloader(
    private val client: OkHttpClient,
) : NovelPluginDownloader {
    override suspend fun download(url: String): ByteArray {
        return withContext(Dispatchers.IO) {
            client.newCall(GET(url)).awaitSuccess().use { response ->
                response.body.bytes()
            }
        }
    }
}
