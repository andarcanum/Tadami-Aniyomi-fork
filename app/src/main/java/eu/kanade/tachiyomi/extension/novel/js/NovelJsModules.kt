package eu.kanade.tachiyomi.extension.novel.js

private class NamedJsModule(override val name: String) : JsModule

fun defaultNovelJsModuleRegistry(): JsModuleRegistry {
    val registry = JsModuleRegistry()
    listOf(
        "@libs/fetch",
        "@libs/storage",
        "@libs/filterInputs",
        "@libs/novelStatus",
        "@libs/defaultCover",
    ).forEach { name ->
        registry.register(NamedJsModule(name))
    }
    return registry
}
