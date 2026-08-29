package eu.kanade.tachiyomi.extension.novel.api

import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoEntry
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUpdateInteractor
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
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val timeProvider: () -> Long = { Instant.now().toEpochMilli() },
) {

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_novel_ext_check"), 0)
    }

    suspend fun checkForUpdates(
        fromAvailableExtensionList: Boolean = false,
    ): List<NovelPluginRepoEntry>? {
        // The 24h budget belongs to the fetching call; a screen-initiated check reuses a list it
        // already has and must neither be gated nor stamp the timestamp.
        if (!fromAvailableExtensionList &&
            timeProvider() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        updateExtensionRepo.awaitAll()

        // Pass the repo base URLs; each repo is fetched once via its first available candidate.
        val repoUrls = getExtensionRepo.getAll()
            .map { it.indexUrl?.takeIf { url -> url.isNotBlank() } ?: it.baseUrl }
            .distinct()

        val updates = repoUpdateInteractor.findUpdates(repoUrls)
        if (!fromAvailableExtensionList) {
            lastExtCheck.set(timeProvider())
        }
        // B8: the badge pref (novelExtensionUpdatesCount) is written only by the screen model's
        // classifier path and the auto-update runner; this fetch must not touch it.
        return updates
    }
}
