package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory
import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.domain.extension.novel.model.NovelPlugin
import java.lang.ref.WeakReference

class NovelJsSourceFactory(
    private val runtimeFactory: NovelJsRuntimeFactory,
    private val pluginStorage: NovelPluginStorage,
    private val json: Json,
    private val runtimeOverrides: NovelPluginRuntimeOverrides,
) : NovelPluginSourceFactory {

    private val scriptBuilder = NovelPluginScriptBuilder()
    private val filterMapper = NovelPluginFilterMapper(json)
    private val scriptOverridesApplier = NovelPluginScriptOverridesApplier(runtimeOverrides)
    private val resultNormalizer = NovelPluginResultNormalizer()
    private val sources = mutableListOf<WeakReference<NovelJsSource>>()

    override fun create(plugin: NovelPlugin.Installed): NovelSource? {
        val scriptBytes = pluginStorage.readPluginScript(plugin.id)
        if (scriptBytes == null) {
            logcat(LogPriority.WARN) { "Novel plugin source missing script id=${plugin.id}" }
            return null
        }
        val runtimeOverride = runtimeOverrides.forPlugin(plugin.id)
        val script = scriptOverridesApplier.apply(
            pluginId = plugin.id,
            script = scriptBytes.toString(Charsets.UTF_8),
        )
        val source = NovelJsSource(
            plugin = plugin,
            script = script,
            runtimeFactory = runtimeFactory,
            json = json,
            scriptBuilder = scriptBuilder,
            filterMapper = filterMapper,
            resultNormalizer = resultNormalizer,
            runtimeOverride = runtimeOverride,
        )
        synchronized(sources) {
            sources.removeAll { it.get() == null }
            sources.add(WeakReference(source))
        }
        return source
    }

    override fun clearRuntimeCaches() {
        synchronized(sources) {
            val iterator = sources.iterator()
            while (iterator.hasNext()) {
                val source = iterator.next().get()
                if (source == null) {
                    iterator.remove()
                } else {
                    source.clearInMemoryCaches()
                }
            }
        }
    }
}
