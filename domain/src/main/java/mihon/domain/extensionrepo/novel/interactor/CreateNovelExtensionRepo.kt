package mihon.domain.extensionrepo.novel.interactor
import eu.kanade.tachiyomi.util.lang.Hash
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.model.legacyBaseUrl
import mihon.domain.extensionstore.model.repoDisplayNameFallback
import mihon.domain.extensionstore.model.toExtensionStoreBaseUrl
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import mihon.domain.extensionstore.toExtensionRepo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat

class CreateNovelExtensionRepo(
    private val repository: NovelExtensionStoreRepository,
) {
    private val indexSuffix = "/index.min.json"
    private val pluginsSuffixes = setOf("/plugins.min.json", "/plugins.json")

    suspend fun await(
        url: String,
        displayName: String? = null,
        forceLocalInsert: Boolean = false,
    ): Result {
        val normalizedUrl = url.toHttpUrlOrNull()?.toString() ?: return Result.InvalidUrl

        return when {
            normalizedUrl.endsWith(indexSuffix) || normalizedUrl.endsWith("/repo.json") -> {
                val insertResult = repository.insert(normalizedUrl)
                if (insertResult.isSuccess) {
                    applyDisplayName(normalizedUrl, displayName)
                    Result.Success
                } else if (forceLocalInsert) {
                    val baseUrl = normalizedUrl.toExtensionStoreBaseUrl()
                    insertPluginStyleStore(baseUrl, displayName)
                    Result.Success
                } else {
                    handleInsertionError(
                        indexUrl = normalizedUrl,
                        displayName = displayName,
                        baseUrlHint = normalizedUrl,
                        failure = insertResult.exceptionOrNull(),
                    )
                }
            }
            pluginsSuffixes.any { normalizedUrl.endsWith(it) } -> {
                val suffix = pluginsSuffixes.first { normalizedUrl.endsWith(it) }
                val baseUrl = normalizedUrl.removeSuffix(suffix)
                insertPluginStyleStore(baseUrl, displayName)
            }
            else -> {
                val insertResult = repository.insert(normalizedUrl)
                if (insertResult.isSuccess) {
                    applyDisplayName(normalizedUrl, displayName)
                    Result.Success
                } else if (forceLocalInsert) {
                    repository.insertFromPreference(
                        normalizedUrl,
                        displayName?.takeIf { it.isNotBlank() } ?: extractRepoName(normalizedUrl),
                    )
                    Result.Success
                } else {
                    handleInsertionError(
                        indexUrl = normalizedUrl,
                        displayName = displayName,
                        baseUrlHint = normalizedUrl,
                        failure = insertResult.exceptionOrNull(),
                    )
                }
            }
        }
    }

    private suspend fun insertPluginStyleStore(baseUrl: String, displayName: String?): Result {
        val fingerprint = "NOFINGERPRINT-${Hash.sha256(baseUrl)}"
        val name = displayName.takeIf { !it.isNullOrBlank() } ?: extractRepoName(baseUrl)
        val stores = repository.getAll()
        if (stores.any { it.indexUrl.trimEnd('/') == baseUrl.trimEnd('/') }) {
            return Result.RepoAlreadyExists
        }
        val matching = stores.find { it.signingKey == fingerprint }
        if (matching != null) {
            return Result.DuplicateFingerprint(
                matching.toExtensionRepo(),
                ExtensionRepo(
                    baseUrl = baseUrl,
                    name = name,
                    shortName = null,
                    website = baseUrl,
                    signingKeyFingerprint = fingerprint,
                ),
            )
        }
        repository.upsertStore(
            ExtensionStore(
                indexUrl = baseUrl,
                name = name,
                badgeLabel = name,
                signingKey = fingerprint,
                contact = ExtensionStore.Contact(website = baseUrl, discord = null),
                isLegacy = true,
                extensionListUrl = null,
            ),
        )
        return Result.Success
    }

    private suspend fun applyDisplayName(indexUrl: String, displayName: String?) {
        if (displayName.isNullOrBlank()) return
        // insert() persists the canonical url (e.g. .../index.min.json becomes .../repo.json), so the
        // pasted url will not match; compare the suffix-stripped bases instead.
        val base = indexUrl.toExtensionStoreBaseUrl().trimEnd('/')
        val store = repository.getAll().find { it.legacyBaseUrl().trimEnd('/') == base } ?: return
        repository.setCustomName(store.indexUrl, displayName.takeIf { it != store.name })
    }

    private fun extractRepoName(baseUrl: String): String {
        // GitHub -> owner/repo; other hosts -> first two path segments; host as last resort.
        return try {
            baseUrl.repoDisplayNameFallback()
        } catch (_: Exception) {
            baseUrl
        }
    }

    private suspend fun handleInsertionError(
        indexUrl: String,
        displayName: String?,
        baseUrlHint: String,
        failure: Throwable?,
    ): Result {
        val stores = repository.getAll()
        if (stores.any { it.indexUrl == indexUrl }) {
            return Result.RepoAlreadyExists
        }
        val fingerprint = "NOFINGERPRINT-${Hash.sha256(baseUrlHint)}"
        val matching = stores.find { it.signingKey == fingerprint }
        if (matching != null) {
            val newRepo = ExtensionRepo(
                baseUrl = baseUrlHint.toExtensionStoreBaseUrl(),
                name = displayName?.takeIf { it.isNotBlank() } ?: extractRepoName(baseUrlHint),
                shortName = null,
                website = baseUrlHint,
                signingKeyFingerprint = fingerprint,
            )
            return Result.DuplicateFingerprint(matching.toExtensionRepo(), newRepo)
        }
        logcat(LogPriority.WARN, failure) { "Failed to add novel extension store $indexUrl" }
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

    suspend fun migrateRepoNames() {
        for (store in repository.getAll()) {
            if (!store.signingKey.startsWith("NOFINGERPRINT")) continue
            if (store.name != store.legacyBaseUrl()) continue
            val newName = extractRepoName(store.legacyBaseUrl())
            if (newName != store.legacyBaseUrl()) {
                repository.upsertStore(store.copy(name = newName, badgeLabel = newName))
            }
        }
    }

    companion object {
        const val MIGRATION_DONE_KEY = "NovelExtensionRepoNameMigrationDone"
    }
}
