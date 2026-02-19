package mihon.domain.extensionrepo.novel.interactor

import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository

class GetNovelExtensionRepoCount(
    private val repository: NovelExtensionRepoRepository,
) {
    fun subscribe() = repository.getCount()
}
