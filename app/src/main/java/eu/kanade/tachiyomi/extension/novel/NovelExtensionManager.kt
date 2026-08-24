package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginCapabilities
import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.novel.model.StubNovelSource

interface NovelExtensionManager {
    val installedSourcesFlow: Flow<List<NovelSource>>
    val installedPluginsFlow: Flow<List<NovelPlugin.Installed>>
    val availablePluginsFlow: Flow<List<NovelPlugin.Available>>
    val untrustedPluginsFlow: Flow<List<NovelPlugin.Untrusted>>
    val updatesFlow: Flow<List<NovelPlugin.Installed>>

    /** Repo indexes that failed to load during the last refresh (baseUrl to error). */
    val repoFetchErrors: Flow<Map<String, String>>

    /**
     * Install attempts that failed because the APK is signed with a different key than the
     * installed copy. The UI subscribes to offer reinstall-with-uninstall.
     */
    data class SignatureMismatchEvent(
        val pluginId: String,
        val displayName: String,
        val candidate: NovelPlugin.Available? = null,
    )

    val signatureMismatchEvents: SharedFlow<SignatureMismatchEvent>

    fun reportSignatureMismatch(pluginId: String)

    suspend fun refreshAvailablePlugins()

    suspend fun installPlugin(plugin: NovelPlugin.Available): NovelPlugin.Installed

    /** Best-effort cancel of an in-flight install of [plugin]. */
    fun cancelPluginInstall(plugin: NovelPlugin.Available) {}

    suspend fun uninstallPlugin(plugin: NovelPlugin.Installed)

    suspend fun uninstallPlugin(plugin: NovelPlugin.Untrusted)

    suspend fun replacePluginFromRepo(
        installed: NovelPlugin.Installed,
        replacement: NovelPlugin.Available,
    ): NovelPlugin.Installed

    suspend fun trustPlugin(plugin: NovelPlugin.Untrusted)

    suspend fun getSourceData(id: Long): StubNovelSource?

    fun getPluginIconUrlForSource(sourceId: Long): String?

    fun getCapabilitiesForSource(sourceId: Long): NovelPluginCapabilities?

    fun getPluginId(sourceId: Long): String?

    fun getPluginIdAsFlow(sourceId: Long): Flow<String?>

    fun isNsfwForSource(sourceId: Long): Boolean

    fun isNsfwForSourceAsFlow(sourceId: Long): Flow<Boolean>
}
