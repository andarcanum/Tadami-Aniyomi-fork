package eu.kanade.tachiyomi.extension.novel.runtime

class NovelPluginScriptBuilder {
    fun wrap(script: String, moduleName: String): String {
        return """
            __defineModule("$moduleName", function(module, exports) {
            $script
            });
        """.trimIndent()
    }
}
