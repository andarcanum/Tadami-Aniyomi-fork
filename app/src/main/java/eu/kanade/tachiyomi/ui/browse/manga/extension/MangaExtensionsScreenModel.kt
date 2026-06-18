package eu.kanade.tachiyomi.ui.browse.manga.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.newestByVersion
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
) : StateScreenModel<MangaExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())
    private val collapsedLanguages = MutableStateFlow<Set<String>>(emptySet())
    private val availableExtensionVariants = MutableStateFlow<Map<String, List<MangaExtension.Available>>>(emptyMap())
    private val updateExtensionVariants = MutableStateFlow<Map<String, List<MangaExtension.Available>>>(emptyMap())

    init {
        val context = Injekt.get<Application>()
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
            filter@{ extension ->
                if (query.isEmpty()) return@filter true
                query.split(",").any { _input ->
                    val input = _input.trim()
                    if (input.isEmpty()) return@any false
                    when (extension) {
                        is MangaExtension.Available -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.baseUrl.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull()
                            } ||
                                extension.name.contains(input, ignoreCase = true)
                        }
                        is MangaExtension.Installed -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull() ||
                                    if (it is HttpSource) {
                                        it.baseUrl.contains(
                                            input,
                                            ignoreCase = true,
                                        )
                                    } else {
                                        false
                                    }
                            } ||
                                extension.name.contains(input, ignoreCase = true)
                        }
                        is MangaExtension.Untrusted -> extension.name.contains(
                            input,
                            ignoreCase = true,
                        )
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

                val itemsGroups: ItemGroups = mutableMapOf()
                availableExtensionVariants.value = _available.groupBy { it.pkgName }
                updateExtensionVariants.value = rawAvailable.groupBy { it.pkgName }
                val availableRepoCounts = _available
                    .groupBy { it.pkgName }
                    .mapValues { (_, variants) -> variants.map { it.repoUrl }.distinct().size }
                val updateRepoCounts = rawAvailable
                    .groupBy { it.pkgName }
                    .mapValues { (_, variants) -> variants.map { it.repoUrl }.distinct().size }
                val repoCounts = availableRepoCounts + updateRepoCounts
                val displayAvailable = _available
                    .filter(queryFilter(searchQuery))
                    .groupBy { it.pkgName }
                    .mapNotNull { (_, variants) -> variants.newestByVersion() }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                val updates = _updates.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads, repoCounts, updateExtensionVariants.value),
                )
                if (updates.isNotEmpty()) {
                    itemsGroups[MangaExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending)] = updates
                }

                val installed = _installed.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads, repoCounts, updateExtensionVariants.value),
                )
                val untrusted = _untrusted.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads, repoCounts, emptyMap()),
                )
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

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<MangaExtension.Installed>()
                .filter { it.hasUpdate && !it.needsReinstall }
                .forEach { updateExtensionNow(it) }
        }
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
            if (extension.needsReinstall || getRegularUpdate(extension) == null) {
                return@launchIO
            }
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

    private fun getRegularUpdate(extension: MangaExtension.Installed): MangaExtension.Available? {
        return selectMangaRegularUpdate(
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

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: MangaExtension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .onCompletion { removeDownloadState(extension) }
            .collect()

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
    ) {
        val isEmpty = items.isEmpty()
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

internal fun selectMangaInstalledRepoDisplayName(
    extension: MangaExtension.Installed,
    variants: List<MangaExtension.Available>,
): String? {
    extension.repoUrl?.let { repoUrl ->
        return extension.repoName?.takeIf { it.isNotBlank() } ?: repoUrl
    }

    val exactVersionMatches = variants.filter {
        it.versionCode == extension.versionCode && it.libVersion == extension.libVersion
    }
    val displayCandidate = exactVersionMatches.singleOrNull()
        ?: variants.singleOrNull()

    return displayCandidate?.repoName?.ifBlank { displayCandidate.repoUrl }
}

private fun inferMangaInstalledRepo(
    extension: MangaExtension.Installed,
    variants: List<MangaExtension.Available>,
): MangaExtension.Available? {
    extension.repoUrl?.let { repoUrl ->
        return variants.firstOrNull { it.repoUrl == repoUrl }
    }

    val exactVersionMatches = variants.filter {
        it.versionCode == extension.versionCode && it.libVersion == extension.libVersion
    }

    return exactVersionMatches.singleOrNull()
        ?: variants.singleOrNull()
        ?: variants
            .map { it.repoUrl }
            .distinct()
            .singleOrNull()
            ?.let { repoUrl -> variants.first { it.repoUrl == repoUrl } }
}

internal fun selectMangaSameRepoUpdate(
    extension: MangaExtension.Installed,
    variants: List<MangaExtension.Available>,
): MangaExtension.Available? {
    val repoUrl = inferMangaInstalledRepo(extension, variants)?.repoUrl ?: return null
    return variants
        .filter { it.repoUrl == repoUrl && isNewer(extension, it) }
        .latestVersionGroup()
        .firstOrNull()
}

internal fun selectMangaRegularUpdate(
    extension: MangaExtension.Installed,
    variants: List<MangaExtension.Available>,
): MangaExtension.Available? {
    selectMangaSameRepoUpdate(extension, variants)?.let { return it }

    if (inferMangaInstalledRepo(extension, variants) != null) return null

    val latestVersionGroup = variants
        .filter { isNewer(extension, it) }
        .latestVersionGroup()

    if (variants.size == 1) return latestVersionGroup.singleOrNull()
    return latestVersionGroup.takeIf { it.size > 1 }?.firstOrNull()
}

internal fun selectMangaReinstallCandidates(
    extension: MangaExtension.Installed,
    variants: List<MangaExtension.Available>,
): List<MangaExtension.Available> {
    if (selectMangaRegularUpdate(extension, variants) != null) return emptyList()

    val installedRepoUrl = inferMangaInstalledRepo(extension, variants)?.repoUrl

    return variants
        .filter { installedRepoUrl == null || it.repoUrl != installedRepoUrl }
        .filter { isNewer(extension, it) }
        .latestVersionGroup()
}

private fun List<MangaExtension.Available>.latestVersionGroup(): List<MangaExtension.Available> {
    val latest = maxWithOrNull(
        compareBy<MangaExtension.Available> { it.versionCode }
            .thenBy { it.libVersion },
    ) ?: return emptyList()

    return filter { it.versionCode == latest.versionCode && it.libVersion == latest.libVersion }
        .sortedWith(
            compareBy<MangaExtension.Available> { it.repoName.ifBlank { it.repoUrl } }
                .thenBy { it.repoUrl },
        )
}

private fun isNewer(
    extension: MangaExtension.Installed,
    candidate: MangaExtension.Available,
): Boolean {
    return candidate.versionCode > extension.versionCode || candidate.libVersion > extension.libVersion
}

private fun MangaExtension.Installed.fallbackRepoDisplayName(
    variants: List<MangaExtension.Available>,
): String? {
    return selectMangaInstalledRepoDisplayName(this, variants)
}
