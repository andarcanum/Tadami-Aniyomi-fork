package eu.kanade.tachiyomi.ui.browse.novel.extension

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.ExtensionApkFileStore
import eu.kanade.tachiyomi.extension.installer.ExtensionInstallDiagnostic
import eu.kanade.tachiyomi.extension.installer.UnifiedApkExtensionInstaller
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelPluginId
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.extension.novel.runtime.hasVisiblePluginSettingsByDiscovery
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.extensionstore.model.repoDisplayNameFallback
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class NovelExtensionsScreenModel(
    private val extensionManager: NovelExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val context: Application = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val installCoordinator: UnifiedApkExtensionInstaller = Injekt.get(),
) : StateScreenModel<NovelExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())
    private val allPluginVariants = MutableStateFlow<Map<String, List<NovelPlugin.Available>>>(emptyMap())
    private val lastDiagnostics = MutableStateFlow<Map<String, ExtensionInstallDiagnostic>>(emptyMap())
    private val installedPluginsSnapshot = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())
    private val apkFileStore = ExtensionApkFileStore(basePreferences)

    // BEXT-6: mutated from several IO coroutines (install tracking, observers) - synchronize;
    // the check-then-act in launchInstall stays confined to its single-flight comment's scope.
    private val activeInstallJobs: MutableMap<String, Job> =
        java.util.Collections.synchronizedMap(mutableMapOf())
    private val installStateObservers: MutableMap<String, Job> =
        java.util.Collections.synchronizedMap(mutableMapOf())

    /**
     * Completed when a signature-mismatch event leaves the UI (resolved via reinstall or
     * dismissed). Starts completed so awaiters before any mismatch are released immediately;
     * consumed by the extension-lifecycle follow-up work.
     */
    // BEXT-12: @Volatile - written by the Main-thread event collector (:244), awaited by the
    // update-all queue on IO; without it the IO reader could observe a stale reference.
    @Volatile
    private var signatureResolutionSignal = CompletableDeferred<Unit>().apply { complete(Unit) }

    /** Keys this observer itself put into [currentDownloads]; cleaned when their store step completes. */
    private val mirroredDownloadKeys = ConcurrentHashMap<String, MutableSet<String>>()

    init {
        screenModelScope.launchIO {
            val sourceStateFlow = combine(
                currentDownloads,
                extensionManager.installedPluginsFlow,
                extensionManager.installedSourcesFlow,
                extensionManager.availablePluginsFlow,
                extensionManager.untrustedPluginsFlow,
            ) { downloads, installed, installedSources, available, untrusted ->
                installedPluginsSnapshot.value = installed
                ListingSourceState(
                    downloads = downloads,
                    installed = installed,
                    installedSources = installedSources,
                    available = available,
                    untrusted = untrusted,
                )
            }
            val listingFlow = combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                sourceStateFlow,
            ) { query, sourceState ->
                ListingInput(
                    query = query?.trim().orEmpty(),
                    downloads = sourceState.downloads,
                    installed = sourceState.installed,
                    installedSources = sourceState.installedSources,
                    available = sourceState.available,
                    untrusted = sourceState.untrusted,
                )
            }

            combine(
                sourcePreferences.showNsfwSource().changes(),
                sourcePreferences.enabledLanguages().changes(),
                listingFlow,
            ) { showNsfwSources, enabledLanguages, input ->
                val variantsMap = input.available.groupBy { it.id }
                allPluginVariants.value = variantsMap
                val repoCounts = variantsMap.mapValues { (_, plugins) ->
                    plugins.map { it.repoUrl }.distinct().size
                }
                val available = variantsMap.mapNotNull { (_, plugins) ->
                    plugins.maxWithOrNull(NOVEL_AVAILABLE_COMPARATOR)
                }
                val installedSettingsSourceIdsByPluginId = input.installedSources
                    .asSequence()
                    .filter { source -> source.hasVisiblePluginSettingsByDiscovery() }
                    .mapNotNull { source ->
                        val pluginId = (source as? NovelPluginIdentitySource)?.pluginId ?: return@mapNotNull null
                        pluginId to source.id
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, sourceIds) -> sourceIds.first() }
                val searchQuery = input.query

                val updateStatesById = input.installed.associate { plugin ->
                    plugin.id to NovelPluginUpdateClassifier.classify(
                        installed = plugin,
                        variants = variantsMap[plugin.id].orEmpty(),
                    )
                }
                val updateIds = updateStatesById
                    .filterValues { it.hasAnyUpdate }
                    .keys
                val installedIds = (input.installed.map { it.id } + input.untrusted.map { it.id }).toSet()
                val matches: (NovelPlugin) -> Boolean = { plugin ->
                    if (searchQuery.isEmpty()) {
                        true
                    } else {
                        plugin.name.contains(searchQuery, ignoreCase = true) ||
                            plugin.id.contains(searchQuery, ignoreCase = true) ||
                            plugin.lang.contains(searchQuery, ignoreCase = true) ||
                            plugin.site.contains(searchQuery, ignoreCase = true)
                    }
                }

                val availableSorted = available
                    .filter { it.id !in installedIds }
                    .filter(matches)
                    .filter { showNsfwSources || !it.isNsfw }
                    .filter { it.lang in enabledLanguages }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)

                val updatesList = input.installed.filter { it.id in updateIds }.filter(matches)
                    .filter { showNsfwSources || !it.isNsfw }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                val installedList = input.installed.filter { it.id !in updateIds }.filter(matches)
                    .filter { showNsfwSources || !it.isNsfw }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                val untrustedList = input.untrusted.filter(matches)
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                val items = buildList {
                    updatesList.forEach { plugin ->
                        val updateState = updateStatesById.getValue(plugin.id)
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.UpdateAvailable,
                                installStep = input.downloads[plugin.id] ?: InstallStep.Idle,
                                settingsSourceId = plugin.settingsSourceId(installedSettingsSourceIdsByPluginId),
                                repoSourceCount = repoCounts[plugin.id] ?: 1,
                                hasUpdate = updateState.hasSameRepoUpdate,
                                hasRepoUpdate = updateState.hasOtherRepoUpdate,
                                repoDisplayName = plugin.fallbackRepoDisplayName(variantsMap[plugin.id].orEmpty()),
                            ),
                        )
                    }
                    installedList.forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.Installed,
                                installStep = input.downloads[plugin.id] ?: InstallStep.Idle,
                                settingsSourceId = plugin.settingsSourceId(installedSettingsSourceIdsByPluginId),
                                repoSourceCount = repoCounts[plugin.id] ?: 1,
                                repoDisplayName = plugin.fallbackRepoDisplayName(variantsMap[plugin.id].orEmpty()),
                            ),
                        )
                    }
                    untrustedList.forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.Untrusted,
                                installStep = input.downloads[plugin.id] ?: InstallStep.Idle,
                                settingsSourceId = null,
                            ),
                        )
                    }
                    availableSorted.values.flatten().forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.Available,
                                installStep = input.downloads[plugin.id] ?: InstallStep.Idle,
                                settingsSourceId = null,
                                repoSourceCount = repoCounts[plugin.id] ?: 1,
                            ),
                        )
                    }
                }

                Triple(items, updateIds.size, availableSorted.keys.toList())
            }
                .collectLatest { (items, updatesCount, availableLanguages) ->
                    sourcePreferences.novelExtensionUpdatesCount().set(updatesCount)
                    mutableState.update { state ->
                        val normalizedCollapsed = state.collapsedLanguages.intersect(availableLanguages.toSet())
                        state.copy(
                            isLoading = false,
                            items = items,
                            updates = updatesCount,
                            availableLanguages = availableLanguages,
                            collapsedLanguages = normalizedCollapsed,
                        )
                    }
                }
        }

        screenModelScope.launchIO { refresh() }

        extensionManager.signatureMismatchEvents
            .onEach { event ->
                // Fresh signal per event: queue workers awaiting resolution suspend until
                // the dialog is dismissed or the reinstall finishes (see awaitSignatureResolution).
                // BEXT-12: complete the PREVIOUS signal before swapping - an update-all already
                // awaiting the old deferred would otherwise hang forever when a second
                // signature-mismatch event replaced it with an unfinished one.
                signatureResolutionSignal.complete(Unit)
                signatureResolutionSignal = CompletableDeferred()
                mutableState.update { state -> state.copy(signatureMismatchEvent = event) }
            }
            .launchIn(screenModelScope)

        // Repo indexes that failed to load are otherwise invisible: the manager returns whatever
        // it has and the user cannot tell a dead repo from an empty one.
        extensionManager.repoFetchErrors
            .distinctUntilChanged()
            .onEach { errors ->
                if (errors.isNotEmpty()) {
                    val summary = errors.entries.joinToString("\n") { (url, msg) -> "$url: $msg" }
                    Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                }
            }
            .launchIn(screenModelScope)

        observeInstallStates()
    }

    fun refresh() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }
            try {
                extensionManager.refreshAvailablePlugins()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to refresh novel plugins" }
            } finally {
                mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun toggleSection(language: String) {
        mutableState.update { state ->
            val collapsed = if (language in state.collapsedLanguages) {
                state.collapsedLanguages - language
            } else {
                state.collapsedLanguages + language
            }
            state.copy(collapsedLanguages = collapsed)
        }
    }

    fun installExtension(plugin: NovelPlugin.Available) {
        screenModelScope.launchIO {
            val variants = allPluginVariants.value[plugin.id].orEmpty()
            if (variants.size > 1) {
                mutableState.update {
                    it.copy(
                        repoPickerPluginId = plugin.id,
                        repoPickerOptions = variants.sortedByDescending { v -> v.versionCode },
                    )
                }
            } else {
                launchInstall(plugin)
            }
        }
    }

    fun cancelInstall(plugin: NovelPlugin) {
        // Real cancellation: stop the install coroutine and tell the manager/coordinator to
        // abandon the attempt (including its pending-permission queue entry).
        activeInstallJobs.remove(plugin.id)?.cancel()
        when (plugin) {
            is NovelPlugin.Available -> extensionManager.cancelPluginInstall(plugin)
            is NovelPlugin.Installed -> extensionManager.cancelPluginInstall(plugin)
            is NovelPlugin.Untrusted -> Unit
        }
        removeDownloadState(plugin)
    }

    fun diagnosticFor(plugin: NovelPlugin): String {
        return (lastDiagnostics.value[plugin.id] ?: buildDiagnostic(plugin, null)).format()
    }

    fun shareApk(plugin: NovelPlugin) {
        screenModelScope.launch {
            apkFileStore.share(context, plugin.packageNameForDiagnostic())
        }
    }

    fun installerCompatibilityDiagnostic(): String {
        return ExtensionInstallDiagnostic.getInstallerDiagnosticString(context, basePreferences)
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            // Full installed set, not the rendered list: search filters and collapsed language
            // sections hide items whose updates still must be applied (parity with c344dcc09).
            // Sequential awaited queue: each update finishes (or fails into row diagnostics)
            // before the next starts, and the loop suspends while a signature-mismatch
            // dialog is open so the user resolves it before the queue moves on.
            installedPluginsSnapshot.value.forEach { installed ->
                val updateState = NovelPluginUpdateClassifier.classify(
                    installed = installed,
                    variants = allPluginVariants.value[installed.id].orEmpty(),
                )
                when {
                    updateState.sameRepoUpdate != null -> {
                        installAwaiting(updateState.sameRepoUpdate)
                        awaitSignatureResolution()
                    }
                    // Never silently skip a reinstall-needing plugin (B5, parity with manga/anime):
                    // pause the queue until the user resolves the reinstall dialog for this
                    // plugin; dismissing it skips it.
                    updateState.hasOtherRepoUpdate -> {
                        resolveQueuedReinstall(installed, updateState.otherRepoUpdates)
                    }
                }
            }
        }
    }

    /** Set while the update-all queue is waiting for a reinstall decision on this plugin (B5). */
    // @Volatile: written by the update-all coroutine on IO right after publishing the dialog
    // state, read by the caller thread completing the decision — without it the completion
    // may observe a stale null and the reinstall dialog never dismisses.
    @Volatile
    private var queuedReinstallResolution: CompletableDeferred<NovelPlugin.Available?>? = null

    private suspend fun resolveQueuedReinstall(
        installed: NovelPlugin.Installed,
        candidates: List<NovelPlugin.Available>,
    ) {
        // Publish the resolution slot BEFORE the dialog state: a completion can only be
        // sent after the caller observes the dialog, so ordering the slot first closes
        // the race where the decision lands on the previous (null) slot and the dialog
        // never dismisses.
        val resolution = CompletableDeferred<NovelPlugin.Available?>()
        queuedReinstallResolution = resolution
        mutableState.update {
            it.copy(queuedReinstallPlugin = installed, queuedReinstallCandidates = candidates)
        }
        val chosen = resolution.await()
        mutableState.update {
            it.copy(queuedReinstallPlugin = null, queuedReinstallCandidates = emptyList())
        }
        if (chosen != null) {
            reinstallTracked(installed, chosen)
        }
    }

    fun resolveQueuedReinstall(replacement: NovelPlugin.Available?) {
        queuedReinstallResolution?.complete(replacement)
        queuedReinstallResolution = null
    }

    private suspend fun installAwaiting(plugin: NovelPlugin.Available) {
        try {
            installExtensionNow(plugin)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Diagnostics already recorded by installExtensionNow; keep draining the queue.
        }
    }

    private suspend fun awaitSignatureResolution() {
        signatureResolutionSignal.await()
    }

    fun updateExtension(plugin: NovelPlugin.Installed) {
        screenModelScope.launchIO {
            val available = getSameRepoUpdate(plugin) ?: return@launchIO
            launchInstall(available)
        }
    }

    fun getReinstallCandidates(plugin: NovelPlugin.Installed): List<NovelPlugin.Available> {
        return NovelPluginUpdateClassifier.classify(
            installed = plugin,
            variants = allPluginVariants.value[plugin.id].orEmpty(),
        ).otherRepoUpdates
    }

    fun reinstallFromRepo(installed: NovelPlugin.Installed, replacement: NovelPlugin.Available) {
        dismissRepoPicker()
        screenModelScope.launchIO { reinstallTracked(installed, replacement) }
    }

    /**
     * Replaces an installed plugin with [replacement] while tracking progress on the row:
     * failures surface as a row error with diagnostics instead of escaping to the global
     * crash handler. Also signals signature-mismatch resolution for awaiting consumers.
     */
    private suspend fun reinstallTracked(installed: NovelPlugin.Installed, replacement: NovelPlugin.Available) {
        addDownloadState(installed, InstallStep.Installing)
        try {
            extensionManager.replacePluginFromRepo(installed, replacement)
            clearDiagnostic(installed)
            addDownloadState(installed, InstallStep.Installed)
            removeDownloadState(installed)
        } catch (e: CancellationException) {
            removeDownloadState(installed)
            throw e
        } catch (e: Throwable) {
            val diagnostic = buildDiagnostic(replacement, e)
            lastDiagnostics.update { it + (installed.id to diagnostic) }
            logcat(LogPriority.WARN, e) {
                "Failed to reinstall novel plugin ${installed.id} from ${replacement.repoUrl}\n${diagnostic.format()}"
            }
            addDownloadState(installed, InstallStep.Error)
        } finally {
            signatureResolutionSignal.complete(Unit) // Task 9 hook; harmless standalone
        }
    }

    private fun buildDiagnostic(plugin: NovelPlugin, failure: Throwable?): ExtensionInstallDiagnostic {
        return ExtensionInstallDiagnostic.forNovelPlugin(
            context = context,
            basePreferences = basePreferences,
            packageName = plugin.packageNameForDiagnostic(),
            displayName = plugin.name,
            repoUrl = plugin.repoUrl,
            assetUrl = plugin.assetUrlForDiagnostic(),
            isKotlinExtension = plugin.isKotlinExtensionForDiagnostic(),
            failure = failure,
        )
    }

    private fun clearDiagnostic(plugin: NovelPlugin) {
        lastDiagnostics.update { it - plugin.id }
    }

    private fun NovelPlugin.packageNameForDiagnostic(): String {
        return when (this) {
            is NovelPlugin.Available -> pkgName ?: id
            is NovelPlugin.Installed -> pkgName ?: id
            is NovelPlugin.Untrusted -> pkgName
        }
    }

    private fun NovelPlugin.assetUrlForDiagnostic(): String {
        return when (this) {
            is NovelPlugin.Available -> apkUrl ?: url
            is NovelPlugin.Installed -> apkUrl ?: url
            is NovelPlugin.Untrusted -> url
        }
    }

    private fun NovelPlugin.isKotlinExtensionForDiagnostic(): Boolean {
        return when (this) {
            is NovelPlugin.Available -> isKotlinExtension
            is NovelPlugin.Installed -> isKotlinExtension
            is NovelPlugin.Untrusted -> isKotlinExtension
        }
    }

    private fun getSameRepoUpdate(plugin: NovelPlugin.Installed): NovelPlugin.Available? {
        return NovelPluginUpdateClassifier.classify(
            installed = plugin,
            variants = allPluginVariants.value[plugin.id].orEmpty(),
        ).sameRepoUpdate
    }

    fun installFromRepo(plugin: NovelPlugin.Available) {
        dismissRepoPicker()
        // BEXT-6: route through the single-flight tracked install - the direct
        // screenModelScope.launchIO { installExtensionNow } bypassed activeInstallJobs, so
        // cancelInstall could not stop it and a duplicate request raced the shared <pkg>.apk.part
        // download file (the exact hazard launchInstall's comment warns about).
        launchInstall(plugin)
    }

    fun dismissRepoPicker() {
        mutableState.update { it.copy(repoPickerPluginId = null, repoPickerOptions = emptyList()) }
    }

    fun reinstallAfterSignatureMismatch() {
        val event = state.value.signatureMismatchEvent ?: return
        val candidate = event.candidate
        val installed = installedPluginsSnapshot.value.firstOrNull { it.id == event.pluginId }
        dismissSignatureMismatch()
        if (installed == null || candidate == null) return
        screenModelScope.launchIO { reinstallTracked(installed, candidate) }
    }

    fun dismissSignatureMismatch() {
        mutableState.update { it.copy(signatureMismatchEvent = null) }
        signatureResolutionSignal.complete(Unit)
    }

    fun uninstallExtension(plugin: NovelPlugin.Installed) {
        screenModelScope.launchIO {
            extensionManager.uninstallPlugin(plugin)
        }
    }

    fun uninstallExtension(plugin: NovelPlugin.Untrusted) {
        screenModelScope.launchIO {
            extensionManager.uninstallPlugin(plugin)
        }
    }

    fun trust(extension: NovelPlugin.Untrusted) {
        screenModelScope.launchIO {
            extensionManager.trustPlugin(extension)
        }
    }

    private fun addDownloadState(plugin: NovelPlugin, installStep: InstallStep) {
        currentDownloads.update { it + Pair(plugin.id, installStep) }
    }

    private fun removeDownloadState(plugin: NovelPlugin) {
        currentDownloads.update { it - plugin.id }
    }

    private fun removeDownloadStateById(pluginId: String) {
        currentDownloads.update { it - pluginId }
    }

    /**
     * Mirrors installer-side steps from the coordinator's state store into [currentDownloads]
     * so an install started outside this ScreenModel (auto-resume after process death, or a
     * ScreenModel recreation mid-install) still shows progress. Entries this observer mirrored
     * are removed again once the store reaches a completed step, unless the row deliberately
     * displays an Error diagnostic that owns its own lifecycle: such rows are never overwritten
     * by mirrored progress, and their cleanup is retried on a later terminal step once the
     * diagnostic is gone.
     */
    private fun observeInstallStates() {
        screenModelScope.launchIO {
            extensionManager.installedPluginsFlow.collectLatest { installed ->
                val wanted = installed.mapNotNull { it.pkgName }.toSet()
                synchronized(installStateObservers) {
                    installStateObservers.keys.filter { it !in wanted }.forEach { pkgName ->
                        installStateObservers.remove(pkgName)?.cancel()
                        mirroredDownloadKeys.remove(pkgName)?.forEach { pluginId ->
                            removeDownloadStateById(pluginId)
                        }
                    }
                }
                wanted.forEach { pkgName ->
                    val alreadyObserved = synchronized(installStateObservers) {
                        installStateObservers.containsKey(pkgName)
                    }
                    if (alreadyObserved) return@forEach
                    val job = screenModelScope.launchIO {
                        installCoordinator.observe(pkgName).collect { step ->
                            if (
                                step == InstallStep.Pending ||
                                step == InstallStep.Downloading ||
                                step == InstallStep.Installing
                            ) {
                                val pluginId = installed.firstOrNull { it.pkgName == pkgName }?.id ?: pkgName
                                // A deliberate Error display (reinstall diagnostics) owns its own
                                // lifecycle: never overwrite it with store-driven progress and do
                                // not claim ownership of the row either.
                                if (lastDiagnostics.value.containsKey(pluginId)) return@collect
                                mirroredDownloadKeys.getOrPut(pkgName) {
                                    Collections.newSetFromMap(ConcurrentHashMap())
                                }.add(pluginId)
                                currentDownloads.update { it + (pluginId to step) }
                            } else if (step.isCompleted()) {
                                val mirrored = mirroredDownloadKeys.remove(pkgName).orEmpty()
                                // Decide per key BEFORE discarding ownership: rows this observer
                                // owns are cleaned; rows still under a deliberate Error display are
                                // re-inserted so a future terminal step retries their cleanup
                                // instead of stranding them until disposal.
                                val diagnosticOwned = mutableSetOf<String>()
                                mirrored.forEach { pluginId ->
                                    if (lastDiagnostics.value.containsKey(pluginId)) {
                                        diagnosticOwned += pluginId
                                    } else {
                                        removeDownloadStateById(pluginId)
                                    }
                                }
                                if (diagnosticOwned.isNotEmpty()) {
                                    mirroredDownloadKeys.getOrPut(pkgName) {
                                        Collections.newSetFromMap(ConcurrentHashMap())
                                    }.addAll(diagnosticOwned)
                                }
                            }
                        }
                    }
                    synchronized(installStateObservers) { installStateObservers[pkgName] = job }
                }
            }
        }
    }

    /** Launches a tracked install so [cancelInstall] can stop the real work, not just the UI. */
    private fun launchInstall(plugin: NovelPlugin.Available) {
        // Single-flight: a duplicate request while an install is running would race the
        // shared <pkg>.apk.part download file and the per-package install state store.
        if (activeInstallJobs[plugin.id]?.isActive == true) return
        val job = screenModelScope.launchIO {
            try {
                installExtensionNow(plugin)
            } finally {
                activeInstallJobs.remove(plugin.id)
            }
        }
        activeInstallJobs[plugin.id] = job
    }

    private suspend fun installExtensionNow(plugin: NovelPlugin.Available) {
        addDownloadState(plugin, InstallStep.Installing)
        try {
            extensionManager.installPlugin(plugin)
            clearDiagnostic(plugin)
            addDownloadState(plugin, InstallStep.Installed)
            removeDownloadState(plugin)
        } catch (e: CancellationException) {
            removeDownloadState(plugin)
            throw e
        } catch (e: Throwable) {
            val reason = e.message ?: e::class.simpleName.orEmpty()
            val diagnostic = buildDiagnostic(plugin, e)
            lastDiagnostics.update { it + (plugin.id to diagnostic) }
            logcat(LogPriority.WARN, e) {
                "Failed to install novel plugin ${plugin.id}: $reason\n${diagnostic.format()}"
            }
            addDownloadState(plugin, InstallStep.Error)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: List<NovelExtensionItem> = emptyList(),
        val updates: Int = 0,
        val searchQuery: String? = null,
        val availableLanguages: List<String> = emptyList(),
        val collapsedLanguages: Set<String> = emptySet(),
        val repoPickerPluginId: String? = null,
        val repoPickerOptions: List<NovelPlugin.Available> = emptyList(),
        val signatureMismatchEvent: NovelExtensionManager.SignatureMismatchEvent? = null,
        /** Set while the update-all queue is paused on a plugin needing reinstall (B5). */
        val queuedReinstallPlugin: NovelPlugin.Installed? = null,
        val queuedReinstallCandidates: List<NovelPlugin.Available> = emptyList(),
    )

    private companion object {
        val NOVEL_AVAILABLE_COMPARATOR = compareBy<NovelPlugin.Available> { it.versionCode }
            .thenBy { it.repoName.ifBlank { it.repoUrl } }
            .thenBy { it.repoUrl }
    }

    private data class ListingInput(
        val query: String,
        val downloads: Map<String, InstallStep>,
        val installed: List<NovelPlugin.Installed>,
        val installedSources: List<eu.kanade.tachiyomi.novelsource.NovelSource>,
        val available: List<NovelPlugin.Available>,
        val untrusted: List<NovelPlugin.Untrusted>,
    )

    private data class ListingSourceState(
        val downloads: Map<String, InstallStep>,
        val installed: List<NovelPlugin.Installed>,
        val installedSources: List<eu.kanade.tachiyomi.novelsource.NovelSource>,
        val available: List<NovelPlugin.Available>,
        val untrusted: List<NovelPlugin.Untrusted>,
    )
}

