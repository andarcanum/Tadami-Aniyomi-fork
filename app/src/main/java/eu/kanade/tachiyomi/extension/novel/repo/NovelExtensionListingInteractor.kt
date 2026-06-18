package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.extension.novel.NovelExtensionUpdateChecker
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
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
    private val repository: NovelPluginRepository = Injekt.get(),
    private val updateChecker: NovelExtensionUpdateChecker = Injekt.get(),
) {
    suspend fun fetch(): NovelExtensionListing {
        val installed = repository.getAll().map { it.toRepoEntry() }
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

private fun NovelPlugin.Installed.toRepoEntry(): NovelPluginRepoEntry {
    return NovelPluginRepoEntry(
        id = id,
        name = name,
        site = site,
        lang = lang,
        version = versionCode,
        url = url,
        iconUrl = iconUrl,
        customJsUrl = customJs,
        customCssUrl = customCss,
        hasSettings = hasSettings,
        sha256 = sha256,
    )
}
