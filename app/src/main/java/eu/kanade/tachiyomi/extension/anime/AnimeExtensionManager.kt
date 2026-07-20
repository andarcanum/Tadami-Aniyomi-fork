package eu.kanade.tachiyomi.extension.anime

import android.content.Context
import android.graphics.drawable.Drawable
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.api.AnimeExtensionApi
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.extension.anime.model.newestByVersion
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstaller
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * The manager of anime extensions installed as another apk which extend the available sources. It handles
 * the retrieval of remotely available anime extensions as well as installing, updating and removing them.
 * To avoid malicious distribution, every anime extension must be signed and it will only be loaded if its
 * signature is trusted, otherwise the user will be prompted with a warning to trust it before being
 * loaded.
 *
 * @param context The application context.
 * @param preferences The application preferences.
 */
internal fun String.toInstalledAnimeExtensionPkgName(): String {
    val suffix = substringAfterLast('-', missingDelimiterValue = "")
    return if (suffix.isNotEmpty() && suffix.all(Char::isDigit)) {
        substringBeforeLast('-')
    } else {
        this
    }
}

class AnimeExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
    private val trustExtension: TrustAnimeExtension = Injekt.get(),
) {

    val scope = CoroutineScope(SupervisorJob())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * API where all the available anime extensions can be found.
     */
    private val api by lazy(LazyThreadSafetyMode.NONE) {
        AnimeExtensionApi(animeExtensionManager = this)
    }

    /**
     * The installer which installs, updates and uninstalls the anime extensions.
     */
    private val installer by lazy { AnimeExtensionInstaller(context) }

    private val iconMap = mutableMapOf<String, Drawable?>()
    private val pendingInstallRepos = mutableMapOf<String, InstalledRepo>()
    private var sourceIdToPackageName = emptyMap<Long, String>()

    private val installedExtensionsMapFlow = MutableStateFlow(emptyMap<String, AnimeExtension.Installed>())
    val installedExtensionsFlow = installedExtensionsMapFlow.mapExtensions(scope)

    private val availableExtensionsStateFlow = MutableStateFlow(emptyList<AnimeExtension.Available>())
    val availableExtensionsFlow: StateFlow<List<AnimeExtension.Available>> = availableExtensionsStateFlow.asStateFlow()

    private val availableExtensionsMapFlow = MutableStateFlow(emptyMap<String, AnimeExtension.Available>())

    private val untrustedExtensionsMapFlow = MutableStateFlow(emptyMap<String, AnimeExtension.Untrusted>())
    val untrustedExtensionsFlow = untrustedExtensionsMapFlow.mapExtensions(scope)

    init {
        initAnimeExtensions()
        AnimeExtensionInstallReceiver(AnimeInstallationListener()).register(context)
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages().isSet()

    fun getExtensionPackage(sourceId: Long): String? {
        return sourceIdToPackageName[sourceId]
    }

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?> {
        return installedExtensionsFlow.map { extensions ->
            extensions.find { extension ->
                extension.sources.any { it.id == sourceId }
            }
                ?.pkgName
        }
    }

    fun isNsfwForSource(sourceId: Long): Boolean {
        val pkgName = getExtensionPackage(sourceId) ?: return false
        return installedExtensionsMapFlow.value[pkgName]?.isNsfw ?: false
    }

    fun isNsfwForSourceAsFlow(sourceId: Long): Flow<Boolean> {
        return installedExtensionsFlow.map { extensions ->
            extensions.find { extension ->
                extension.sources.any { it.id == sourceId }
            }
                ?.isNsfw
                ?: false
        }
    }

    fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = sourceIdToPackageName[sourceId] ?: return null

        return iconMap[pkgName] ?: iconMap.getOrPut(pkgName) {
            AnimeExtensionLoader.getAnimeExtensionPackageInfoFromPkgName(context, pkgName)
                ?.applicationInfo
                ?.loadIcon(context.packageManager)
        }
    }

    private var availableAnimeExtensionsSourcesData: Map<Long, StubAnimeSource> = emptyMap()

    private fun setupAvailableAnimeExtensionsSourcesDataMap(
        animeextensions: List<AnimeExtension.Available>,
    ) {
        if (animeextensions.isEmpty()) return
        availableAnimeExtensionsSourcesData = animeextensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableAnimeExtensionsSourcesData[id]

    /**
     * Loads and registers the installed animeextensions.
     */
    private fun initAnimeExtensions() {
        val animeextensions = AnimeExtensionLoader.loadExtensions(context)

        installedExtensionsMapFlow.value = animeextensions
            .filterIsInstance<AnimeLoadResult.Success>()
            .associate { result ->
                val extension = result.extension.withSavedRepo()
                extension.pkgName to extension
            }
        cacheInstalledExtensionIcons()
        rebuildSourcePackageIndex()

        untrustedExtensionsMapFlow.value = animeextensions
            .filterIsInstance<AnimeLoadResult.Untrusted>()
            .associate { it.extension.pkgName to it.extension }

        _isInitialized.value = true
    }

    /**
     * Finds the available anime extensions in the [api] and updates [availableExtensionsMapFlow].
     */
    suspend fun findAvailableExtensions() {
        val fetched = try {
            api.findExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withUIContext { context.toast(MR.strings.extension_api_error) }
            // Keep the last known state instead of wiping the available list.
            return
        }

        // If some stores failed to respond, carry over their last known extensions so a
        // transient failure doesn't drop their icons or mark installed extensions as obsolete.
        val extensions = if (fetched.isComplete) {
            fetched.extensions
        } else {
            val fetchedKeys = fetched.extensions.map { it.pkgName to it.repoUrl }.toHashSet()
            fetched.extensions + availableExtensionsStateFlow.value.filter {
                it.repoUrl in fetched.failedRepoUrls && (it.pkgName to it.repoUrl) !in fetchedKeys
            }
        }

        enableAdditionalSubLanguages(extensions)

        availableExtensionsStateFlow.value = extensions
        availableExtensionsMapFlow.value = extensions
            .groupBy { it.pkgName }
            .mapValues { (_, variants) -> variants.newestByVersion()!! }
        updatedInstalledAnimeExtensionsStatuses(extensions, canMarkObsolete = fetched.isComplete)
        setupAvailableAnimeExtensionsSourcesDataMap(extensions)
    }

    /**
     * Enables the additional sub-languages in the app first run. This addresses
     * the issue where users still need to enable some specific languages even when
     * the device language is inside that major group. As an example, if a user
     * has a zh device language, the app will also enable zh-Hans and zh-Hant.
     *
     * If the user have already changed the enabledLanguages preference value once,
     * the new languages will not be added to respect the user enabled choices.
     */
    private fun enableAdditionalSubLanguages(animeextensions: List<AnimeExtension.Available>) {
        if (subLanguagesEnabledOnFirstRun || animeextensions.isEmpty()) {
            return
        }

        // Use the source lang as some aren't present on the animeextension level.
        val availableLanguages = animeextensions
            .flatMap(AnimeExtension.Available::sources)
            .distinctBy(AnimeExtension.Available.AnimeSource::lang)
            .map(AnimeExtension.Available.AnimeSource::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages().defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages().set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    /**
     * Sets the update field of the installed animeextensions with the given [availableExtensions].
     *
     * @param availableExtensions The list of animeextensions given by the [api].
     */
    private fun updatedInstalledAnimeExtensionsStatuses(
        availableExtensions: List<AnimeExtension.Available>,
        canMarkObsolete: Boolean = true,
    ) {
        if (availableExtensions.isEmpty()) {
            preferences.animeExtensionUpdatesCount().set(0)
            return
        }

        val availableExtensionsByPkgName = availableExtensions.groupBy { it.pkgName }
        val installedExtensionsMap = installedExtensionsMapFlow.value.toMutableMap()
        var changed = false

        for ((pkgName, extension) in installedExtensionsMap) {
            val variants = availableExtensionsByPkgName[pkgName].orEmpty()
            val availableExt = variants.newestByVersion()

            if (availableExt == null && canMarkObsolete && !extension.isObsolete) {
                installedExtensionsMap[pkgName] = extension.copy(isObsolete = true)
                changed = true
            } else if (availableExt != null) {
                val extensionWithRepo = extension.withInferredRepo(variants)
                val regularUpdate = selectRegularUpdate(extensionWithRepo, variants)
                val reinstallCandidates = selectReinstallCandidates(extensionWithRepo, variants)

                val updatedExtension = extensionWithRepo.copy(
                    hasUpdate = regularUpdate != null || reinstallCandidates.isNotEmpty(),
                    needsReinstall = regularUpdate == null && reinstallCandidates.isNotEmpty(),
                    // The extension is available again, so it is no longer obsolete.
                    isObsolete = false,
                )
                if (updatedExtension != extension) {
                    installedExtensionsMap[pkgName] = updatedExtension
                    changed = true
                }
                saveInstalledRepo(extensionWithRepo)
            }
        }
        if (changed) {
            installedExtensionsMapFlow.value = installedExtensionsMap
        }
        updatePendingUpdatesCount()
    }

    /**
     * Returns a flow of the installation process for the given anime extension. It will complete
     * once the anime extension is installed or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The anime extension to be installed.
     */
    fun installExtension(extension: AnimeExtension.Available): Flow<InstallStep> {
        val installableExtension = extension.copy(pkgName = extension.pkgName.toInstalledAnimeExtensionPkgName())
        pendingInstallRepos[installableExtension.pkgName] = InstalledRepo(extension.repoUrl, extension.repoName)
        return installer.downloadAndInstall(api.getApkUrl(extension), installableExtension)
    }

    /**
     * Returns a flow of the installation process for the given anime extension. It will complete
     * once the anime extension is updated or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The anime extension to be updated.
     */
    fun updateExtension(extension: AnimeExtension.Installed): Flow<InstallStep> {
        val variants = availableExtensionsStateFlow.value.filter { it.pkgName == extension.pkgName }
        val availableExt = selectRegularUpdate(extension, variants) ?: return emptyFlow()
        return installExtension(availableExt)
    }

    fun replaceExtensionFromRepo(
        installedExtension: AnimeExtension.Installed,
        replacementExtension: AnimeExtension.Available,
    ): Flow<InstallStep> = flow {
        emit(InstallStep.Installing)
        installer.uninstallApk(installedExtension.pkgName)

        repeat(REPLACE_UNINSTALL_WAIT_SECONDS) {
            if (!context.isPackageInstalled(installedExtension.pkgName)) {
                installExtension(replacementExtension).collect { emit(it) }
                return@flow
            }
            delay(1.seconds)
        }

        emit(InstallStep.Error)
    }

    fun cancelInstallUpdateExtension(extension: AnimeExtension) {
        installer.cancelInstall(extension.pkgName.toInstalledAnimeExtensionPkgName())
    }

    /**
     * Sets to "installing" status of an anime extension installation.
     *
     * @param downloadId The id of the download.
     */
    fun setInstalling(downloadId: Long) {
        installer.updateInstallStep(downloadId, InstallStep.Installing)
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        installer.updateInstallStep(downloadId, step)
    }

    /**
     * Uninstalls the anime extension that matches the given package name.
     *
     * @param extension The extension to uninstall.
     */
    fun uninstallExtension(extension: AnimeExtension) {
        installer.uninstallApk(extension.pkgName)
    }

    /**
     * Adds the given extension to the list of trusted extensions. It also loads in background the
     * now trusted extensions.
     *
     * @param extension the extension to trust
     */
    suspend fun trust(extension: AnimeExtension.Untrusted) {
        untrustedExtensionsMapFlow.value[extension.pkgName] ?: return

        trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)

        untrustedExtensionsMapFlow.value -= extension.pkgName

        AnimeExtensionLoader.loadExtensionFromPkgName(context, extension.pkgName)
            .let { it as? AnimeLoadResult.Success }
            ?.let { registerNewExtension(it.extension) }
    }

    /**
     * Registers the given anime extension in this and the source managers.
     *
     * @param extension The anime extension to be registered.
     */
    private fun registerNewExtension(extension: AnimeExtension.Installed) {
        val extensionWithRepo = extension.withPendingOrSavedRepo().withUpdateCheck()
        installedExtensionsMapFlow.value += extensionWithRepo
        saveInstalledRepo(extensionWithRepo)
        iconMap[extension.pkgName] = extension.icon
        rebuildSourcePackageIndex()

        // ponytail: Auto-enable languages of the newly installed extension
        val langs = if (extension.lang != "all" && extension.lang.isNotBlank()) {
            setOf(extension.lang)
        } else {
            val deviceLang = java.util.Locale.getDefault().language
            extension.sources.map { it.lang }.filter { it == deviceLang }.toSet()
        }
        if (langs.isNotEmpty()) {
            val currentLangs = preferences.enabledLanguages().get()
            if (!currentLangs.containsAll(langs)) {
                preferences.enabledLanguages().set(currentLangs + langs)
            }
        }
    }

    /**
     * Registers the given updated anime extension in this and the source managers previously removing
     * the outdated ones.
     *
     * @param extension The anime extension to be registered.
     */
    private fun registerUpdatedExtension(extension: AnimeExtension.Installed) {
        val extensionWithRepo = extension.withPendingOrSavedRepo().withUpdateCheck()
        installedExtensionsMapFlow.value += extensionWithRepo
        saveInstalledRepo(extensionWithRepo)
        iconMap[extension.pkgName] = extension.icon
        rebuildSourcePackageIndex()
    }

    /**
     * Loads an extension by package name and registers it directly, bypassing the broadcast
     * receiver. Used as a safety net to handle cases where [AnimeExtensionInstallReceiver] may
     * not receive the system broadcast (MIUI, Android 11 quirks, etc.).
     *
     * @param pkgName The package name of the extension to reload and register.
     */
    fun reloadAndRegisterExtension(pkgName: String) {
        scope.launch {
            when (val result = AnimeExtensionLoader.loadExtensionFromPkgName(context, pkgName)) {
                is AnimeLoadResult.Success -> {
                    registerUpdatedExtension(result.extension)
                }
                is AnimeLoadResult.Untrusted -> {
                    installedExtensionsMapFlow.value -= result.extension.pkgName
                    untrustedExtensionsMapFlow.value += result.extension
                    rebuildSourcePackageIndex()
                }
                else -> return@launch
            }
            updatePendingUpdatesCount()
        }
    }

    /**
     * Unregisters the animeextension in this and the source managers given its package name. Note this
     * method is called for every uninstalled application in the system.
     *
     * @param pkgName The package name of the uninstalled application.
     */
    private fun unregisterAnimeExtension(pkgName: String) {
        removeSavedInstalledRepo(pkgName)
        pendingInstallRepos.remove(pkgName)
        installedExtensionsMapFlow.value -= pkgName
        untrustedExtensionsMapFlow.value -= pkgName
        iconMap -= pkgName
        rebuildSourcePackageIndex()
    }

    private fun cacheInstalledExtensionIcons() {
        installedExtensionsMapFlow.value.values.forEach { extension ->
            iconMap[extension.pkgName] = extension.icon
        }
    }

    private fun rebuildSourcePackageIndex() {
        sourceIdToPackageName = installedExtensionsMapFlow.value.values
            .flatMap { extension -> extension.sources.map { source -> source.id to extension.pkgName } }
            .toMap()
    }

    /**
     * Listener which receives events of the anime extensions being installed, updated or removed.
     */
    private inner class AnimeInstallationListener : AnimeExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: AnimeExtension.Installed) {
            registerNewExtension(extension)
            updatePendingUpdatesCount()
        }

        override fun onExtensionUpdated(extension: AnimeExtension.Installed) {
            registerUpdatedExtension(extension)
            updatePendingUpdatesCount()
        }

        override fun onExtensionUntrusted(extension: AnimeExtension.Untrusted) {
            installedExtensionsMapFlow.value -= extension.pkgName
            untrustedExtensionsMapFlow.value += extension
            rebuildSourcePackageIndex()
            updatePendingUpdatesCount()
        }

        override fun onPackageUninstalled(pkgName: String) {
            AnimeExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterAnimeExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    /**
     * AnimeExtension method to set the update field of an installed anime extension.
     */
    private fun AnimeExtension.Installed.withUpdateCheck(): AnimeExtension.Installed {
        val variants = availableExtensionsStateFlow.value.filter { it.pkgName == pkgName }
        if (variants.isEmpty()) {
            return if (updateExists()) copy(hasUpdate = true) else this
        }

        val regularUpdate = selectRegularUpdate(this, variants)
        val reinstallCandidates = selectReinstallCandidates(this, variants)
        return copy(
            hasUpdate = regularUpdate != null || reinstallCandidates.isNotEmpty(),
            needsReinstall = regularUpdate == null && reinstallCandidates.isNotEmpty(),
        )
    }

    private fun AnimeExtension.Installed.updateExists(
        availableExtension: AnimeExtension.Available? = null,
    ): Boolean {
        val availableExt = availableExtension
            ?: availableExtensionsMapFlow.value[pkgName]
            ?: return false

        return (availableExt.versionCode > versionCode || availableExt.libVersion > libVersion)
    }

    private fun selectRegularUpdate(
        extension: AnimeExtension.Installed,
        variants: List<AnimeExtension.Available>,
    ): AnimeExtension.Available? {
        extension.repoUrl
            ?.let { repoUrl ->
                variants
                    .filter { it.repoUrl == repoUrl && extension.updateExists(it) }
                    .latestVersionGroup()
                    .firstOrNull()
            }
            ?.let { return it }

        if (extension.repoUrl != null) return null

        val latestVersionGroup = variants
            .filter { extension.updateExists(it) }
            .latestVersionGroup()

        if (variants.size == 1) return latestVersionGroup.singleOrNull()
        return latestVersionGroup.takeIf { it.size > 1 }?.firstOrNull()
    }

    private fun selectReinstallCandidates(
        extension: AnimeExtension.Installed,
        variants: List<AnimeExtension.Available>,
    ): List<AnimeExtension.Available> {
        if (selectRegularUpdate(extension, variants) != null) return emptyList()

        return variants
            .filter { extension.repoUrl == null || it.repoUrl != extension.repoUrl }
            .filter { extension.updateExists(it) }
            .latestVersionGroup()
    }

    private fun List<AnimeExtension.Available>.latestVersionGroup(): List<AnimeExtension.Available> {
        val latest = maxWithOrNull(
            compareBy<AnimeExtension.Available> { it.versionCode }
                .thenBy { it.libVersion },
        ) ?: return emptyList()

        return filter { it.versionCode == latest.versionCode && it.libVersion == latest.libVersion }
            .sortedWith(
                compareBy<AnimeExtension.Available> { it.repoName.ifBlank { it.repoUrl } }
                    .thenBy { it.repoUrl },
            )
    }

    private fun AnimeExtension.Installed.withInferredRepo(
        variants: List<AnimeExtension.Available>,
    ): AnimeExtension.Installed {
        if (repoUrl != null || variants.isEmpty()) return this

        val exactVersionMatches = variants.filter {
            it.versionCode == versionCode && it.libVersion == libVersion
        }
        val repoCandidate = exactVersionMatches.singleOrNull()
            ?: variants.singleOrNull()
            ?: variants
                .map { InstalledRepo(url = it.repoUrl, name = it.repoName) }
                .distinctBy { it.url }
                .singleOrNull()
                ?.let { repo -> variants.first { it.repoUrl == repo.url } }
            ?: return this

        return copy(
            repoUrl = repoCandidate.repoUrl,
            repoName = repoCandidate.repoName.takeIf { it.isNotBlank() },
        )
    }

    private fun AnimeExtension.Installed.withPendingOrSavedRepo(): AnimeExtension.Installed {
        val pendingRepo = pendingInstallRepos.remove(pkgName)
        return when {
            pendingRepo != null -> copy(
                repoUrl = pendingRepo.url,
                repoName = pendingRepo.name.takeIf {
                    it.isNotBlank()
                },
            )
            repoUrl != null -> this
            else -> withSavedRepo()
        }
    }

    private fun AnimeExtension.Installed.withSavedRepo(): AnimeExtension.Installed {
        if (repoUrl != null) return this
        val savedRepo = getSavedInstalledRepo(pkgName) ?: return this
        return copy(repoUrl = savedRepo.url, repoName = savedRepo.name.takeIf { it.isNotBlank() })
    }

    private fun getSavedInstalledRepo(pkgName: String): InstalledRepo? {
        return preferences.animeInstalledExtensionRepos().get()
            .firstOrNull { it.substringBefore('|') == pkgName }
            ?.let { entry ->
                val parts = entry.split('|', limit = 3)
                val url = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@let null
                InstalledRepo(url = url, name = parts.getOrNull(2).orEmpty())
            }
    }

    private fun saveInstalledRepo(extension: AnimeExtension.Installed) {
        val repoUrl = extension.repoUrl ?: return
        val repoName = extension.repoName.orEmpty()
        preferences.animeInstalledExtensionRepos().getAndSet { entries ->
            entries.filterNot { it.substringBefore('|') == extension.pkgName }.toSet() +
                "${extension.pkgName}|$repoUrl|$repoName"
        }
    }

    private fun removeSavedInstalledRepo(pkgName: String) {
        preferences.animeInstalledExtensionRepos().getAndSet { entries ->
            entries.filterNot { it.substringBefore('|') == pkgName }.toSet()
        }
    }

    private data class InstalledRepo(
        val url: String,
        val name: String,
    )

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = installedExtensionsMapFlow.value.values.count { it.hasUpdate }
        preferences.animeExtensionUpdatesCount().set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            ExtensionUpdateNotifier(context).dismiss()
        }
    }

    private operator fun <T : AnimeExtension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    private companion object {
        const val REPLACE_UNINSTALL_WAIT_SECONDS = 120
    }

    private fun <T : AnimeExtension> StateFlow<Map<String, T>>.mapExtensions(
        scope: CoroutineScope,
    ): StateFlow<List<T>> {
        return map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, value.values.toList())
    }
}