data class NovelExtensionItem(
    val plugin: NovelPlugin,
    val status: Status,
    val installStep: InstallStep,
    val settingsSourceId: Long?,
    val repoSourceCount: Int = 1,
    val hasUpdate: Boolean = false,
    val hasRepoUpdate: Boolean = false,
    val repoDisplayName: String? = null,
) {
    val hasSettings: Boolean
        get() = settingsSourceId != null

    sealed interface Status {
        data object UpdateAvailable : Status
        data object Installed : Status
        data object Untrusted : Status
        data object Available : Status
    }
}

private fun NovelPlugin.Installed.settingsSourceId(
    sourceIdsByPluginId: Map<String, Long>,
): Long? {
    return sourceIdsByPluginId[id] ?: if (hasSettings) NovelPluginId.toSourceId(id) else null
}

private fun NovelPlugin.Installed.fallbackRepoDisplayName(
    variants: List<NovelPlugin.Available>,
): String? {
    // The name persisted at install time is a snapshot: it goes stale when the user renames the
    // store. Prefer the label of the current store serving this exact repo URL, fall back to the
    // snapshot, then to a human-readable form of the URL.
    val freshName = repoUrl.takeIf { it.isNotBlank() }?.let { url ->
        variants.firstOrNull { it.repoUrl == url }?.repoName?.takeIf { it.isNotBlank() }
    }
    freshName?.let { return it }
    repoName?.takeIf { it.isNotBlank() }?.let { return it }
    repoUrl.takeIf { it.isNotBlank() }?.let { return it.repoDisplayNameFallback() }

    val exactVersionMatches = variants.filter {
        it.versionCode == versionCode
    }
    val displayCandidate = exactVersionMatches.singleOrNull()
        ?: variants.singleOrNull()

    return displayCandidate?.repoName?.ifBlank { displayCandidate.repoUrl.repoDisplayNameFallback() }
}
