package eu.kanade.tachiyomi.data.discord

data class DiscordPresenceInfo(
    val mediaKind: MediaKind,
    val title: String,
    val primaryNumber: Double?,
    val secondaryLine: String?,
    val startedAt: Long,
) {
    enum class MediaKind { ANIME, MANGA, NOVEL }
}
