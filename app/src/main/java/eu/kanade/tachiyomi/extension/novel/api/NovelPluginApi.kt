package eu.kanade.tachiyomi.extension.novel.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelPluginApi(
    private val repoProvider: NovelPluginRepoProvider,
    private val fetcher: NovelPluginIndexFetcher,
    private val parser: NovelPluginIndexParser,
) : NovelPluginApiFacade {
    override suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available> {
        return withContext(Dispatchers.IO) {
            val repos = repoProvider.getAll()
            repos.flatMap { fetchPluginsFromRepo(it) }
        }
    }

    private suspend fun fetchPluginsFromRepo(repo: ExtensionRepo): List<NovelPlugin.Available> {
        val payload = fetcher.fetch(repo.baseUrl)
        return parser.parse(payload, repo.baseUrl)
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
