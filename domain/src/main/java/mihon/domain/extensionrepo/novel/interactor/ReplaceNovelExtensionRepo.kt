package mihon.domain.extensionrepo.novel.interactor

import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository

class ReplaceNovelExtensionRepo(
    private val repository: NovelExtensionRepoRepository,
) {
    suspend fun await(newRepo: ExtensionRepo) {
        repository.replaceRepo(newRepo)
    }
}
