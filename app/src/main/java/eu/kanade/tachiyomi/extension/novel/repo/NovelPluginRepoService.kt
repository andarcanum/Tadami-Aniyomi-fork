package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.extension.novel.NovelIndexFormatDecoder
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

interface NovelPluginRepoServiceContract {
    suspend fun fetch(repoUrl: String): List<NovelPluginRepoEntry>

    /**
     * Fetches the first index candidate the repo actually publishes. Repos use different file
     * names (plugins.min.json vs index.min.json), so individual candidate misses are expected;
     * this keeps it to one request per repo when the first candidate succeeds.
     */
    suspend fun fetchFirstAvailable(repoBaseUrl: String): List<NovelPluginRepoEntry> {
        for (candidate in resolveNovelPluginRepoIndexUrls(repoBaseUrl)) {
            val entries = fetch(candidate)
            if (entries.isNotEmpty()) return entries
        }
        return emptyList()
    }
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
                        val payload = NovelIndexFormatDecoder.decodeToPluginJson(response.body.bytes())
                        if (payload.isNullOrBlank()) {
                            emptyList()
                        } else {
                            runCatching { parser.parse(payload) }
                                .onFailure { error ->
                                    logcat(LogPriority.ERROR, error) { "Failed to parse novel plugin repo url=$url" }
                                }
                                .getOrDefault(emptyList())
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A single candidate miss is expected: repos publish different index file names
                // and the caller falls back to the next candidate (see fetchFirstAvailable).
                logcat(LogPriority.WARN, e) { "Failed to fetch novel plugin repo url=$url" }
                emptyList()
            }
        }
    }
}
