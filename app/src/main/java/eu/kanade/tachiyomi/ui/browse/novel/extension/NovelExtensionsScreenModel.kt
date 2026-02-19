package eu.kanade.tachiyomi.ui.browse.novel.extension

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
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
    private val availablePlugins = MutableStateFlow<List<NovelPlugin.Available>>(emptyList())

    init {
        screenModelScope.launchIO {
            val listingFlow = combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                currentDownloads,
                extensionManager.installedPluginsFlow,
                extensionManager.availablePluginsFlow,
                extensionManager.updatesFlow,
            ) { query, downloads, installed, available, updates ->
                ListingInput(
                    query = query?.trim().orEmpty(),
                    downloads = downloads,
                    installed = installed,
                    available = available,
                    updates = updates,
                )
            }

            combine(
                sourcePreferences.enabledLanguages().changes(),
                listingFlow,
            ) { enabledLanguages, input ->
                availablePlugins.value = input.available
                val searchQuery = input.query

                val updateIds = input.updates.map { it.id }.toSet()
                val installedIds = input.installed.map { it.id }.toSet()
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

                val availableByLanguage = input.available
                    .asSequence()
                    .filter { it.id !in installedIds }
                    .filter(matches)
                    .filter { it.lang.isBlank() || it.lang in enabledLanguages }
                    .filter { it.lang.isNotBlank() }
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)

                val items = buildList {
                    input.updates.filter(matches).forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.UpdateAvailable,
                                installStep = input.downloads[plugin.id] ?: InstallStep.Idle,
                            ),
                        )
                    }
                    input.installed.filter { it.id !in updateIds }.filter(matches).forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.Installed,
                                installStep = input.downloads[plugin.id] ?: InstallStep.Idle,
                            ),
                        )
                    }
                    availableByLanguage.values.flatten().forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.Available,
                                installStep = input.downloads[plugin.id] ?: InstallStep.Idle,
                            ),
                        )
                    }
                }

                Triple(items, input.updates.size, availableByLanguage.keys.toList())
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
            addDownloadState(plugin, InstallStep.Installing)
            try {
                extensionManager.installPlugin(plugin)
                addDownloadState(plugin, InstallStep.Installed)
            } finally {
                removeDownloadState(plugin)
            }
        }
    }

    fun cancelInstall(plugin: NovelPlugin.Available) {
        currentDownloads.update { it - plugin.id }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            val updateIds = state.value.items
                .filter { it.status == NovelExtensionItem.Status.UpdateAvailable }
                .mapNotNull { it.plugin as? NovelPlugin.Installed }
                .map { it.id }
                .toSet()
            availablePlugins.value
                .filter { it.id in updateIds }
                .forEach { installExtension(it) }
        }
    }

    fun updateExtension(plugin: NovelPlugin.Installed) {
        screenModelScope.launchIO {
            val available = availablePlugins.value.firstOrNull { it.id == plugin.id } ?: return@launchIO
            installExtension(available)
        }
    }

    fun uninstallExtension(plugin: NovelPlugin.Installed) {
        screenModelScope.launchIO {
            extensionManager.uninstallPlugin(plugin)
        }
    }

    private fun addDownloadState(plugin: NovelPlugin, installStep: InstallStep) {
        currentDownloads.update { it + Pair(plugin.id, installStep) }
    }

    private fun removeDownloadState(plugin: NovelPlugin) {
        currentDownloads.update { it - plugin.id }
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
    )

    private data class ListingInput(
        val query: String,
        val downloads: Map<String, InstallStep>,
        val installed: List<NovelPlugin.Installed>,
        val available: List<NovelPlugin.Available>,
        val updates: List<NovelPlugin.Installed>,
    )
}

data class NovelExtensionItem(
    val plugin: NovelPlugin,
    val status: Status,
    val installStep: InstallStep,
) {
    sealed interface Status {
        data object UpdateAvailable : Status
        data object Installed : Status
        data object Available : Status
    }
}
