package eu.kanade.tachiyomi.extension.manga

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.ExtensionSignatureComparison
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionApi
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.model.inferMangaInstalledRepo
import eu.kanade.tachiyomi.extension.manga.model.newestByVersion
import eu.kanade.tachiyomi.extension.manga.model.selectMangaRegularUpdate
import eu.kanade.tachiyomi.extension.manga.model.selectMangaReinstallCandidates
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstaller
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

internal fun String.toInstalledMangaExtensionPkgName(): String {
    val suffix = substringAfterLast('-', missingDelimiterValue = "")
    return if (suffix.isNotEmpty() && suffix.all(Char::isDigit)) {
        substringBeforeLast('-')
    } else {
        this
    }
}

/**
 * The manager of extensions installed as another apk which extend the available sources. It handles
 * the retrieval of remotely available extensions as well as installing, updating and removing them.
 * To avoid malicious distribution, every extension must be signed and it will only be loaded if its
 * signature is trusted, otherwise the user will be prompted with a warning to trust it before being
 * loaded.
 */
class MangaExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
    private val trustExtension: TrustMangaExtension = Injekt.get(),
) {

    val scope = CoroutineScope(SupervisorJob())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * API where all the available extensions can be found.
     */
    private val api by lazy(LazyThreadSafetyMode.NONE) {
        MangaExtensionApi(extensionManager = this)
    }

    /**
     * The installer which installs, updates and uninstalls the extensions.
     */
    private val installer by lazy { MangaExtensionInstaller(context) }

    // F-M8: written from the IO status refresh, the manager's Default scope and the main-thread
    // broadcast receiver - the plain maps/var lost updates across threads (a concurrent install
    // or uninstall vanished from the UI until restart).
    private val iconMap: MutableMap<String, Drawable?> =
        java.util.Collections.synchronizedMap(mutableMapOf())
    private val pendingInstallRepos: MutableMap<String, InstalledRepo> =
        java.util.Collections.synchronizedMap(mutableMapOf())

    @Volatile
    private var sourceIdToPackageName = emptyMap<Long, String>()

    private val installedExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Installed>())
    val installedExtensionsFlow = installedExtensionsMapFlow.mapExtensions(scope)

    private val availableExtensionsStateFlow = MutableStateFlow(emptyList<MangaExtension.Available>())
    val availableExtensionsFlow: StateFlow<List<MangaExtension.Available>> = availableExtensionsStateFlow.asStateFlow()

    private val availableExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Available>())

    private val untrustedExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Untrusted>())
    val untrustedExtensionsFlow = untrustedExtensionsMapFlow.mapExtensions(scope)

    /**
     * Install attempts that failed because the APK is signed with a different key than the
     * installed copy. The UI subscribes to offer reinstall-with-uninstall.
     */
    data class SignatureMismatchEvent(
        val packageName: String,
        val displayName: String,
        /** Recommended replacement variant (newest from the preferred repo), when known. */
        val candidate: MangaExtension.Available? = null,
    )

    private val _signatureMismatchEvents = MutableSharedFlow<SignatureMismatchEvent>(extraBufferCapacity = 8)
    val signatureMismatchEvents: SharedFlow<SignatureMismatchEvent> = _signatureMismatchEvents.asSharedFlow()

    /** Reports a signature mismatch detected during an install attempt. */
    fun reportSignatureMismatch(pkgName: String) {
        val displayName = installedExtensionsMapFlow.value[pkgName]?.name ?: pkgName
        _signatureMismatchEvents.tryEmit(
            SignatureMismatchEvent(
                packageName = pkgName,
                displayName = displayName,
                candidate = availableExtensionsMapFlow.value[pkgName],
            ),
        )
    }

    /**
     * Carries the user's trust to the newly installed (shared) version when the signing key is
     * unchanged, so an update does not silently flip the extension back to Untrusted.
     */
    private fun carryTrustToNewVersion(pkgName: String) {
        val info = runCatching {
            context.packageManager.getPackageInfo(pkgName, 0)
        }.getOrNull() ?: return
        val signatures = ExtensionSignatureComparison.installedSignatures(context, pkgName) ?: return
        signatures.lastOrNull()?.let { signatureHash ->
            trustExtension.trustIfSameSigner(
                pkgName,
                PackageInfoCompat.getLongVersionCode(info),
                signatureHash,
            )
        }
    }

    init {
        initExtensions()
        MangaExtensionInstallReceiver(InstallationListener()).register(context)
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages().isSet()

    fun getExtensionPackage(sourceId: Long): String? {
        return sourceIdToPackageName[sourceId]
    }

    /**
     * Returns the extensions-lib version of the installed extension serving [sourceId],
     * or null when the source is not backed by an installed extension.
     */
    fun getLibVersionForSource(sourceId: Long): Double? {
        val pkgName = getExtensionPackage(sourceId) ?: return null
        return installedExtensionsMapFlow.value[pkgName]?.libVersion
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
            MangaExtensionLoader.getMangaExtensionPackageInfoFromPkgName(context, pkgName)
                ?.applicationInfo
                ?.loadIcon(context.packageManager)
        }
    }

    private var availableExtensionsSourcesData: Map<Long, StubMangaSource> = emptyMap()

    private fun setupAvailableExtensionsSourcesDataMap(extensions: List<MangaExtension.Available>) {
        if (extensions.isEmpty()) return
        availableExtensionsSourcesData = extensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableExtensionsSourcesData[id]

    /**
     * Loads and registers the installed extensions.
     */
    private fun initExtensions() {
        val extensions = MangaExtensionLoader.loadMangaExtensions(context)

        installedExtensionsMapFlow.value = extensions
            .filterIsInstance<MangaLoadResult.Success>()
            .associate { result ->
                val extension = result.extension.withSavedRepo()
                extension.pkgName to extension
            }
        cacheInstalledExtensionIcons()
        rebuildSourcePackageIndex()

        untrustedExtensionsMapFlow.value = extensions
            .filterIsInstance<MangaLoadResult.Untrusted>()
            .associate { it.extension.pkgName to it.extension }

        _isInitialized.value = true
    }

    /**
     * Finds the available extensions in the [api] and updates [availableExtensionsMapFlow].
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
            .groupBy { it.pkgName.toInstalledMangaExtensionPkgName() }
            .mapValues { (_, variants) -> variants.newestByVersion()!! }
        updatedInstalledExtensionsStatuses(extensions, canMarkObsolete = fetched.isComplete)
        setupAvailableExtensionsSourcesDataMap(extensions)
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
    private fun enableAdditionalSubLanguages(extensions: List<MangaExtension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) {
            return
        }

        // Use the source lang as some aren't present on the extension level.
        val availableLanguages = extensions
            .flatMap(MangaExtension.Available::sources)
            .distinctBy(MangaExtension.Available.MangaSource::lang)
            .map(MangaExtension.Available.MangaSource::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages().defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages().set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    /**
     * Sets the update field of the installed extensions with the given [availableExtensions].
     *
     * @param availableExtensions The list of extensions given by the [api].
     */
    private fun updatedInstalledExtensionsStatuses(
        availableExtensions: List<MangaExtension.Available>,
        canMarkObsolete: Boolean = true,
    ) {
        if (availableExtensions.isEmpty()) {
            preferences.mangaExtensionUpdatesCount().set(0)
            return
        }

        val availableExtensionsByPkgName = availableExtensions
            .groupBy { it.pkgName.toInstalledMangaExtensionPkgName() }
        // F-M8: atomic read-modify-write (MutableStateFlow.update CAS). The previous
        // copy-mutate-assign from IO raced with the broadcast receiver's map writes on main:
        // a concurrent install/uninstall was overwritten and vanished from the UI until restart.
        // saveInstalledRepo inside the lambda is an idempotent pref write, so a CAS retry is safe.
        installedExtensionsMapFlow.update { currentMap ->
            val installedExtensionsMap = currentMap.toMutableMap()
            var changed = false

            for ((pkgName, extension) in installedExtensionsMap) {
                val variants = availableExtensionsByPkgName[pkgName].orEmpty()
                val availableExt = variants.newestByVersion()

                if (availableExt == null && canMarkObsolete && !extension.isObsolete) {
                    installedExtensionsMap[pkgName] = extension.copy(isObsolete = true)
                    changed = true
                } else if (availableExt != null) {
                    val extensionWithRepo = extension.withInferredRepo(variants)
                    val regularUpdate = selectMangaRegularUpdate(extensionWithRepo, variants)
                    val reinstallCandidates = selectMangaReinstallCandidates(extensionWithRepo, variants)

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
            if (changed) installedExtensionsMap else currentMap
        }
        updatePendingUpdatesCount()
    }

    /**
     * Returns a flow of the installation process for the given extension. It will complete
     * once the extension is installed or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The extension to be installed.
     */
    fun installExtension(
        extension: MangaExtension.Available,
        isUpdateForPrivatelyInstalled: Boolean = false,
    ): Flow<InstallStep> {
        val installableExtension = extension.copy(pkgName = extension.pkgName.toInstalledMangaExtensionPkgName())
        pendingInstallRepos[installableExtension.pkgName] = InstalledRepo(extension.repoUrl, extension.repoName)
        return installer.downloadAndInstall(
            url = api.getApkUrl(extension),
            extension = installableExtension,
            isUpdateForPrivatelyInstalled = isUpdateForPrivatelyInstalled,
        )
    }

    /**
     * Returns a flow of the installation process for the given extension. It will complete
     * once the extension is updated or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The extension to be updated.
     */
    fun updateExtension(extension: MangaExtension.Installed): Flow<InstallStep> = flow {
        // The update banner only relies on the stored hasUpdate flag, while performing the update
        // needs a matching entry in the in-memory available list. When that list is stale or was
        // never populated in this process, the action silently did nothing, so refresh the repos
        // once before giving up.
        val availableExt = selectAvailableUpdate(extension)
            ?: run {
                findAvailableExtensions()
                selectAvailableUpdate(extension)
            }

        if (availableExt == null) {
            emit(InstallStep.Error)
            return@flow
        }

        // A privately installed extension has no system package: installing its update through
        // PackageInstaller/Shizuku would add a second, system copy next to the private one.
        installExtension(availableExt, isUpdateForPrivatelyInstalled = !extension.isShared)
            .collect { emit(it) }
    }

    private fun selectAvailableUpdate(extension: MangaExtension.Installed): MangaExtension.Available? {
        val variants = availableVariantsFor(extension.pkgName)
        return selectMangaRegularUpdate(extension.withInferredRepo(variants), variants)
    }

    /**
     * All available builds of an installed package. Repos may publish package names carrying a
     * numeric suffix that is stripped when installing, so both forms have to be matched.
     */
    private fun availableVariantsFor(pkgName: String): List<MangaExtension.Available> {
        return availableExtensionsStateFlow.value.filter {
            it.pkgName.toInstalledMangaExtensionPkgName() == pkgName
        }
    }

    fun replaceExtensionFromRepo(
        installedExtension: MangaExtension.Installed,
        replacementExtension: MangaExtension.Available,
    ): Flow<InstallStep> {
        // X3: the replacement used to run inside a plain flow collected by the extensions screen
        // model scope - leaving the screen between the uninstall and the install cancelled the
        // flow and left the extension REMOVED with no replacement. It now runs on the manager's
        // own scope and the returned flow merely mirrors progress (replay=1 so a re-attaching
        // collector sees the latest step).
        val progress = MutableSharedFlow<InstallStep>(replay = 1, extraBufferCapacity = 16)
        scope.launch {
            progress.emit(InstallStep.Installing)
            installer.uninstallApk(installedExtension.pkgName)

            repeat(REPLACE_UNINSTALL_WAIT_SECONDS) {
                if (!context.isPackageInstalled(installedExtension.pkgName)) {
                    installExtension(replacementExtension).collect { progress.emit(it) }
                    return@launch
                }
                delay(1.seconds)
            }

            progress.emit(InstallStep.Error)
        }
        return progress
    }

    /** Reconnects downloads orphaned by a process death and installs the finished ones. */
    fun resumeOrphanedDownloads() {
        installer.resumeOrphanedDownloads(context)
    }

    fun cancelInstallUpdateExtension(extension: MangaExtension) {
        installer.cancelInstall(extension.pkgName.toInstalledMangaExtensionPkgName())
    }

    /**
     * Sets to "installing" status of an extension installation.
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
     * Uninstalls the extension that matches the given package name.
     *
     * @param extension The extension to uninstall.
     */
    fun uninstallExtension(extension: MangaExtension) {
        installer.uninstallApk(extension.pkgName)
    }

    /**
     * Adds the given extension to the list of trusted extensions. It also loads in background the
     * now trusted extensions.
     *
     * @param extension the extension to trust
     */
    suspend fun trust(extension: MangaExtension.Untrusted) {
        untrustedExtensionsMapFlow.value[extension.pkgName] ?: return

        trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)

        untrustedExtensionsMapFlow.value -= extension.pkgName

        MangaExtensionLoader.loadMangaExtensionFromPkgName(context, extension.pkgName)
            .let { it as? MangaLoadResult.Success }
            ?.let { registerNewExtension(it.extension) }
    }

    /**
     * Registers the given extension in this and the source managers.
     *
     * @param extension The extension to be registered.
     */
    private fun registerNewExtension(extension: MangaExtension.Installed) {
        val extensionWithRepo = extension.withPendingOrSavedRepo().withUpdateCheck()
        installedExtensionsMapFlow.update { it + extensionWithRepo }
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
     * Registers the given updated extension in this and the source managers previously removing
     * the outdated ones.
     *
     * @param extension The extension to be registered.
     */
    private fun registerUpdatedExtension(extension: MangaExtension.Installed) {
        val extensionWithRepo = extension.withPendingOrSavedRepo().withUpdateCheck()
        installedExtensionsMapFlow.update { it + extensionWithRepo }
        saveInstalledRepo(extensionWithRepo)
        iconMap[extension.pkgName] = extension.icon
        rebuildSourcePackageIndex()
    }

    /**
     * Loads an extension by package name and registers it directly, bypassing the broadcast
     * receiver. Used as a safety net to handle cases where [MangaExtensionInstallReceiver] may
     * not receive the system broadcast (MIUI, Android 11 quirks, etc.).
     *
     * @param pkgName The package name of the extension to reload and register.
     */
    fun reloadAndRegisterExtension(pkgName: String) {
        scope.launch {
            when (val result = MangaExtensionLoader.loadMangaExtensionFromPkgName(context, pkgName)) {
                is MangaLoadResult.Success -> {
                    registerUpdatedExtension(result.extension)
                }
                is MangaLoadResult.Untrusted -> {
                    installedExtensionsMapFlow.update { it - result.extension.pkgName }
                    untrustedExtensionsMapFlow.update { it + result.extension }
                    rebuildSourcePackageIndex()
                }
                else -> return@launch
            }
            updatePendingUpdatesCount()
        }
    }

    /**
     * Unregisters the extension in this and the source managers given its package name. Note this
     * method is called for every uninstalled application in the system.
     *
     * @param pkgName The package name of the uninstalled application.
     */
    private fun unregisterExtension(pkgName: String) {
        removeSavedInstalledRepo(pkgName)
        pendingInstallRepos.remove(pkgName)
        installedExtensionsMapFlow.update { it - pkgName }
        untrustedExtensionsMapFlow.update { it - pkgName }
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
     * Listener which receives events of the extensions being installed, updated or removed.
     */
    private inner class InstallationListener : MangaExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: MangaExtension.Installed) {
            registerNewExtension(extension)
            updatePendingUpdatesCount()
        }

        override fun onExtensionUpdated(extension: MangaExtension.Installed) {
            registerUpdatedExtension(extension)
            updatePendingUpdatesCount()
            // A system install finished — keep the user's trust when the signing key is unchanged.
            carryTrustToNewVersion(extension.pkgName)
        }

        override fun onExtensionUntrusted(extension: MangaExtension.Untrusted) {
            installedExtensionsMapFlow.update { it - extension.pkgName }
            untrustedExtensionsMapFlow.update { it + extension }
            rebuildSourcePackageIndex()
            updatePendingUpdatesCount()
        }

        override fun onPackageUninstalled(pkgName: String) {
            MangaExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    /**
     * Extension method to set the update field of an installed extension.
     */
    private fun MangaExtension.Installed.withUpdateCheck(): MangaExtension.Installed {
        val variants = availableVariantsFor(pkgName)
        if (variants.isEmpty()) {
            return if (updateExists()) copy(hasUpdate = true) else this
        }

        val extensionWithRepo = withInferredRepo(variants)
        val regularUpdate = selectMangaRegularUpdate(extensionWithRepo, variants)
        val reinstallCandidates = selectMangaReinstallCandidates(extensionWithRepo, variants)
        return extensionWithRepo.copy(
            hasUpdate = regularUpdate != null || reinstallCandidates.isNotEmpty(),
            needsReinstall = regularUpdate == null && reinstallCandidates.isNotEmpty(),
        )
    }

    private fun MangaExtension.Installed.updateExists(
        availableExtension: MangaExtension.Available? = null,
    ): Boolean {
        val availableExt = availableExtension
            ?: availableExtensionsMapFlow.value[pkgName]
            ?: return false

        return (availableExt.versionCode > versionCode || availableExt.libVersion > libVersion)
    }

    private fun MangaExtension.Installed.withInferredRepo(
        variants: List<MangaExtension.Available>,
    ): MangaExtension.Installed {
        if (repoUrl != null || variants.isEmpty()) return this

        val repoCandidate = inferMangaInstalledRepo(this, variants) ?: return this

        return copy(
            repoUrl = repoCandidate.repoUrl,
            repoName = repoCandidate.repoName.takeIf { it.isNotBlank() },
        )
    }

    private fun MangaExtension.Installed.withPendingOrSavedRepo(): MangaExtension.Installed {
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

    private fun MangaExtension.Installed.withSavedRepo(): MangaExtension.Installed {
        if (repoUrl != null) return this
        val savedRepo = getSavedInstalledRepo(pkgName) ?: return this
        return copy(repoUrl = savedRepo.url, repoName = savedRepo.name.takeIf { it.isNotBlank() })
    }

    private fun getSavedInstalledRepo(pkgName: String): InstalledRepo? {
        return preferences.mangaInstalledExtensionRepos().get()
            .firstOrNull { it.substringBefore('|') == pkgName }
            ?.let { entry ->
                val parts = entry.split('|', limit = 3)
                val url = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@let null
                InstalledRepo(url = url, name = parts.getOrNull(2).orEmpty())
            }
    }

    private fun saveInstalledRepo(extension: MangaExtension.Installed) {
        val repoUrl = extension.repoUrl ?: return
        val repoName = extension.repoName.orEmpty()
        preferences.mangaInstalledExtensionRepos().getAndSet { entries ->
            entries.filterNot { it.substringBefore('|') == extension.pkgName }.toSet() +
                "${extension.pkgName}|$repoUrl|$repoName"
        }
    }

    private fun removeSavedInstalledRepo(pkgName: String) {
        preferences.mangaInstalledExtensionRepos().getAndSet { entries ->
            entries.filterNot { it.substringBefore('|') == pkgName }.toSet()
        }
    }

    private data class InstalledRepo(
        val url: String,
        val name: String,
    )

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = installedExtensionsMapFlow.value.values.count { it.hasUpdate }
        preferences.mangaExtensionUpdatesCount().set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            ExtensionUpdateNotifier(context).dismiss()
        }
    }

    private operator fun <T : MangaExtension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    private companion object {
        const val REPLACE_UNINSTALL_WAIT_SECONDS = 120
    }

    private fun <T : MangaExtension> StateFlow<Map<String, T>>.mapExtensions(
        scope: CoroutineScope,
    ): StateFlow<List<T>> {
        return map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, value.values.toList())
    }
}
