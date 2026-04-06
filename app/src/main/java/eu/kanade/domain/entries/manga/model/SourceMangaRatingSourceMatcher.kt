package eu.kanade.domain.entries.manga.model

import java.util.Locale

internal object SourceMangaRatingSourceMatcher {
    private val groupLeSourceNames = setOf(
        "readmanga",
        "mintmanga",
        "seimanga",
        "selfmanga",
        "usagi",
        "allhentai",
        "rumix",
    )
    private val inkStoryHosts = setOf(
        "inkstory.net",
        "api.inkstory.net",
    )

    fun resolveFamily(
        sourceName: String?,
        sourceBaseUrl: String?,
        sourceClassName: String? = null,
    ): SourceMangaRatingFamily? {
        if (isGroupLeSource(sourceName)) {
            return SourceMangaRatingFamily.GROUP_LE
        }
        if (isInkStorySource(sourceName, sourceBaseUrl)) {
            return SourceMangaRatingFamily.INK_STORY
        }
        if (isMadaraSource(sourceClassName)) {
            return SourceMangaRatingFamily.MADARA
        }
        return null
    }

    fun isGroupLeSource(sourceName: String?): Boolean {
        val normalizedSourceName = sourceName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return normalizedSourceName in groupLeSourceNames
    }

    fun isInkStorySource(sourceName: String?, sourceBaseUrl: String?): Boolean {
        val normalizedSourceName = sourceName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalizedSourceName == "inkstory") return true

        val normalizedBaseUrl = sourceBaseUrl?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return inkStoryHosts.any { host -> normalizedBaseUrl.contains(host) }
    }

    fun isMadaraSource(sourceClassName: String?): Boolean {
        return sourceClassName
            ?.trim()
            ?.equals("Madara", ignoreCase = true)
            ?: false
    }
}
