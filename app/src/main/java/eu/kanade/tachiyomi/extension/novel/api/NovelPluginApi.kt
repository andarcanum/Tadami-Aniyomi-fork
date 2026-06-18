package eu.kanade.tachiyomi.extension.novel.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelPluginApi(
    private val repoProvider: NovelPluginRepoProvider,
    private val fetcher: NovelPluginIndexFetcher,
    private val parser: NovelPluginIndexParser,
) : NovelPluginApiFacade {
    override suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available> {
        return withContext(Dispatchers.IO) {
            val repos = repoProvider.getAll()
            repos.flatMap { repo ->
                fetchPluginsFromRepo(repo).map { plugin ->
                    plugin.copy(
                        repoName = repo.name.ifBlank { repo.shortName ?: repo.baseUrl },
                    )
                }
            }
        }
    }

    private suspend fun fetchPluginsFromRepo(repo: ExtensionRepo): List<NovelPlugin.Available> {
        return try {
            val payload = fetcher.fetch(repo.baseUrl)
            parser.parse(payload, repo.baseUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch novel plugins from ${repo.baseUrl}" }
            emptyList()
        }
    }
}

interface NovelPluginRepoProvider {
    suspend fun getAll(): List<ExtensionRepo>
}

interface NovelPluginApiFacade {
    suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available>
}

interface NovelPluginIndexFetcher {
    suspend fun fetch(repoUrl: String): String
}
