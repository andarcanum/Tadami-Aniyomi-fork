package eu.kanade.tachiyomi.extension.novel.js

interface JsModule {
    val name: String
}

class JsModuleRegistry {
    private val modules = mutableMapOf<String, JsModule>()

    fun register(module: JsModule) {
        modules[module.name] = module
    }

    fun get(name: String): JsModule? = modules[name]
}

class NovelJsRuntime(
    private val registry: JsModuleRegistry = defaultNovelJsModuleRegistry(),
) {
    private val contexts = mutableMapOf<String, NovelJsContext>()

    fun getOrCreateContext(pluginId: String): NovelJsContext {
        return contexts.getOrPut(pluginId) { NovelJsContext(pluginId, registry) }
    }
}

class NovelJsContext(
    val pluginId: String,
    val registry: JsModuleRegistry,
) {
    val moduleCache: MutableMap<String, Any> = mutableMapOf()
}
