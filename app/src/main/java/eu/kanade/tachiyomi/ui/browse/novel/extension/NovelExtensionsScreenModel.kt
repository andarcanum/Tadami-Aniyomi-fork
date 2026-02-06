package eu.kanade.tachiyomi.ui.browse.novel.extension

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
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
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                currentDownloads,
                extensionManager.installedPluginsFlow,
                extensionManager.availablePluginsFlow,
                extensionManager.updatesFlow,
            ) { query, downloads, installed, available, updates ->
                availablePlugins.value = available
                val searchQuery = query?.trim().orEmpty()

                val updateIds = updates.map { it.id }.toSet()
                val installedIds = installed.map { it.id }.toSet()
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

                val items = buildList {
                    updates.filter(matches).forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.UpdateAvailable,
                                installStep = downloads[plugin.id] ?: InstallStep.Idle,
                            ),
                        )
                    }
                    installed.filter { it.id !in updateIds }.filter(matches).forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.Installed,
                                installStep = downloads[plugin.id] ?: InstallStep.Idle,
                            ),
                        )
                    }
                    available.filter { it.id !in installedIds }.filter(matches).forEach { plugin ->
                        add(
                            NovelExtensionItem(
                                plugin = plugin,
                                status = NovelExtensionItem.Status.Available,
                                installStep = downloads[plugin.id] ?: InstallStep.Idle,
                            ),
                        )
                    }
                }

                items to updates.size
            }
                .collectLatest { (items, updatesCount) ->
                    sourcePreferences.novelExtensionUpdatesCount().set(updatesCount)
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = items,
                            updates = updatesCount,
                        )
                    }
                }
        }

        screenModelScope.launchIO { refresh() }
    }

    fun refresh() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }
            extensionManager.refreshAvailablePlugins()
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
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
