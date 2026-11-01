package tachiyomi.domain.reels.anime.interactor

import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference

/**
 * One resolved quality decision for an item.
 *
 * [requested] is the effective preference after the manual override and data-saver policy;
 * [variant] is the actually selected rendition. [manual] records that the user explicitly
 * chose this item's quality, and [fallbackToSource] means the exact requested resolution was
 * not available and Source was selected instead (never a nearest resolution).
 */
data class ReelsQualityDecision(
    val requested: ReelsQualityPreference,
    val variant: FeedVideoQualityVariant,
    val manual: Boolean,
    val fallbackToSource: Boolean,
)

/**
 * Pure quality selector: no Android, TDLib or IO dependencies.
 *
 * The catalog is expected to contain exactly one Source variant. Resolution matching uses the
 * shorter edge of the actual dimensions, so 1080x1920 is 1080p. Only exact resolution matches
 * are considered; if none is compatible, Source is the fallback.
 */
object ReelsQualitySelector {

    private const val SOURCE_VALUE = "source"

    fun parseGlobal(value: String?): ReelsQualityPreference =
        when (value?.trim()?.lowercase()) {
            "480" -> ReelsQualityPreference.Resolution(480)
            "720" -> ReelsQualityPreference.Resolution(720)
            "1080" -> ReelsQualityPreference.Resolution(1080)
            else -> ReelsQualityPreference.Source
        }

    fun serializeGlobal(value: ReelsQualityPreference): String =
        when (value) {
            ReelsQualityPreference.Source -> SOURCE_VALUE
            is ReelsQualityPreference.Resolution -> value.shortEdge.toString()
        }

    fun select(
        global: ReelsQualityPreference,
        override: ReelsQualityPreference?,
        forceSd: Boolean,
        variants: List<FeedVideoQualityVariant>,
        compatibleIds: Set<String>,
    ): ReelsQualityDecision {
        val source = variants.single { it.isSource }
        val requested = override ?: if (forceSd) {
            ReelsQualityPreference.Resolution(480)
        } else {
            global
        }
        val selected = when (requested) {
            ReelsQualityPreference.Source -> source
            is ReelsQualityPreference.Resolution ->
                variants
                    .asSequence()
                    .filterNot { it.isSource }
                    .filter { it.id in compatibleIds }
                    .filter { it.shortEdge() == requested.shortEdge }
                    .minWithOrNull(variantOrder)
                    ?: source
        }
        return ReelsQualityDecision(
            requested = requested,
            variant = selected,
            manual = override != null,
            fallbackToSource = requested is ReelsQualityPreference.Resolution && selected.isSource,
        )
    }

    private val variantOrder: Comparator<FeedVideoQualityVariant> =
        compareBy<FeedVideoQualityVariant> { codecRank(it.codec) }
            .thenBy { it.sizeBytes?.takeIf { size -> size > 0 } ?: Long.MAX_VALUE }
            .thenBy { it.id }

    private fun codecRank(codec: String?): Int =
        when (codec?.trim()?.lowercase()) {
            "h264", "avc", "avc1" -> 0
            "h265", "hevc" -> 1
            "av1", "av01" -> 2
            else -> 3
        }

    private fun FeedVideoQualityVariant.shortEdge(): Int? {
        val width = width ?: return null
        val height = height ?: return null
        if (width <= 0 || height <= 0) return null
        return minOf(width, height)
    }
}
