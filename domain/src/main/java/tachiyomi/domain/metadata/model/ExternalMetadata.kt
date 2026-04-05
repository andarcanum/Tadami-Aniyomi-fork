package tachiyomi.domain.metadata.model

data class ExternalMetadata(
    val contentType: MetadataContentType,
    val source: MetadataSource,
    val mediaId: Long,
    val remoteId: Long?,
    val score: Double?,
    val format: String?,
    val status: String?,
    val coverUrl: String?,
    val coverUrlFallback: String?,
    val searchQuery: String,
    val updatedAt: Long,
    val isManualMatch: Boolean = false,
) {
    fun isStale(currentTime: Long = System.currentTimeMillis()): Boolean {
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
        return currentTime - updatedAt > sevenDaysInMillis
    }

    fun hasData(): Boolean = score != null || format != null || coverUrl != null
}
