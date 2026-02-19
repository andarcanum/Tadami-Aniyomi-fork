package mihon.domain.extensionrepo.novel.interactor

import eu.kanade.tachiyomi.util.lang.Hash
import logcat.LogPriority
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat

class CreateNovelExtensionRepo(
    private val repository: NovelExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {
    private val indexSuffix = "/index.min.json"
    private val pluginsSuffix = "/plugins.min.json"

    suspend fun await(url: String): Result {
        val normalizedUrl = url.toHttpUrlOrNull()?.toString() ?: return Result.InvalidUrl

        return when {
            normalizedUrl.endsWith(indexSuffix) -> {
                // Mihon-style extension repo (expects repo.json at baseUrl)
                val baseUrl = normalizedUrl.removeSuffix(indexSuffix)
                service.fetchRepoDetails(baseUrl)?.let { insert(it) } ?: Result.InvalidUrl
            }
            normalizedUrl.endsWith(pluginsSuffix) -> {
                // LNReader-style novel plugin index repo (plugins.min.json)
                val baseUrl = normalizedUrl.removeSuffix(pluginsSuffix)
                val fingerprint = "NOFINGERPRINT-${Hash.sha256(baseUrl)}"
                insert(
                    ExtensionRepo(
                        baseUrl = baseUrl,
                        name = baseUrl,
                        shortName = null,
                        website = baseUrl,
                        signingKeyFingerprint = fingerprint,
                    ),
                )
            }
            else -> Result.InvalidUrl
        }
    }

    private suspend fun insert(repo: ExtensionRepo): Result {
        return try {
            repository.insertRepo(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
            )
            Result.Success
        } catch (e: SaveExtensionRepoException) {
            logcat(LogPriority.WARN, e) { "SQL Conflict attempting to add new novel repository ${repo.baseUrl}" }
            return handleInsertionError(repo)
        }
    }

    private suspend fun handleInsertionError(repo: ExtensionRepo): Result {
        val repoExists = repository.getRepo(repo.baseUrl)
        if (repoExists != null) {
            return Result.RepoAlreadyExists
        }
        val matchingFingerprintRepo = repository.getRepoBySigningKeyFingerprint(repo.signingKeyFingerprint)
        if (matchingFingerprintRepo != null) {
            return Result.DuplicateFingerprint(matchingFingerprintRepo, repo)
        }
        return Result.Error
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result
    }
}
