package eu.kanade.tachiyomi.torrentutils.model

data class TorrentFile(
    val path: String,
    val indexFile: Int,
    val size: Long,
    private val torrentHash: String,
    private val trackers: List<String> = emptyList(),
) {
    fun toMagnetURI(): String {
        val trackerQuery = trackers.joinToString("&tr=") { it.percentEncode() }
        return buildString {
            append("magnet:?xt=urn:btih:")
            append(torrentHash)
            if (trackerQuery.isNotEmpty()) {
                append("&tr=")
                append(trackerQuery)
            }
            append("&index=")
            append(indexFile)
        }
    }

    private fun String.percentEncode(): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return encodeToByteArray().joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            val char = value.toChar()
            if (char in unreserved) {
                char.toString()
            } else {
                "%" + value.toString(16).uppercase().padStart(2, '0')
            }
        }
    }
}
