package mihon.domain.extensionrepo.manga.interactor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import tachiyomi.core.common.util.system.logcat

class UpdateMangaExtensionRepo(
    private val repository: MangaExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {

    suspend fun awaitAll() = coroutineScope {
        repository.getAll()
            .map { async { await(it) } }
            .awaitAll()
    }

    suspend fun await(repo: ExtensionRepo) {
        val newRepo = runCatching { service.fetchRepoDetails(repo.baseUrl) }
            .onFailure { error ->
                logcat(LogPriority.WARN, error) {
                    "Skipping manga extension repo refresh for ${repo.baseUrl}"
                }
            }
            .getOrNull()
            ?: return
        if (
            repo.signingKeyFingerprint.startsWith("NOFINGERPRINT") ||
            repo.signingKeyFingerprint == newRepo.signingKeyFingerprint
        ) {
            repository.upsertRepo(newRepo)
        }
    }
}
