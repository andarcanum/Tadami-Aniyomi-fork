package tachiyomi.domain.shikimori.model

/**
 * Metadata from Shikimori.one for an anime.
 *
 * Used to display rating, type, and cover from Shikimori in Aurora anime cards.
 */
data class ShikimoriMetadata(
    val animeId: Long,
    val shikimoriId: Long?,       // null = not found in Shikimori
    val score: Double?,           // Rating (e.g., 8.5)
    val kind: String?,            // Type: tv, movie, ova, special, ona, music
    val coverUrl: String?,        // URL to Shikimori poster
    val searchQuery: String,      // Query used to search (for debugging)
    val updatedAt: Long,          // Timestamp of last update
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
    fun hasData(): Boolean = score != null || kind != null || coverUrl != null
}
