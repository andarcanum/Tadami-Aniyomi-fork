package eu.kanade.tachiyomi.ui.browse.novel.extension

import tachiyomi.domain.extension.novel.model.NovelPlugin

data class NovelPluginUpdateState(
    val sameRepoUpdate: NovelPlugin.Available?,
    val otherRepoUpdates: List<NovelPlugin.Available>,
) {
    val hasSameRepoUpdate: Boolean = sameRepoUpdate != null
    val hasOtherRepoUpdate: Boolean = otherRepoUpdates.isNotEmpty()
    val hasAnyUpdate: Boolean = hasSameRepoUpdate || hasOtherRepoUpdate
}

/**
 * Single source of truth for how an installed novel plugin can be updated when the same plugin is
 * published by multiple repos, potentially signed with different keys — the novel counterpart of
 * the manga/anime `*ExtensionUpdateResolver`:
 * - A "regular update" is a newer build from the repo the plugin was installed from (same signing
 *   key, installs on top).
 * - "Reinstall candidates" are newer builds from other repos (potentially different signing keys,
 *   require uninstall + install). They only exist when no regular update does, and only in the
 *   newest version group — offering older cross-repo builds would invite needless downgrades.
 */
internal object NovelPluginUpdateClassifier {
    fun classify(
        installed: NovelPlugin.Installed,
        variants: List<NovelPlugin.Available>,
    ): NovelPluginUpdateState {
        val installedRepoUrl = installed.repoUrl.takeIf { it.isNotBlank() }
            ?: inferInstalledRepoUrl(installed, variants)
        val newerVariants = variants.filter { it.versionCode > installed.versionCode }
        val sameRepoUpdate = installedRepoUrl?.let { repoUrl ->
            newerVariants
                .filter { it.repoUrl == repoUrl }
                .maxByOrNull { it.versionCode }
        }
        val otherRepoUpdates = if (sameRepoUpdate != null) {
            emptyList()
        } else {
            newerVariants
                .filter { installedRepoUrl == null || it.repoUrl != installedRepoUrl }
                .latestVersionGroup()
        }

        return NovelPluginUpdateState(
            sameRepoUpdate = sameRepoUpdate,
            otherRepoUpdates = otherRepoUpdates,
        )
    }

    private fun List<NovelPlugin.Available>.latestVersionGroup(): List<NovelPlugin.Available> {
        val latest = maxOfOrNull { it.versionCode } ?: return emptyList()
        return filter { it.versionCode == latest }
            .sortedWith(
                compareBy<NovelPlugin.Available> { it.repoName.ifBlank { it.repoUrl } }
                    .thenBy { it.repoUrl },
            )
    }

    private fun inferInstalledRepoUrl(
        installed: NovelPlugin.Installed,
        variants: List<NovelPlugin.Available>,
    ): String? {
        val exactVersionMatches = variants.filter { it.versionCode == installed.versionCode }

        return exactVersionMatches.singleOrNull()?.repoUrl
            ?: variants.singleOrNull()?.repoUrl
            ?: variants
                .map { it.repoUrl }
                .distinct()
                .singleOrNull()
    }
}
