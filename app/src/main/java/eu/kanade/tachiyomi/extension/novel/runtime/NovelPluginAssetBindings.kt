package eu.kanade.tachiyomi.extension.novel.runtime

import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.extension.novel.NovelPluginStorage

/**
 * Binds plugin custom assets (JS/CSS) to plugin-owned surfaces only.
 * Assets are NOT injected into the general reader pipeline by default.
 */
class NovelPluginAssetBindings(
    private val pluginStorage: NovelPluginStorage,
) {
    private val assetCache = mutableMapOf<String, PluginAssets>()

    fun getCustomJs(pluginId: String): String? {
        return getOrLoadAssets(pluginId).customJs
    }

    fun getCustomCss(pluginId: String): String? {
        return getOrLoadAssets(pluginId).customCss
    }

    fun generateAssetInjectionScript(pluginId: String): String {
        val assets = getOrLoadAssets(pluginId)
        val parts = mutableListOf<String>()

        assets.customJs?.let { js ->
            parts.add(
                """
                // Plugin custom JS
                (function() {
                    try {
                        $js
                    } catch(e) {
                        console.error('Plugin custom JS error:', e);
                    }
                })();
                """.trimIndent(),
            )
        }

        assets.customCss?.let { css ->
            parts.add(
                """
                // Plugin custom CSS
                (function() {
                    try {
                        var style = document.createElement('style');
                        style.textContent = ${escapeJsString(css)};
                        document.head.appendChild(style);
                    } catch(e) {
                        console.error('Plugin custom CSS error:', e);
                    }
                })();
                """.trimIndent(),
            )
        }

        return if (parts.isEmpty()) {
            ""
        } else {
            parts.joinToString("\n\n")
        }
    }

    fun hasCustomAssets(pluginId: String): Boolean {
        val assets = getOrLoadAssets(pluginId)
        return assets.customJs != null || assets.customCss != null
    }

    fun clearCache(pluginId: String) {
        assetCache.remove(pluginId)
    }

    fun clearAllCaches() {
        assetCache.clear()
    }

    private fun getOrLoadAssets(pluginId: String): PluginAssets {
        return assetCache.getOrPut(pluginId) {
            loadAssets(pluginId)
        }
    }

    private fun loadAssets(pluginId: String): PluginAssets {
        val customJs = pluginStorage.readCustomJs(pluginId)?.let { bytes ->
            val content = bytes.toString(Charsets.UTF_8)
            logcat(LogPriority.DEBUG) {
                "Loaded plugin custom JS id=$pluginId bytes=${bytes.size}"
            }
            content
        }

        val customCss = pluginStorage.readCustomCss(pluginId)?.let { bytes ->
            val content = bytes.toString(Charsets.UTF_8)
            logcat(LogPriority.DEBUG) {
                "Loaded plugin custom CSS id=$pluginId bytes=${bytes.size}"
            }
            content
        }

        return PluginAssets(
            customJs = customJs,
            customCss = customCss,
        )
    }

    private fun escapeJsString(value: String): String {
        return "\"${
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }\""
    }

    private data class PluginAssets(
        val customJs: String?,
        val customCss: String?,
    )
}
