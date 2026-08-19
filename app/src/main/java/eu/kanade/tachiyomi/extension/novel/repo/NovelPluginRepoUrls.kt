package eu.kanade.tachiyomi.extension.novel.repo

internal fun resolveNovelPluginRepoIndexUrls(baseUrl: String): List<String> {
    val normalized = baseUrl.trim().trimEnd('/')
    if (normalized.isEmpty()) return emptyList()

    return if (
        normalized.endsWith(".json", ignoreCase = true) ||
        normalized.endsWith(".pb", ignoreCase = true)
    ) {
        listOf(normalized)
    } else {
        // LNReader-style repos publish plugins.min.json / plugins.json; Tachiyomi-style repos
        // publish index.min.json / index.json. Try the LNReader names first since this app's
        // novel plugin repos use them, then fall back to the generic ones.
        listOf(
            "$normalized/plugins.min.json",
            "$normalized/plugins.json",
            "$normalized/index.json",
            "$normalized/index.pb",
            "$normalized/index.min.json",
        )
    }
}

internal fun resolveNovelPluginRepoIndexUrl(baseUrl: String): String {
    return resolveNovelPluginRepoIndexUrls(baseUrl).firstOrNull().orEmpty()
}
