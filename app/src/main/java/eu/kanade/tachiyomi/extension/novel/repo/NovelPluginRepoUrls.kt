package eu.kanade.tachiyomi.extension.novel.repo

internal fun resolveNovelPluginRepoIndexUrls(baseUrl: String): List<String> {
    val normalized = baseUrl.trim().trimEnd('/')
    if (normalized.isEmpty()) return emptyList()

    return if (normalized.endsWith(".json", ignoreCase = true)) {
        listOf(normalized)
    } else {
        listOf(
            "$normalized/index.min.json",
            "$normalized/plugins.min.json",
        )
    }
}

internal fun resolveNovelPluginRepoIndexUrl(baseUrl: String): String {
    return resolveNovelPluginRepoIndexUrls(baseUrl).firstOrNull().orEmpty()
}
