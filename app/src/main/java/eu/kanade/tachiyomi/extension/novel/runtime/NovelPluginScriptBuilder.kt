package eu.kanade.tachiyomi.extension.novel.runtime

class NovelPluginScriptBuilder {
    fun wrap(script: String, moduleName: String): String {
        return """
            var module = { exports: {} };
            var exports = module.exports;
            $script
            var __pluginExport = exports.default || module.exports.default || module.exports;
            if (!__pluginExport) throw new Error("Plugin $moduleName: no export found");
            if (typeof __pluginExport === "function") {
                __plugin = new __pluginExport();
            } else {
                __plugin = __pluginExport;
            }
        """.trimIndent()
    }
}
