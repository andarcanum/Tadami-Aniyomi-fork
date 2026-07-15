package eu.kanade.tachiyomi.extension.anime.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.newestByVersion
import mihon.data.extension.mapper.toAnimeExtensionAvailable
import mihon.data.extension.repository.ExtensionStoreFetcher
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.model.legacyBaseUrl
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.days

internal class AnimeExtensionApi(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val storeRepository: AnimeExtensionStoreRepository = Injekt.get(),
    private val storeFetcher: ExtensionStoreFetcher = Injekt.get(),
    private val updateExtensionRepo: UpdateAnimeExtensionRepo = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun checkForUpdatesIfDue(context: Context): List<AnimeExtension.Installed>? {
        return checkForUpdates(context, fromAvailableExtensionList = true)
    }

    /**
     * The result of fetching the available extensions.
     *
     * @param extensions The extensions of the stores that responded successfully.
     * @param failedRepoUrls The repo urls of the stores that failed to respond. When this is not
     * empty the fetch is partial and consumers should not assume missing extensions were removed.
     */
    data class FetchedExtensions(
        val extensions: List<AnimeExtension.Available>,
        val failedRepoUrls: Set<String>,
    ) {
        val isComplete: Boolean get() = failedRepoUrls.isEmpty()
    }

    suspend fun findExtensions(): FetchedExtensions {
        return withIOContext {
            val result = storeFetcher.fetchExtensions(storeRepository.getAll())
            FetchedExtensions(
                extensions = result.extensions.mapNotNull { it.toAnimeExtensionAvailable() },
                failedRepoUrls = result.failedStores.map { it.legacyBaseUrl() }.toSet(),
            )
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<AnimeExtension.Installed>? {
        val nowMs = timeProvider()
        if (fromAvailableExtensionList &&
            nowMs < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            animeExtensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().extensions
        }
        lastExtCheck.set(nowMs)

        val extensionsByPkgName = extensions
            .groupBy { it.pkgName }
            .mapValues { (_, variants) -> variants.newestByVersion()!! }

        val installedExtensions = animeExtensionManager.installedExtensionsFlow.value

        val extensionsWithUpdate = mutableListOf<AnimeExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensionsByPkgName[pkgName] ?: continue

            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(
                names = extensionsWithUpdate.map { it.name },
                anime = true,
            )
        }

        return extensionsWithUpdate
    }

    fun getApkUrl(extension: AnimeExtension.Available): String {
        return if (extension.apkName.startsWith("http://") || extension.apkName.startsWith("https://")) {
            extension.apkName
        } else {
            "${extension.repoUrl}/apk/${extension.apkName}"
        }
    }
}
