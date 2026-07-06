package tachiyomi.data.anixart

/**
 * Heuristics for guiding source selection during Anixart import.
 * Manga-like catalogue sources often return noisy results for anime titles.
 */
object AnixartSourceHints {

    private val MANGA_LIKE_KEYWORDS = listOf(
        "manga",
        "manhwa",
        "manhua",
        "comic",
        "doujin",
        "hentai",
        "yaoi",
        "yuri",
    )

    private val KNOWN_NOISY_SOURCES = setOf(
        "myreadingmanga",
    )

    enum class Recommendation {
        RECOMMENDED,
        NEUTRAL,
        WARNING,
    }

    fun recommendation(sourceName: String): Recommendation {
        val normalized = sourceName.trim().lowercase()
        if (KNOWN_NOISY_SOURCES.any { normalized.contains(it) }) return Recommendation.WARNING
        if (MANGA_LIKE_KEYWORDS.any { normalized.contains(it) }) return Recommendation.WARNING
        return Recommendation.NEUTRAL
    }

    fun isLikelyNoisySource(sourceName: String): Boolean =
        recommendation(sourceName) == Recommendation.WARNING
}
