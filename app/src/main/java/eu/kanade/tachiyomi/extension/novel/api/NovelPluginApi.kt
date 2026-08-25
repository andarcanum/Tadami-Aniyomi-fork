package eu.kanade.tachiyomi.extension.novel.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
            _repoFetchErrors.value = emptyMap() // "since the last refresh" semantics
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

    private val _repoFetchErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    override val repoFetchErrors: Flow<Map<String, String>> = _repoFetchErrors.asStateFlow()

    private suspend fun fetchPluginsFromRepo(repo: ExtensionRepo): List<NovelPlugin.Available> {
        return try {
            // Prefer the canonical index url (e.g. a pasted index.pb/repo.json); fall back to
            // probing the base url candidates (plugins.*/index.*).
            val targetUrl = repo.indexUrl?.takeIf { it.isNotBlank() } ?: repo.baseUrl
            val payload = fetcher.fetch(targetUrl)
            val plugins = parser.parse(payload, targetUrl)
            _repoFetchErrors.update { it - repo.baseUrl } // recovered: drop stale error
            plugins
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch novel plugins from ${repo.baseUrl}" }
            _repoFetchErrors.update { it + (repo.baseUrl to (e.message ?: e.javaClass.simpleName)) }
            emptyList()
        }
    }
}

interface NovelPluginRepoProvider {
    suspend fun getAll(): List<ExtensionRepo>
}

interface NovelPluginApiFacade {
    suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available>

    /** Repo base URLs whose index failed to load, with the error message, since the last refresh. */
    val repoFetchErrors: Flow<Map<String, String>>
}

interface NovelPluginIndexFetcher {
    suspend fun fetch(repoUrl: String): String
}
