package mihon.domain.extensionrepo.anime.interactor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import tachiyomi.core.common.util.system.logcat

class UpdateAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
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
                    "Skipping anime extension repo refresh for ${repo.baseUrl}"
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
