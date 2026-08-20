package eu.kanade.tachiyomi.extension.anime.model

/**
 * Single source of truth for choosing how an installed extension is updated
 * when the same package is published by multiple stores (repos), potentially
 * signed with different keys.
 *
 * Used by the extension manager (hasUpdate / needsReinstall flags), the
 * background update checker (badge count and notifications) and the
 * extensions UI (update / reinstall actions), so that all of them always
 * agree on what is actually installable:
 * - A "regular update" is a newer build from the repo the extension was
 *   installed from (same signing key, installs on top).
 * - "Reinstall candidates" are newer builds from other repos (potentially
 *   different signing keys, require uninstall + install).
 */

internal fun selectAnimeInstalledRepoDisplayName(
    extension: AnimeExtension.Installed,
    variants: List<AnimeExtension.Available>,
): String? {
    extension.repoUrl?.let { repoUrl ->
        return extension.repoName?.takeIf { it.isNotBlank() } ?: repoUrl
    }

    val exactVersionMatches = variants.filter {
        it.versionCode == extension.versionCode && it.libVersion == extension.libVersion
    }
    val displayCandidate = exactVersionMatches.singleOrNull()
        ?: variants.singleOrNull()

    return displayCandidate?.repoName?.ifBlank { displayCandidate.repoUrl }
}

internal fun inferAnimeInstalledRepo(
    extension: AnimeExtension.Installed,
    variants: List<AnimeExtension.Available>,
): AnimeExtension.Available? {
    extension.repoUrl?.let { repoUrl ->
        return variants.firstOrNull { it.repoUrl == repoUrl }
    }

    val exactVersionMatches = variants.filter {
        it.versionCode == extension.versionCode && it.libVersion == extension.libVersion
    }

    return exactVersionMatches.singleOrNull()
        ?: variants.singleOrNull()
        ?: variants
            .map { it.repoUrl }
            .distinct()
            .singleOrNull()
            ?.let { repoUrl -> variants.first { it.repoUrl == repoUrl } }
}

internal fun selectAnimeSameRepoUpdate(
    extension: AnimeExtension.Installed,
    variants: List<AnimeExtension.Available>,
): AnimeExtension.Available? {
    val repoUrl = inferAnimeInstalledRepo(extension, variants)?.repoUrl ?: return null
    return variants
        .filter { it.repoUrl == repoUrl && isNewer(extension, it) }
        .latestVersionGroup()
        .firstOrNull()
}

internal fun selectAnimeRegularUpdate(
    extension: AnimeExtension.Installed,
    variants: List<AnimeExtension.Available>,
): AnimeExtension.Available? {
    selectAnimeSameRepoUpdate(extension, variants)?.let { return it }

    if (inferAnimeInstalledRepo(extension, variants) != null) return null

    val latestVersionGroup = variants
        .filter { isNewer(extension, it) }
        .latestVersionGroup()

    if (variants.size == 1) return latestVersionGroup.singleOrNull()
    return latestVersionGroup.takeIf { it.size > 1 }?.firstOrNull()
}

internal fun selectAnimeReinstallCandidates(
    extension: AnimeExtension.Installed,
    variants: List<AnimeExtension.Available>,
): List<AnimeExtension.Available> {
    if (selectAnimeRegularUpdate(extension, variants) != null) return emptyList()

    val installedRepoUrl = inferAnimeInstalledRepo(extension, variants)?.repoUrl

    return variants
        .filter { installedRepoUrl == null || it.repoUrl != installedRepoUrl }
        .filter { isNewer(extension, it) }
        .latestVersionGroup()
}

private fun List<AnimeExtension.Available>.latestVersionGroup(): List<AnimeExtension.Available> {
    val latest = maxWithOrNull(
        compareBy<AnimeExtension.Available> { it.versionCode }
            .thenBy { it.libVersion },
    ) ?: return emptyList()

    return filter { it.versionCode == latest.versionCode && it.libVersion == latest.libVersion }
        .sortedWith(
            compareBy<AnimeExtension.Available> { it.repoName.ifBlank { it.repoUrl } }
                .thenBy { it.repoUrl },
        )
}

private fun isNewer(
    extension: AnimeExtension.Installed,
    candidate: AnimeExtension.Available,
): Boolean {
    return !candidate.needsAppUpdate &&
        (candidate.versionCode > extension.versionCode || candidate.libVersion > extension.libVersion)
}
