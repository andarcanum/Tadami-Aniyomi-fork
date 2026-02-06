package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.novel.model.StubNovelSource

interface NovelExtensionManager {
    val installedSourcesFlow: Flow<List<NovelSource>>
    val installedPluginsFlow: Flow<List<NovelPlugin.Installed>>
    val availablePluginsFlow: Flow<List<NovelPlugin.Available>>
    val updatesFlow: Flow<List<NovelPlugin.Installed>>

    suspend fun refreshAvailablePlugins()

    suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed

    suspend fun uninstallPlugin(plugin: NovelPlugin.Installed)

    suspend fun getSourceData(id: Long): StubNovelSource?

    fun getPluginIconUrlForSource(sourceId: Long): String?
}
