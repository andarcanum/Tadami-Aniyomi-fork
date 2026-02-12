package eu.kanade.tachiyomi.extension.novel.runtime

class NovelPluginScriptOverridesApplier(
    private val overrides: NovelPluginRuntimeOverrides,
) {
    fun apply(pluginId: String, script: String): String {
        val patches = overrides.forPlugin(pluginId).scriptPatches
        if (patches.isEmpty()) return script

        var result = script
        patches.forEach { patch ->
            if (patch.pattern.isBlank()) return@forEach
            result = if (patch.regex) {
                val options = if (patch.ignoreCase) {
                    setOf(RegexOption.IGNORE_CASE)
                } else {
                    emptySet()
                }
                Regex(patch.pattern, options).replace(result, patch.replacement)
            } else if (patch.ignoreCase) {
                Regex(Regex.escape(patch.pattern), setOf(RegexOption.IGNORE_CASE))
                    .replace(result, patch.replacement)
            } else {
                result.replace(patch.pattern, patch.replacement)
            }
        }
        return result
    }
}

