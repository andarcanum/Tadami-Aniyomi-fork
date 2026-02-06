package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

interface NovelPluginRepoServiceContract {
    suspend fun fetch(repoUrl: String): List<NovelPluginRepoEntry>
}

class NovelPluginRepoService(
    private val client: OkHttpClient,
    private val parser: NovelPluginRepoParser,
) : NovelPluginRepoServiceContract {
    override suspend fun fetch(repoUrl: String): List<NovelPluginRepoEntry> {
        return fetchRepoEntries(repoUrl)
    }

    suspend fun fetchRepoEntries(url: String): List<NovelPluginRepoEntry> {
        return withIOContext {
            try {
                client.newCall(GET(url))
                    .awaitSuccess()
                    .use { response ->
                        val payload = response.body?.string().orEmpty()
                        if (payload.isBlank()) {
                            emptyList()
                        } else {
                            parser.parse(payload)
                        }
                    }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch novel plugin repo" }
                emptyList()
            }
        }
    }
}
