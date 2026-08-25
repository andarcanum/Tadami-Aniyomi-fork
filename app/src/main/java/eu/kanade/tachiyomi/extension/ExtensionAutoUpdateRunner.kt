package eu.kanade.tachiyomi.extension

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Installs pending extension updates without any user interaction.
 *
 * The run is deliberately not tied to app startup: it is started lazily once the app is already
 * usable, after the regular update checks have filled the pending update lists.
 */
class ExtensionAutoUpdateRunner(
    private val basePreferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val mangaExtensionManager: MangaExtensionManager = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
    private val novelExtensionManager: NovelExtensionManagerProvider = NovelExtensionManagerProvider(),
) {

    suspend fun run(context: Context) {
        if (!basePreferences.autoUpdateExtensions().get()) return
        val installer = basePreferences.extensionInstaller().get()
        if (installer != BasePreferences.ExtensionInstaller.PRIVATE) return

        runCatching { updateMangaExtensions(context, installer) }
            .onFailure { logcat(LogPriority.WARN, it) { "Manga extension auto-update failed" } }
        runCatching { updateAnimeExtensions(context, installer) }
            .onFailure { logcat(LogPriority.WARN, it) { "Anime extension auto-update failed" } }
        runCatching { updateNovelExtensions(installer) }
            .onFailure { logcat(LogPriority.WARN, it) { "Novel extension auto-update failed" } }
    }

    private suspend fun updateMangaExtensions(context: Context, installer: BasePreferences.ExtensionInstaller) {
        val installed = mangaExtensionManager.installedExtensionsFlow.first()
        val sharedSkipped = installed.filter { it.hasUpdate && it.isShared }
        val candidates = installed.filter { it.hasUpdate }
            .filter { canAutoUpdateExtension(true, installer, isSharedInstall = it.isShared) }
        if (candidates.isEmpty()) {
            if (sharedSkipped.isNotEmpty()) {
                ExtensionUpdateNotifier(context).notifySharedAutoUpdateSkipped(sharedSkipped.map { it.name })
            }
            return
        }

        val updated = candidates.mapNotNull { extension ->
            val terminalStep = runCatching {
                mangaExtensionManager.updateExtension(extension).first { it.isCompleted() }
            }.getOrNull()
            extension.name.takeIf { terminalStep == InstallStep.Installed }
        }
        if (updated.isEmpty()) {
            if (sharedSkipped.isNotEmpty()) {
                ExtensionUpdateNotifier(context).notifySharedAutoUpdateSkipped(sharedSkipped.map { it.name })
            }
            return
        }

        sourcePreferences.mangaExtensionUpdatesCount()
            .set(mangaExtensionManager.installedExtensionsFlow.first().count { it.hasUpdate })
        ExtensionUpdateNotifier(context).notifyAutoUpdated(updated)
    }

    private suspend fun updateAnimeExtensions(context: Context, installer: BasePreferences.ExtensionInstaller) {
        val installed = animeExtensionManager.installedExtensionsFlow.first()
        val sharedSkipped = installed.filter { it.hasUpdate && it.isShared }
        val candidates = installed.filter { it.hasUpdate }
            .filter { canAutoUpdateExtension(true, installer, isSharedInstall = it.isShared) }
        if (candidates.isEmpty()) {
            if (sharedSkipped.isNotEmpty()) {
                ExtensionUpdateNotifier(context).notifySharedAutoUpdateSkipped(
                    sharedSkipped.map {
                        it.name
                    },
                    anime = true,
                )
            }
            return
        }

        val updated = candidates.mapNotNull { extension ->
            val terminalStep = runCatching {
                animeExtensionManager.updateExtension(extension).first { it.isCompleted() }
            }.getOrNull()
            extension.name.takeIf { terminalStep == InstallStep.Installed }
        }
        if (updated.isEmpty()) {
            if (sharedSkipped.isNotEmpty()) {
                ExtensionUpdateNotifier(context).notifySharedAutoUpdateSkipped(
                    sharedSkipped.map {
                        it.name
                    },
                    anime = true,
                )
            }
            return
        }

        sourcePreferences.animeExtensionUpdatesCount()
            .set(animeExtensionManager.installedExtensionsFlow.first().count { it.hasUpdate })
        ExtensionUpdateNotifier(context).notifyAutoUpdated(updated, anime = true)
    }

    internal suspend fun updateNovelExtensions(
        installer: BasePreferences.ExtensionInstaller,
        manager: NovelExtensionManager? = novelExtensionManager.get(),
    ) {
        if (manager == null) return
        val pending = manager.updatesFlow.first()
        val available = manager.availablePluginsFlow.first()
        val userRepos = sourcePreferences.novelInstalledExtensionRepos().get()
        val candidates = pending.filter { plugin ->
            if (plugin.isKotlinExtension) {
                canAutoUpdateExtension(true, installer, isSharedInstall = false)
            } else {
                // JS plugins are plain files executed inside the app process and carry no
                // signature, so auto-update them only when the plugin comes from a repo the user
                // added and the replacement provides a checksum to verify against.
                plugin.repoUrl in userRepos &&
                    available.any {
                        it.id == plugin.id &&
                            it.versionCode > plugin.versionCode &&
                            it.sha256.isNotBlank()
                    }
            }
        }
        if (candidates.isEmpty()) return

        var updatedAny = false
        candidates.forEach { installed ->
            // Never silently switch repos (S3.5/B10): only replace a plugin with a newer variant
            // published to the same repo and carrying a checksum. Installed records can lose their
            // repo attribution (e.g. Kotlin extensions loaded from the package manager), so fall
            // back to the same unambiguous inference the manual classifier uses and skip the
            // plugin entirely when no repo can be attributed conservatively.
            val targetRepoUrl = installed.repoUrl.takeIf { it.isNotBlank() }
                ?: inferInstalledRepoUrl(installed, available)
                ?: return@forEach
            val replacement = available
                .filter {
                    it.id == installed.id &&
                        it.versionCode > installed.versionCode &&
                        it.repoUrl == targetRepoUrl && // never silently switch repos (S3.5/B10)
                        it.sha256.isNotBlank()
                }
                .maxByOrNull { it.versionCode }
                ?: return@forEach
            runCatching { manager.installPlugin(replacement) }
                .onSuccess { updatedAny = true }
                .onFailure { logcat(LogPriority.WARN, it) { "Failed to auto-update novel extension ${installed.id}" } }
        }
        if (!updatedAny) return

        val availableAll = manager.availablePluginsFlow.first()
        val badgeCount = manager.installedPluginsFlow.first().count { installed ->
            val variants = availableAll.filter { it.id == installed.id }
            // Same classification the extensions screen uses (S3.5/B8): full set incl. Kotlin.
            eu.kanade.tachiyomi.ui.browse.novel.extension.NovelPluginUpdateClassifier
                .classify(installed, variants).hasAnyUpdate
        }
        sourcePreferences.novelExtensionUpdatesCount().set(badgeCount)
    }

    /**
     * Mirrors the unambiguous-repo rule of the manual classifier
     * (eu.kanade.tachiyomi.ui.browse.novel.extension.NovelPluginUpdateClassifier) for an installed
     * record that carries no repo of its own. Returns null when this plugin's variants cannot be
     * attributed to a single repo, so auto-update never guesses.
     */
    private fun inferInstalledRepoUrl(
        installed: NovelPlugin.Installed,
        variants: List<NovelPlugin.Available>,
    ): String? {
        val pluginVariants = variants.filter { it.id == installed.id }
        val exactVersionMatches = pluginVariants.filter { it.versionCode == installed.versionCode }

        return exactVersionMatches.singleOrNull()?.repoUrl
            ?: pluginVariants.singleOrNull()?.repoUrl
            ?: pluginVariants.map { it.repoUrl }.distinct().singleOrNull()
    }

    /**
     * The novel manager is optional in some builds/tests, so resolve it lazily and tolerate absence.
     */
    class NovelExtensionManagerProvider {
        fun get(): NovelExtensionManager? = runCatching { Injekt.get<NovelExtensionManager>() }.getOrNull()
    }
}
