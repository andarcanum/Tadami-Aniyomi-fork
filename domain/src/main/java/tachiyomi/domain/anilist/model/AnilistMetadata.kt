package tachiyomi.domain.anilist.model

/**
 * Metadata from Anilist.co for an anime.
 *
 * Used to display rating, type, status, and cover from Anilist in Aurora anime cards.
 */
data class AnilistMetadata(
    val animeId: Long,
    val anilistId: Long?, // null = not found in Anilist
    val score: Double?, // Rating 0-100 (from averageScore)
    val format: String?, // Type: TV, MOVIE, OVA, SPECIAL, ONA, MUSIC
    val status: String?, // Status: FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED
    val coverUrl: String?, // URL to Anilist poster (coverImage.large)
    val coverUrlFallback: String?, // URL to medium quality poster (fallback if large fails)
    val searchQuery: String, // Query used to search (for debugging)
    val updatedAt: Long, // Timestamp of last update
    val isManualMatch: Boolean = false, // true if user manually selected
) {
    /**
     * Check if cached data is stale (older than 7 days).
     */
    fun isStale(currentTime: Long = System.currentTimeMillis()): Boolean {
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
        return currentTime - updatedAt > sevenDaysInMillis
    }

    /**
     * Check if metadata contains any useful data.
     */
    fun hasData(): Boolean = score != null || format != null || coverUrl != null

    /**
     * Get formatted status string in Russian.
     * Anilist uses different status values than Shikimori.
     */
    fun getFormattedStatus(): String? = when (status?.uppercase()) {
        "FINISHED" -> "Завершён"
        "RELEASING" -> "Онгоинг"
        "NOT_YET_RELEASED" -> "Анонс"
        "CANCELLED" -> "Отменён"
        "HIATUS" -> "На паузе"
        else -> null
    }

    /**
     * Get score formatted for display (0-10 scale).
     * Anilist uses 0-100 scale, convert to 0-10 for consistency.
     */
    fun getDisplayScore(): Double? = score?.let { it / 10.0 }
}
