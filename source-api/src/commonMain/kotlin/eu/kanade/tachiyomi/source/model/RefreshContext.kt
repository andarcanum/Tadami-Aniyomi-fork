package eu.kanade.tachiyomi.source.model

/**
 * Compatibility placeholder for extensions built against newer source-api versions.
 */
data class RefreshContext(
    val chapters: List<SChapter> = emptyList(),
)
