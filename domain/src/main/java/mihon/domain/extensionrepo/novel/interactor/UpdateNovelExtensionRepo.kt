package mihon.domain.extensionrepo.novel.interactor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService

class UpdateNovelExtensionRepo(
    private val repository: NovelExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {

    suspend fun awaitAll() = coroutineScope {
        repository.getAll()
            .map { async { await(it) } }
            .awaitAll()
    }

    suspend fun await(repo: ExtensionRepo) {
        val newRepo = service.fetchRepoDetails(repo.baseUrl) ?: return
        if (
            repo.signingKeyFingerprint.startsWith("NOFINGERPRINT") ||
            repo.signingKeyFingerprint == newRepo.signingKeyFingerprint
        ) {
            repository.upsertRepo(newRepo)
        }
    }
}
