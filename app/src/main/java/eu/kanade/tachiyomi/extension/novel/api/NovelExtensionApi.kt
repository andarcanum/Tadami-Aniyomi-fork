package eu.kanade.tachiyomi.extension.novel.api

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoEntry
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUpdateInteractor
import eu.kanade.tachiyomi.extension.novel.repo.resolveNovelPluginRepoIndexUrls
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import kotlin.time.Duration.Companion.days

internal class NovelExtensionApi(
    private val getExtensionRepo: GetNovelExtensionRepo = Injekt.get(),
    private val updateExtensionRepo: UpdateNovelExtensionRepo = Injekt.get(),
    private val repoUpdateInteractor: NovelPluginRepoUpdateInteractor = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val timeProvider: () -> Long = { Instant.now().toEpochMilli() },
) {

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_novel_ext_check", 0)
    }

    suspend fun checkForUpdates(
        fromAvailableExtensionList: Boolean = false,
    ): List<NovelPluginRepoEntry>? {
        if (fromAvailableExtensionList &&
            timeProvider() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        updateExtensionRepo.awaitAll()

        val repoUrls = getExtensionRepo.getAll()
            .flatMap { resolveNovelPluginRepoIndexUrls(it.baseUrl) }
            .distinct()

        val updates = repoUpdateInteractor.findUpdates(repoUrls)
        if (!fromAvailableExtensionList) {
            lastExtCheck.set(timeProvider())
        }
        sourcePreferences.novelExtensionUpdatesCount().set(updates.size)
        return updates
    }
}
