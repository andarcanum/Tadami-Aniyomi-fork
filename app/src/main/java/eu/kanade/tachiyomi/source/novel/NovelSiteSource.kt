package eu.kanade.tachiyomi.source.novel

/**
 * Optional capability for novel sources that can provide a website base URL.
 */
interface NovelSiteSource {
    val siteUrl: String?
}
