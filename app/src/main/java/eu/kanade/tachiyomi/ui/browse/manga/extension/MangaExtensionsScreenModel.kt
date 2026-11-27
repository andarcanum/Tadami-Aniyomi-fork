package eu.kanade.tachiyomi.ui.browse.manga.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.updater.ExtensionUpdateJob
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.ApkExtensionKind
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.newestByVersion
import eu.kanade.tachiyomi.extension.manga.model.selectMangaInstalledRepoDisplayName
import eu.kanade.tachiyomi.extension.manga.model.selectMangaReinstallCandidates
import eu.kanade.tachiyomi.extension.manga.toInstalledMangaExtensionPkgName
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.ExtensionSearchFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MangaExtensionsScreenModel(
    preferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
    private val getExtensions: GetMangaExtensionsByType = Injekt.get(),
    private val context: Application = Injekt.get(),
) : StateScreenModel<MangaExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())
    private val collapsedLanguages = MutableStateFlow<Set<String>>(emptySet())
    private val availableExtensionVariants = MutableStateFlow<Map<String, List<MangaExtension.Available>>>(emptyMap())
    private val updateExtensionVariants = MutableStateFlow<Map<String, List<MangaExtension.Available>>>(emptyMap())

    init {
        val extensionMapper: (
            Map<String, InstallStep>,
            Map<String, Int>,
            Map<String, List<MangaExtension.Available>>,
        ) -> ((MangaExtension) -> MangaExtensionUiModel.Item) = { map, repoCounts, variantsByPkgName ->
            { extension ->
                MangaExtensionUiModel.Item(
                    extension = extension,
                    installStep = map[extension.pkgName] ?: InstallStep.Idle,
                    repoSourceCount = repoCounts[extension.pkgName] ?: 1,
                    repoDisplayName = (extension as? MangaExtension.Installed)
                        ?.fallbackRepoDisplayName(variantsByPkgName[extension.pkgName].orEmpty()),
                )
            }
        }
        val queryFilter: (String) -> ((MangaExtension) -> Boolean) = { query ->
            if (query.isEmpty()) {
                { true }
            } else {
                val terms = ExtensionSearchFilter.parseTerms(query)
                if (terms.isEmpty()) {
                    { false }
                } else {
                    { extension ->
                        terms.any { (input, sourceId) ->
                            when (extension) {
                                is MangaExtension.Available -> {
                                    extension.name.contains(input, ignoreCase = true) ||
                                        extension.sources.any {
                                            it.name.contains(input, ignoreCase = true) ||
                                                it.baseUrl.contains(input, ignoreCase = true) ||
                                                (sourceId != null && it.id == sourceId)
                                        }
                                }
                                is MangaExtension.Installed -> {
                                    extension.name.contains(input, ignoreCase = true) ||
                                        extension.sources.any {
                                            it.name.contains(input, ignoreCase = true) ||
                                                (sourceId != null && it.id == sourceId) ||
                                                if (it is HttpSource) {
                                                    it.baseUrl.contains(
                                                        input,
                                                        ignoreCase = true,
                                                    ) ||
                                                        it.getHomeUrl().contains(input, ignoreCase = true)
                                                } else {
                                                    false
                                                }
                                        }
                                }
                                is MangaExtension.Untrusted -> extension.name.contains(
                                    input,
                                    ignoreCase = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                currentDownloads,
                getExtensions.subscribe(),
                extensionManager.availableExtensionsFlow,
                collapsedLanguages,
            ) { query, downloads, (_updates, _installed, _available, _untrusted), rawAvailable, _collapsedLanguages ->
                val searchQuery = query ?: ""
                val filterPredicate = queryFilter(searchQuery)

                val itemsGroups: ItemGroups = mutableMapOf()
                val availableByPkg = _available.groupBy { it.pkgName }
                availableExtensionVariants.value = availableByPkg
                // Installed names are normalized (suffix stripped at install time): the update
                // variants map must use the same key or lookups by extension.pkgName miss.
                val updateVariantsByPkg = rawAvailable.groupBy {
                    it.pkgName.toInstalledMangaExtensionPkgName()
                }
                updateExtensionVariants.value = updateVariantsByPkg
                val availableRepoCounts = availableByPkg.mapValues { (_, variants) ->
                    ExtensionSearchFilter.distinctRepoCount(variants) { it.repoUrl }
                }
                val updateRepoCounts = rawAvailable
                    .groupBy { it.pkgName }
                    .mapValues { (_, variants) ->
                        ExtensionSearchFilter.distinctRepoCount(variants) { it.repoUrl }
                    }
                val repoCounts = availableRepoCounts + updateRepoCounts
                val displayAvailable = if (searchQuery.isEmpty()) {
                    availableByPkg.values
                        .mapNotNull { variants -> variants.newestByVersion() }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                } else {
                    _available
                        .filter(filterPredicate)
                        .groupBy { it.pkgName }
                        .mapNotNull { (_, variants) -> variants.newestByVersion() }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                }

                val updateMapper = extensionMapper(downloads, repoCounts, updateVariantsByPkg)
                val updates = (if (searchQuery.isEmpty()) _updates else _updates.filter(filterPredicate))
                    .map(updateMapper)
                if (updates.isNotEmpty()) {
                    itemsGroups[MangaExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending)] = updates
                }

                val installed = (if (searchQuery.isEmpty()) _installed else _installed.filter(filterPredicate))
                    .map(updateMapper)
                val untrusted = (if (searchQuery.isEmpty()) _untrusted else _untrusted.filter(filterPredicate))
                    .map(extensionMapper(downloads, repoCounts, emptyMap()))
                if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                    itemsGroups[MangaExtensionUiModel.Header.Resource(MR.strings.ext_installed)] = installed + untrusted
                }

                val languagesWithExtensions = displayAvailable
                    .filter { it.lang.isNotBlank() }
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)
                    .map { (lang, exts) ->
                        val header = MangaExtensionUiModel.Header.Text(
                            LocaleHelper.getSourceDisplayName(lang, context),
                        )
                        val items = if (header.text in _collapsedLanguages && searchQuery.isEmpty()) {
                            emptyList()
                        } else {
                            exts.map(extensionMapper(downloads, repoCounts, availableExtensionVariants.value))
                        }
                        header to items
                    }

                if (languagesWithExtensions.isNotEmpty()) {
                    itemsGroups.putAll(languagesWithExtensions)
                }

                itemsGroups
            }
                .conflate()
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = it,
                            collapsedLanguages = collapsedLanguages.value,
                        )
                    }
                }
        }

        screenModelScope.launchIO { findAvailableExtensions() }

        preferences.mangaExtensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { mutableState.update { state -> state.copy(installer = it) } }
            .launchIn(screenModelScope)

        extensionManager.signatureMismatchEvents
            .onEach { event ->
                mutableState.update { state ->
                    state.copy(signatureMismatchEvent = event)
                }
            }
            .launchIn(screenModelScope)

        runCatching {
            context.workManager.getWorkInfosForUniqueWorkLiveData(
                ExtensionUpdateJob.getWorkName(ApkExtensionKind.MANGA),
            ).asFlow()
        }.getOrNull()
            ?.onEach { list ->
                val workInfo = list.firstOrNull()
                if (workInfo?.state == WorkInfo.State.CANCELLED && updateAllJob?.isActive == true) {
                    cancelUpdateAll()
                    return@onEach
                }
                if (updateAllJob?.isActive == true) {
                    return@onEach
                }
                val isRunning = workInfo != null && (
                    workInfo.state == WorkInfo.State.RUNNING ||
                        workInfo.state == WorkInfo.State.ENQUEUED
                    )
                val current = workInfo?.progress?.getInt(ExtensionUpdateJob.KEY_CURRENT, 0) ?: 0
                val total = workInfo?.progress?.getInt(ExtensionUpdateJob.KEY_TOTAL, 0) ?: 0
                mutableState.update { state ->
                    state.copy(
                        isUpdatingAll = isRunning,
                        updatingAllProgress = if (isRunning && total > 0) current to total else null,
                    )
                }
            }
            ?.launchIn(screenModelScope)
    }

    fun reinstallAfterSignatureMismatch() {
        val event = state.value.signatureMismatchEvent ?: return
        val candidate = event.candidate
        val installed = extensionManager.installedExtensionsFlow.value
            .firstOrNull { it.pkgName == event.packageName }
        dismissSignatureMismatch()
        if (installed == null || candidate == null) return
        reinstallFromRepo(installed, candidate)
    }

    fun dismissSignatureMismatch() {
        mutableState.update { state -> state.copy(signatureMismatchEvent = null) }
    }

    fun toggleSection(header: MangaExtensionUiModel.Header.Text) {
        collapsedLanguages.update {
            if (it.contains(header.text)) {
                it - header.text
            } else {
                it + header.text
            }
        }
    }

    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    private var updateAllJob: Job? = null

    @Volatile
    private var currentUpdatingExtension: MangaExtension.Installed? = null

    fun updateAllExtensions() {
        if (updateAllJob?.isActive == true) return
        val extensionsToUpdate = extensionManager.installedExtensionsFlow.value
            .filter { it.hasUpdate }
        val total = extensionsToUpdate.size
        mutableState.update {
            it.copy(
                isUpdatingAll = true,
                updatingAllProgress = if (total > 0) 0 to total else null,
            )
        }
        ExtensionUpdateJob.onBatchStarted(ApkExtensionKind.MANGA, total) {
            cancelUpdateAll()
        }
        ExtensionUpdateJob.start(context, ApkExtensionKind.MANGA)
        updateAllJob = screenModelScope.launchIO {
            try {
                // Source candidates from the manager flow, not the rendered list: search filters
                // and collapsed language sections hide items whose updates still must be applied.
                for ((index, extension) in extensionsToUpdate.withIndex()) {
                    mutableState.update {
                        it.copy(
                            isUpdatingAll = true,
                            updatingAllProgress = index to total,
                        )
                    }
                    ExtensionUpdateJob.onBatchProgress(ApkExtensionKind.MANGA, index, total)
                    currentUpdatingExtension = extension
                    try {
                        if (extension.needsReinstall) {
                            // Never skip silently (B5): pause the queue until the user resolves
                            // the reinstall dialog for this extension; null = dismissed → skip.
                            resolveQueuedReinstall(extension)
                        } else {
                            updateExtensionNow(extension)
                        }
                    } finally {
                        currentUpdatingExtension = null
                    }
                    val completed = index + 1
                    mutableState.update {
                        it.copy(
                            isUpdatingAll = true,
                            updatingAllProgress = completed to total,
                        )
                    }
                    ExtensionUpdateJob.onBatchProgress(ApkExtensionKind.MANGA, completed, total)
                }
            } finally {
                currentUpdatingExtension = null
                ExtensionUpdateJob.onBatchFinished(context, ApkExtensionKind.MANGA)
                mutableState.update {
                    it.copy(
                        isUpdatingAll = false,
                        updatingAllProgress = null,
                    )
                }
            }
        }
    }

    fun cancelUpdateAll() {
        val job = updateAllJob
        updateAllJob = null
        job?.cancel()
        currentUpdatingExtension?.let { ext ->
            currentUpdatingExtension = null
            extensionManager.cancelInstallUpdateExtension(ext)
            removeDownloadState(ext)
        }
        queuedReinstallResolution?.complete(null)
        queuedReinstallResolution = null
        mutableState.update {
            it.copy(
                isUpdatingAll = false,
                updatingAllProgress = null,
                queuedReinstallExtension = null,
                queuedReinstallCandidates = emptyList(),
            )
        }
        ExtensionUpdateJob.stop(context, ApkExtensionKind.MANGA)
    }

    /** Set while the update-all queue waits for a reinstall decision on this extension. */
    // BEXT-3: @Volatile + the slot published BEFORE the dialog state (novel etalon :360-379):
    // a tap landing between the state update and the slot assignment used to complete() into
    // null - the decision was lost, the dialog never dismissed and the update-all queue stalled
    // until another tap; the plain var also had no cross-thread visibility guarantee.
    @Volatile
    private var queuedReinstallResolution: CompletableDeferred<MangaExtension.Available?>? = null

    private suspend fun resolveQueuedReinstall(extension: MangaExtension.Installed) {
        val resolution = CompletableDeferred<MangaExtension.Available?>()
        queuedReinstallResolution = resolution
        mutableState.update {
            it.copy(
                queuedReinstallExtension = extension,
                queuedReinstallCandidates = getReinstallCandidates(extension),
            )
        }
        val chosen = resolution.await()
        mutableState.update {
            it.copy(queuedReinstallExtension = null, queuedReinstallCandidates = emptyList())
        }
        if (chosen != null) {
            extensionManager
                .replaceExtensionFromRepo(extension, chosen)
                .collectToInstallUpdate(extension)
        }
    }

    fun resolveQueuedReinstall(replacement: MangaExtension.Available?) {
        queuedReinstallResolution?.complete(replacement)
        queuedReinstallResolution = null
    }

    fun installExtension(extension: MangaExtension.Available) {
        screenModelScope.launchIO {
            val variants = availableExtensionVariants.value[extension.pkgName].orEmpty()
            if (variants.size > 1) {
                showRepoPicker(extension.pkgName, variants)
            } else {
                installExtensionNow(extension)
            }
        }
    }

    fun updateExtension(extension: MangaExtension.Installed) {
        screenModelScope.launchIO {
            if (extension.needsReinstall) {
                // Reinstall-from-repo owns this case; the row already offers its dialog.
                return@launchIO
            }
            // No silent miss here: the manager refreshes the repo list once and emits
            // InstallStep.Error when no installable variant remains, surfacing in the UI
            // through collectToInstallUpdate.
            updateExtensionNow(extension)
        }
    }

    fun installFromRepo(extension: MangaExtension.Available) {
        dismissRepoPicker()
        screenModelScope.launchIO { installExtensionNow(extension) }
    }

    fun getReinstallCandidates(extension: MangaExtension.Installed): List<MangaExtension.Available> {
        return selectMangaReinstallCandidates(
            extension = extension,
            variants = updateExtensionVariants.value[extension.pkgName].orEmpty(),
        )
    }

    fun reinstallFromRepo(
        installedExtension: MangaExtension.Installed,
        replacementExtension: MangaExtension.Available,
    ) {
        dismissRepoPicker()
        screenModelScope.launchIO {
            extensionManager
                .replaceExtensionFromRepo(installedExtension, replacementExtension)
                .collectToInstallUpdate(installedExtension)
        }
    }

    fun dismissRepoPicker() {
        mutableState.update {
            it.copy(
                repoPickerPluginId = null,
                repoPickerOptions = emptyList(),
            )
        }
    }

    fun cancelInstallUpdateExtension(extension: MangaExtension) {
        extensionManager.cancelInstallUpdateExtension(extension)
    }

    private fun addDownloadState(extension: MangaExtension, installStep: InstallStep) {
        currentDownloads.update { it + Pair(extension.pkgName, installStep) }
    }

    private fun removeDownloadState(extension: MangaExtension) {
        currentDownloads.update { it - extension.pkgName }
    }

    private suspend fun installExtensionNow(extension: MangaExtension.Available) {
        extensionManager.installExtension(extension).collectToInstallUpdate(extension)
    }

    private suspend fun updateExtensionNow(extension: MangaExtension.Installed) {
        extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: MangaExtension) {
        // BEXT-4: hold a terminal Error until the next action for this extension overwrites or
        // removes the entry (novel side holds Errors explicitly). Previously the Error was added
        // and removed within the same coroutine tick - the conflated StateFlow never rendered it,
        // failed updates were silent and the row's Retry affordance was unreachable.
        var sawError = false
        this
            .onEach { installStep ->
                if (installStep == InstallStep.Error) sawError = true
                addDownloadState(extension, installStep)
            }
            .onCompletion { if (!sawError) removeDownloadState(extension) }
            .collect()
    }

    private fun showRepoPicker(
        pkgName: String,
        options: List<MangaExtension.Available>,
    ) {
        mutableState.update {
            it.copy(
                repoPickerPluginId = pkgName,
                repoPickerOptions = options.sortedWith(
                    compareByDescending<MangaExtension.Available> { it.versionCode }
                        .thenByDescending { it.libVersion },
                ),
            )
        }
    }

    fun uninstallExtension(extension: MangaExtension) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }
            try {
                extensionManager.findAvailableExtensions()

                // Fake slower refresh so it doesn't seem like it's not doing anything
                delay(1.seconds)
            } finally {
                mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun trustExtension(extension: MangaExtension.Untrusted) {
        screenModelScope.launch {
            extensionManager.trust(extension)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val installer: BasePreferences.ExtensionInstaller? = null,
        val searchQuery: String? = null,
        val collapsedLanguages: Set<String> = emptySet(),
        val repoPickerPluginId: String? = null,
        val repoPickerOptions: List<MangaExtension.Available> = emptyList(),
        val signatureMismatchEvent: MangaExtensionManager.SignatureMismatchEvent? = null,
        /** Set while the update-all queue is paused on an extension needing reinstall (B5). */
        val queuedReinstallExtension: MangaExtension.Installed? = null,
        val queuedReinstallCandidates: List<MangaExtension.Available> = emptyList(),
        val isUpdatingAll: Boolean = false,
        val updatingAllProgress: Pair<Int, Int>? = null, // Pair(current, total)
    ) {
        val isEmpty = items.isEmpty()
    }

    fun installerCompatibilityDiagnostic(): String {
        val app = uy.kohesive.injekt.Injekt.get<android.app.Application>()
        return eu.kanade.tachiyomi.extension.installer.ExtensionInstallDiagnostic.getInstallerDiagnosticString(
            app,
            uy.kohesive.injekt.Injekt.get(),
        )
    }
}

typealias ItemGroups = MutableMap<MangaExtensionUiModel.Header, List<MangaExtensionUiModel.Item>>

object MangaExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }

    data class Item(
        val extension: MangaExtension,
        val installStep: InstallStep,
        val repoSourceCount: Int = 1,
        val repoDisplayName: String? = null,
    )
}

private fun MangaExtension.Installed.fallbackRepoDisplayName(
    variants: List<MangaExtension.Available>,
): String? {
    return selectMangaInstalledRepoDisplayName(this, variants)
}
