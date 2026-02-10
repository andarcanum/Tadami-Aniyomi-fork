package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.extension.novel.NovelExtensionUpdateChecker
import tachiyomi.core.common.util.lang.withIOContext

class NovelPluginRepoUpdateInteractor(
    private val repoService: NovelPluginRepoServiceContract,
    private val storage: NovelPluginStorage,
    private val updateChecker: NovelExtensionUpdateChecker,
) {
    suspend fun findUpdates(repoUrls: List<String>): List<NovelPluginRepoEntry> {
        if (repoUrls.isEmpty()) return emptyList()
        return withIOContext {
            val available = repoUrls
                .groupBy { repoGroupKey(it) }
                .values
                .flatMap { groupedUrls ->
                    groupedUrls
                        .flatMap { repoService.fetch(it) }
                        .groupBy { it.id }
                        .mapNotNull { (_, entries) ->
                            entries.maxByOrNull { it.version }
                        }
                }
            val installed = storage.getAll().map { it.entry }
            updateChecker.findUpdates(installed, available)
        }
    }

    private fun repoGroupKey(repoUrl: String): String {
        val normalized = repoUrl.trim().trimEnd('/')
        val withoutQuery = normalized.substringBefore('?').substringBefore('#')
        return when {
            withoutQuery.endsWith("/index.min.json", ignoreCase = true) ->
                withoutQuery.removeSuffix("/index.min.json")
            withoutQuery.endsWith("/plugins.min.json", ignoreCase = true) ->
                withoutQuery.removeSuffix("/plugins.min.json")
            else -> withoutQuery
        }
    }
}
