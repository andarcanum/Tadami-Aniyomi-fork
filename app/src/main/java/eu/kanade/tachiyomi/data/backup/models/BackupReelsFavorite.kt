package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import java.util.Date

@Serializable
data class BackupReelsFavorite(
    @ProtoNumber(1) val videoId: String,
    @ProtoNumber(2) val sourceId: Long,
    @ProtoNumber(3) val title: String? = null,
    @ProtoNumber(4) val author: String? = null,
    @ProtoNumber(5) val videoUrlHd: String,
    @ProtoNumber(6) val videoUrlSd: String? = null,
    @ProtoNumber(7) val posterUrl: String,
    @ProtoNumber(8) val posterUrlVertical: String? = null,
    @ProtoNumber(9) val webUrl: String? = null,
    @ProtoNumber(10) val durationSec: Double? = null,
    @ProtoNumber(11) val hasAudio: Boolean = false,
    @ProtoNumber(12) val addedAt: Long = 0L,
)

fun ReelsFavorite.toBackupReelsFavorite(): BackupReelsFavorite {
    return BackupReelsFavorite(
        videoId = videoId,
        sourceId = sourceId,
        title = title,
        author = author,
        videoUrlHd = videoUrlHd,
        videoUrlSd = videoUrlSd,
        posterUrl = posterUrl,
        posterUrlVertical = posterUrlVertical,
        webUrl = webUrl,
        durationSec = durationSec,
        hasAudio = hasAudio,
        addedAt = addedAt.time,
    )
}

fun BackupReelsFavorite.toReelsFavorite(): ReelsFavorite {
    return ReelsFavorite(
        videoId = videoId,
        sourceId = sourceId,
        title = title,
        author = author,
        videoUrlHd = videoUrlHd,
        videoUrlSd = videoUrlSd,
        posterUrl = posterUrl,
        posterUrlVertical = posterUrlVertical,
        webUrl = webUrl,
        durationSec = durationSec,
        hasAudio = hasAudio,
        addedAt = Date(addedAt),
    )
}
