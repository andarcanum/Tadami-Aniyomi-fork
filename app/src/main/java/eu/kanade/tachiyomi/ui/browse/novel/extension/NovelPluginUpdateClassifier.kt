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
        val otherRepoUpdates = newerVariants
            .filter { installedRepoUrl == null || it.repoUrl != installedRepoUrl }
            .sortedWith(
                compareByDescending<NovelPlugin.Available> { it.versionCode }
                    .thenBy { it.repoName.ifBlank { it.repoUrl } },
            )

        return NovelPluginUpdateState(
            sameRepoUpdate = sameRepoUpdate,
            otherRepoUpdates = otherRepoUpdates,
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
