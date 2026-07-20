package tachiyomi.data.release

import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.net.URLEncoder

class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? {
        val release = requestGithubRelease(
            "https://api.github.com/repos/${arguments.repository}/releases/latest",
        )
        val downloadLink = getDownloadLink(release = release) ?: return null

        return release.toRelease(downloadLink)
    }

    override suspend fun byTag(repository: String, versionTag: String): Release? {
        val release = try {
            val encodedTag = URLEncoder.encode(versionTag, Charsets.UTF_8.name())
            requestGithubRelease(
                "https://api.github.com/repos/$repository/releases/tags/$encodedTag",
            )
        } catch (e: Exception) {
            val fallbackTag = if (versionTag.startsWith("v")) {
                versionTag.removePrefix("v")
            } else {
                "v$versionTag"
            }
            val encodedFallbackTag = URLEncoder.encode(fallbackTag, Charsets.UTF_8.name())
            requestGithubRelease(
                "https://api.github.com/repos/$repository/releases/tags/$encodedFallbackTag",
            )
        }
        val downloadLink = getDownloadLink(release = release) ?: release.assets.firstOrNull()?.downloadLink.orEmpty()

        return release.toRelease(downloadLink)
    }

    private suspend fun requestGithubRelease(url: String): GithubRelease {
        return with(json) {
            networkService.client
                .newCall(GET(url))
                .awaitSuccess()
                .parseAs<GithubRelease>()
        }
    }

    private fun getDownloadLink(release: GithubRelease): String? {
        val map = release.assets.associate { asset ->
            BUILD_TYPES.find { "-$it" in asset.name } to asset.downloadLink
        }

        return map[Build.SUPPORTED_ABIS[0]] ?: map[null]
    }

    companion object {
        private val BUILD_TYPES = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
}

internal fun GithubRelease.toRelease(downloadLink: String): Release {
    return Release(
        version = version,
        info = info.replace(gitHubUsernameMentionRegex) { mention ->
            "[${mention.value}](https://github.com/${mention.value.substring(1)})"
        },
        releaseLink = releaseLink,
        downloadLink = downloadLink,
        releaseDate = publishedAt.substringBefore('T'),
    )
}

/**
 * Regular expression that matches a mention to a valid GitHub username, like it's
 * done in GitHub Flavored Markdown. It follows these constraints:
 *
 * - Alphanumeric with single hyphens (no consecutive hyphens)
 * - Cannot begin or end with a hyphen
 * - Max length of 39 characters
 *
 * Reference: https://stackoverflow.com/a/30281147
 */
private val gitHubUsernameMentionRegex = """\B@([a-z0-9](?:-(?=[a-z0-9])|[a-z0-9]){0,38}(?<=[a-z0-9]))"""
    .toRegex(RegexOption.IGNORE_CASE)
