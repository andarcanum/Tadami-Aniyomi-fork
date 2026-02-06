package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext

class NovelPluginDownloader(
    private val client: OkHttpClient,
    private val factory: NovelPluginPackageFactory,
) : NovelPluginDownloaderContract {
    override suspend fun download(entry: NovelPluginRepoEntry): Result<NovelPluginPackage> {
        return withIOContext {
            try {
                val script = fetchBytes(entry.url)
                val customJs = entry.customJsUrl?.let { fetchBytes(it) }
                val customCss = entry.customCssUrl?.let { fetchBytes(it) }
                factory.create(entry, script, customJs, customCss)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun fetchBytes(url: String): ByteArray {
        return client.newCall(GET(url))
            .awaitSuccess()
            .use { response -> response.body?.bytes() ?: ByteArray(0) }
    }
}
