package eu.kanade.tachiyomi.ui.reels

import android.media.MediaCodecList
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant

/**
 * Device codec compatibility for progressive variants. Kept in :app because it touches
 * MediaCodecList; the domain selector only receives the resulting compatible-id set.
 */
object ReelsQualityCodecSupport {

    private val regularCodecInfos by lazy {
        runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }
            .getOrDefault(emptyArray())
    }

    fun isSupported(variant: FeedVideoQualityVariant): Boolean {
        val mime = when (variant.codec?.trim()?.lowercase()) {
            "h264", "avc", "avc1" -> "video/avc"
            "h265", "hevc" -> "video/hevc"
            "av1", "av01" -> "video/av01"
            else -> return false
        }
        return regularCodecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }
}
