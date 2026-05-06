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
    private val pluginsSuffixes = setOf("/plugins.min.json", "/plugins.json")

    suspend fun await(url: String, displayName: String? = null): Result {
        val normalizedUrl = url.toHttpUrlOrNull()?.toString() ?: return Result.InvalidUrl

        return when {
            normalizedUrl.endsWith(indexSuffix) -> {
                // Mihon-style extension repo (expects repo.json at baseUrl)
                val baseUrl = normalizedUrl.removeSuffix(indexSuffix)
                service.fetchRepoDetails(baseUrl)
                    ?.let { repo ->
                        insert(
                            repo.copy(
                                name = displayName.takeIf { !it.isNullOrBlank() } ?: repo.name,
                            ),
                        )
                    }
                    ?: Result.InvalidUrl
            }
            pluginsSuffixes.any { normalizedUrl.endsWith(it) } -> {
                // LNReader-style novel plugin index repo
                val suffix = pluginsSuffixes.first { normalizedUrl.endsWith(it) }
                val baseUrl = normalizedUrl.removeSuffix(suffix)
                val fingerprint = "NOFINGERPRINT-${Hash.sha256(baseUrl)}"
                val name = displayName.takeIf { !it.isNullOrBlank() } ?: extractRepoName(baseUrl)
                insert(
                    ExtensionRepo(
                        baseUrl = baseUrl,
                        name = name,
                        shortName = null,
                        website = baseUrl,
                        signingKeyFingerprint = fingerprint,
                    ),
                )
            }
            else -> Result.InvalidUrl
        }
    }

    private fun extractRepoName(baseUrl: String): String {
        return try {
            val uri = java.net.URI(baseUrl)
            val segments = uri.path?.trim('/')?.split("/").orEmpty()
            when {
                uri.host == "raw.githubusercontent.com" && segments.size >= 2 ->
                    "${segments[0]}/${segments[1]}"
                uri.host == "github.com" && segments.size >= 2 ->
                    "${segments[0]}/${segments[1]}"
                segments.size >= 2 -> segments.take(2).joinToString("/")
                else -> baseUrl
            }
        } catch (_: Exception) {
            baseUrl
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

    /** One-time migration: update names for LNReader-style repos that show raw URLs */
    suspend fun migrateRepoNames() {
        for (repo in repository.getAll()) {
            if (!repo.signingKeyFingerprint.startsWith("NOFINGERPRINT")) continue
            if (repo.name != repo.baseUrl) continue
            val newName = extractRepoName(repo.baseUrl)
            if (newName != repo.baseUrl) {
                repository.upsertRepo(
                    repo.baseUrl,
                    newName,
                    repo.shortName,
                    repo.website,
                    repo.signingKeyFingerprint,
                )
            }
        }
    }

    companion object {
        /* prefs key to run the migration only once */
        const val MIGRATION_DONE_KEY = "NovelExtensionRepoNameMigrationDone"
    }
}
