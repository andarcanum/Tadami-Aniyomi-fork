package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade
import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import tachiyomi.domain.source.novel.model.StubNovelSource
import eu.kanade.tachiyomi.extension.novel.withNormalizedLang
import java.util.concurrent.ConcurrentHashMap

class DefaultNovelExtensionManager(
    private val repository: NovelPluginRepository,
    private val api: NovelPluginApiFacade,
    private val installer: NovelPluginInstallerFacade,
    private val sourceFactory: NovelPluginSourceFactory,
) : NovelExtensionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val installedSources = MutableStateFlow<List<NovelSource>>(emptyList())
    private val installedPlugins = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())
    private val availablePlugins = MutableStateFlow<List<NovelPlugin.Available>>(emptyList())
    private val updates = MutableStateFlow<List<NovelPlugin.Installed>>(emptyList())
    private val installedPluginIconUrls = ConcurrentHashMap<Long, String?>()

    override val installedSourcesFlow: Flow<List<NovelSource>> = installedSources.asStateFlow()
    override val installedPluginsFlow: Flow<List<NovelPlugin.Installed>> = installedPlugins.asStateFlow()
    override val availablePluginsFlow: Flow<List<NovelPlugin.Available>> = availablePlugins.asStateFlow()
    override val updatesFlow: Flow<List<NovelPlugin.Installed>> = updates.asStateFlow()

    init {
        scope.launch {
            repository.subscribeAll().collect {
                val normalized = it.map { plugin -> plugin.withNormalizedLang() }
                applyInstalledSnapshot(normalized)
            }
        }
    }

    override suspend fun refreshAvailablePlugins() {
        availablePlugins.value = api.fetchAvailablePlugins().map { it.withNormalizedLang() }
        updatePendingUpdates()
    }

    override suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed {
        val installed = installer.install(plugin).withNormalizedLang()
        applyInstalledSnapshot(
            installedPlugins.value
                .filterNot { it.id == installed.id } + installed,
        )
        return installed
    }

    override suspend fun uninstallPlugin(plugin: NovelPlugin.Installed) {
        installer.uninstall(plugin.id)
        applyInstalledSnapshot(installedPlugins.value.filterNot { it.id == plugin.id })
    }

    override suspend fun getSourceData(id: Long): StubNovelSource? {
        return repository.getAll()
            .firstOrNull { plugin -> NovelPluginId.toSourceId(plugin.id) == id }
            ?.let { plugin -> StubNovelSource(id = id, lang = plugin.lang, name = plugin.name) }
    }

    override fun getPluginIconUrlForSource(sourceId: Long): String? {
        return installedPluginIconUrls[sourceId]
    }

    private fun applyInstalledSnapshot(normalized: List<NovelPlugin.Installed>) {
        installedPlugins.value = normalized
        installedSources.value = normalized.mapNotNull { plugin -> sourceFactory.create(plugin) }
        installedPluginIconUrls.clear()
        normalized.forEach { plugin ->
            plugin.iconUrl?.let { iconUrl ->
                installedPluginIconUrls[NovelPluginId.toSourceId(plugin.id)] = iconUrl
            }
        }
        updatePendingUpdates()
    }

    private fun updatePendingUpdates() {
        val availableMap = availablePlugins.value.associateBy { it.id }
        updates.value = installedPlugins.value.filter { installed ->
            val available = availableMap[installed.id] ?: return@filter false
            available.version > installed.version
        }
    }
}
