package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.extension.novel.NovelExtensionUpdateChecker
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class NovelExtensionListing(
    val updates: List<NovelPluginRepoEntry>,
    val installed: List<NovelPluginRepoEntry>,
    val available: List<NovelPluginRepoEntry>,
)

class NovelExtensionListingInteractor(
    private val getExtensionRepo: GetNovelExtensionRepo = Injekt.get(),
    private val repoService: NovelPluginRepoServiceContract = Injekt.get(),
    private val storage: NovelPluginStorage = Injekt.get(),
    private val updateChecker: NovelExtensionUpdateChecker = Injekt.get(),
) {
    suspend fun fetch(): NovelExtensionListing {
        val installed = storage.getAll().map { it.entry }
        val available = getExtensionRepo.getAll()
            .flatMap { repo ->
                resolveNovelPluginRepoIndexUrls(repo.baseUrl)
                    .flatMap { repoService.fetch(it) }
            }
            .groupBy { it.id }
            .mapNotNull { (_, entries) -> entries.maxByOrNull { it.version } }

        val updates = updateChecker.findUpdates(installed, available)
        val installedIds = installed.map { it.id }.toSet()
        val availableOnly = available.filter { it.id !in installedIds }

        return NovelExtensionListing(
            updates = updates,
            installed = installed,
            available = availableOnly,
        )
    }
}
