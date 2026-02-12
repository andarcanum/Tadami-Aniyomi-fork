package eu.kanade.tachiyomi.extension.novel.runtime

class NovelDomainAliasResolver(
    private val overrides: NovelPluginRuntimeOverrides,
) {
    fun resolve(pluginId: String, url: String): String {
        val input = url.trim()
        if (input.isEmpty()) return url

        val mapping = overrides.forPlugin(pluginId).domainAliases
        if (mapping.isEmpty()) return input

        val normalized = mapping.entries
            .map { normalizePrefix(it.key) to normalizePrefix(it.value) }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
            .sortedByDescending { it.first.length }

        for ((from, to) in normalized) {
            if (input.startsWith(from, ignoreCase = true)) {
                val suffix = input.substring(from.length)
                val target = to.removeSuffix("/")
                return "$target$suffix"
            }
        }

        return input
    }

    private fun normalizePrefix(value: String): String {
        return value.trim().removeSuffix("/")
    }
}

