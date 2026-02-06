package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoEntry

class NovelExtensionUpdateChecker {
    fun findUpdates(
        installed: List<NovelPluginRepoEntry>,
        available: List<NovelPluginRepoEntry>,
    ): List<NovelPluginRepoEntry> {
        val installedById = installed.associateBy { it.id }
        return available.filter { entry ->
            val current = installedById[entry.id] ?: return@filter false
            entry.version > current.version
        }
    }
}
