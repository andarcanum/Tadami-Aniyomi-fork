package mihon.domain.extensionrepo.novel.interactor

import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository

class DeleteNovelExtensionRepo(
    private val repository: NovelExtensionRepoRepository,
) {
    suspend fun await(baseUrl: String) {
        repository.deleteRepo(baseUrl)
    }
}
