package mihon.domain.extensionrepo.anime.interactor
import eu.kanade.tachiyomi.util.lang.Hash
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.model.legacyBaseUrl
import mihon.domain.extensionstore.model.repoDisplayNameFallback
import mihon.domain.extensionstore.model.toExtensionStoreBaseUrl
import mihon.domain.extensionstore.model.toLegacyExtensionRepoUrl
import mihon.domain.extensionstore.toExtensionRepo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat

class CreateAnimeExtensionRepo(
    private val repository: AnimeExtensionStoreRepository,
) {
    suspend fun await(
        indexUrl: String,
        displayName: String? = null,
        forceLocalInsert: Boolean = false,
    ): Result {
        val formattedIndexUrl = indexUrl.toHttpUrlOrNull()?.toString() ?: return Result.InvalidUrl

        val insertResult = repository.insert(formattedIndexUrl)
        if (insertResult.isSuccess) {
            if (!displayName.isNullOrBlank()) {
                renameInsertedStore(formattedIndexUrl, displayName)
            }
            return Result.Success
        }

        if (forceLocalInsert) {
            val localIndexUrl = normalizeForceLocalIndexUrl(formattedIndexUrl)
            repository.insertFromPreference(
                localIndexUrl,
                displayName?.takeIf { it.isNotBlank() } ?: extractRepoName(localIndexUrl),
            )
            return Result.Success
        }

        return handleInsertionError(formattedIndexUrl, displayName, insertResult.exceptionOrNull())
    }

    private fun normalizeForceLocalIndexUrl(indexUrl: String): String {
        return if (indexUrl.trimEnd('/').endsWith("/index.min.json", ignoreCase = true)) {
            indexUrl.toLegacyExtensionRepoUrl()
        } else {
            indexUrl
        }
    }

    private suspend fun renameInsertedStore(indexUrl: String, displayName: String) {
        // insert() persists the canonical url (e.g. .../index.min.json becomes .../repo.json), so the
        // pasted url will not match; compare the suffix-stripped bases instead.
        val base = indexUrl.toExtensionStoreBaseUrl().trimEnd('/')
        val store = repository.getAll().find { it.legacyBaseUrl().trimEnd('/') == base } ?: return
        repository.setCustomName(store.indexUrl, displayName.takeIf { it != store.name })
    }

    private fun extractRepoName(url: String): String {
        // GitHub -> owner/repo; other hosts -> first two path segments; host as last resort.
        return try {
            url.repoDisplayNameFallback()
        } catch (_: Exception) {
            url
        }
    }

    private suspend fun handleInsertionError(
        indexUrl: String,
        displayName: String?,
        failure: Throwable?,
    ): Result {
        val stores = repository.getAll()
        if (stores.any { it.indexUrl == indexUrl }) {
            return Result.RepoAlreadyExists
        }
        val fingerprint = "NOFINGERPRINT-${Hash.sha256(indexUrl)}"
        val matching = stores.find { it.signingKey == fingerprint }
        if (matching != null) {
            val newRepo = ExtensionRepo(
                baseUrl = indexUrl.toExtensionStoreBaseUrl(),
                name = displayName?.takeIf { it.isNotBlank() } ?: extractRepoName(indexUrl),
                shortName = null,
                website = indexUrl,
                signingKeyFingerprint = fingerprint,
            )
            return Result.DuplicateFingerprint(matching.toExtensionRepo(), newRepo)
        }
        logcat(LogPriority.WARN, failure) { "Failed to add anime extension store $indexUrl" }
        // A DNS failure, a non-2xx response or a malformed index is not a bad url; telling the user
        // the url is invalid sends them to fix something that is not broken.
        return if (failure != null) Result.Error else Result.InvalidUrl
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result
    }
}
