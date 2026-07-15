package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.newestByVersion
import mihon.data.extension.mapper.toMangaExtensionAvailable
import mihon.data.extension.repository.ExtensionStoreFetcher
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.legacyBaseUrl
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.days

internal class MangaExtensionApi(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val storeRepository: MangaExtensionStoreRepository = Injekt.get(),
    private val storeFetcher: ExtensionStoreFetcher = Injekt.get(),
    private val updateExtensionRepo: UpdateMangaExtensionRepo = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun checkForUpdatesIfDue(context: Context): List<MangaExtension.Installed>? {
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
        val extensions: List<MangaExtension.Available>,
        val failedRepoUrls: Set<String>,
    ) {
        val isComplete: Boolean get() = failedRepoUrls.isEmpty()
    }

    suspend fun findExtensions(): FetchedExtensions {
        return withIOContext {
            val result = storeFetcher.fetchExtensions(storeRepository.getAll())
            FetchedExtensions(
                extensions = result.extensions.mapNotNull { it.toMangaExtensionAvailable() },
                failedRepoUrls = result.failedStores.map { it.legacyBaseUrl() }.toSet(),
            )
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? {
        val nowMs = timeProvider()
        if (fromAvailableExtensionList &&
            nowMs < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().extensions
        }
        lastExtCheck.set(nowMs)

        val extensionsByPkgName = extensions
            .groupBy { it.pkgName }
            .mapValues { (_, variants) -> variants.newestByVersion()!! }

        val installedExtensions = extensionManager.installedExtensionsFlow.value

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
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
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        return extensionsWithUpdate
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        return if (extension.apkName.startsWith("http://") || extension.apkName.startsWith("https://")) {
            extension.apkName
        } else {
            "${extension.repoUrl}/apk/${extension.apkName}"
        }
    }
}
