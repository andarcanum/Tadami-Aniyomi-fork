package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory
import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelJsSourceFactory(
    private val runtimeFactory: NovelJsRuntimeFactory,
    private val pluginStorage: NovelPluginStorage,
    private val json: Json,
) : NovelPluginSourceFactory {

    private val scriptBuilder = NovelPluginScriptBuilder()
    private val filterMapper = NovelPluginFilterMapper(json)

    override fun create(plugin: NovelPlugin.Installed): NovelSource? {
        val scriptBytes = pluginStorage.readPluginScript(plugin.id)
        if (scriptBytes == null) {
            logcat(LogPriority.WARN) { "Novel plugin source missing script id=${plugin.id}" }
            return null
        }
        val script = scriptBytes.toString(Charsets.UTF_8)
        return NovelJsSource(
            plugin = plugin,
            script = script,
            runtimeFactory = runtimeFactory,
            json = json,
            scriptBuilder = scriptBuilder,
            filterMapper = filterMapper,
        )
    }
}
