package mihon.domain.extensionrepo.novel.interactor

import logcat.LogPriority
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat

class UpdateNovelExtensionRepo(
    private val repository: NovelExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {
    private val indexSuffix = "/index.min.json"
    private val pluginsSuffix = "/plugins.min.json"

    suspend fun awaitAll() {
        repository.getAll().forEach { repo ->
            updateRepo(repo)
        }
    }

    private suspend fun updateRepo(repo: ExtensionRepo) {
        val normalizedUrl = repo.baseUrl.toHttpUrlOrNull()
            ?.toString()
            ?: return

        val detailsBaseUrl = resolveDetailsBaseUrl(normalizedUrl) ?: return
        val refreshedRepo = runCatching { service.fetchRepoDetails(detailsBaseUrl) }
            .onFailure { error ->
                logcat(LogPriority.WARN, error) {
                    "Skipping novel extension repo refresh for ${repo.baseUrl}"
                }
            }
            .getOrNull()
            ?: return

        if (
            repo.signingKeyFingerprint.startsWith("NOFINGERPRINT") ||
            repo.signingKeyFingerprint == refreshedRepo.signingKeyFingerprint
        ) {
            try {
                repository.upsertRepo(refreshedRepo)
            } catch (e: SaveExtensionRepoException) {
                logcat(LogPriority.WARN, e) {
                    "SQL Conflict attempting to update novel repository ${refreshedRepo.baseUrl}"
                }
            }
        }
    }

    private fun resolveDetailsBaseUrl(url: String): String? {
        val trimmedUrl = url.trimEnd('/')
        return when {
            trimmedUrl.endsWith(pluginsSuffix, ignoreCase = true) -> null
            trimmedUrl.endsWith(indexSuffix, ignoreCase = true) -> trimmedUrl.dropLast(indexSuffix.length)
            trimmedUrl.endsWith(".json", ignoreCase = true) -> null
            else -> trimmedUrl
        }
    }
}
