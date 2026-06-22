package eu.kanade.tachiyomi.ui.browse.novel.extension

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelPluginId
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.extension.novel.runtime.hasVisiblePluginSettingsByDiscovery
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionsScreenModel(
    private val extensionManager: NovelExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<NovelExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())
    private val allPluginVariants = MutableStateFlow<Map<String, List<NovelPlugin.Available>>>(emptyMap())

    init {
        screenModelScope.launchIO {
            val sourceStateFlow = combine(
                currentDownloads,
                extensionManager.installedPluginsFlow,
                extensionManager.installedSourcesFlow,
                extensionManager.availablePluginsFlow,
                extensionManager.untrustedPluginsFlow,
            ) { downloads, installed, installedSources, available, untrusted ->
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
                sourcePreferences.enabledLanguages().changes(),
                listingFlow,
            ) { enabledLanguages, input ->
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
                    .filter { it.lang in enabledLanguages }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)

                val updatesList = input.installed.filter { it.id in updateIds }.filter(matches)
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                val installedList = input.installed.filter { it.id !in updateIds }.filter(matches)
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
                installExtensionNow(plugin)
            }
        }
    }

    fun cancelInstall(plugin: NovelPlugin.Available) {
        currentDownloads.update { it - plugin.id }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items
                .filter { it.status == NovelExtensionItem.Status.UpdateAvailable && it.hasUpdate }
                .mapNotNull { it.plugin as? NovelPlugin.Installed }
                .mapNotNull { plugin -> getSameRepoUpdate(plugin) }
                .forEach { installExtensionNow(it) }
        }
    }

    fun updateExtension(plugin: NovelPlugin.Installed) {
        screenModelScope.launchIO {
            val available = getSameRepoUpdate(plugin) ?: return@launchIO
            installExtensionNow(available)
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
        screenModelScope.launchIO {
            addDownloadState(installed, InstallStep.Installing)
            try {
                extensionManager.replacePluginFromRepo(installed, replacement)
                addDownloadState(installed, InstallStep.Installed)
                removeDownloadState(installed)
            } catch (e: CancellationException) {
                removeDownloadState(installed)
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) {
                    "Failed to reinstall novel plugin ${installed.id} from ${replacement.repoUrl}"
                }
                addDownloadState(installed, InstallStep.Error)
            }
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
        screenModelScope.launchIO { installExtensionNow(plugin) }
    }

    fun dismissRepoPicker() {
        mutableState.update { it.copy(repoPickerPluginId = null, repoPickerOptions = emptyList()) }
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

    private suspend fun installExtensionNow(plugin: NovelPlugin.Available) {
        addDownloadState(plugin, InstallStep.Installing)
        try {
            extensionManager.installPlugin(plugin)
            addDownloadState(plugin, InstallStep.Installed)
            removeDownloadState(plugin)
        } catch (e: CancellationException) {
            removeDownloadState(plugin)
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed to install novel plugin ${plugin.id}" }
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
    repoName?.takeIf { it.isNotBlank() }?.let { return it }
    repoUrl.takeIf { it.isNotBlank() }?.let { return it }

    val exactVersionMatches = variants.filter {
        it.versionCode == versionCode
    }
    val displayCandidate = exactVersionMatches.singleOrNull()
        ?: variants.singleOrNull()

    return displayCandidate?.repoName?.ifBlank { displayCandidate.repoUrl }
}
