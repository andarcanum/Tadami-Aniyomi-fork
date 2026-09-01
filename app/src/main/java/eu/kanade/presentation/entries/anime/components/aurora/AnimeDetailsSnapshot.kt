package eu.kanade.presentation.entries.anime.components.aurora

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import eu.kanade.domain.metadata.model.MetadataLoadError
import eu.kanade.presentation.entries.components.displayFormat
import eu.kanade.presentation.entries.components.displayStatus
import eu.kanade.presentation.entries.components.isCompleted
import eu.kanade.tachiyomi.animesource.model.SAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.i18n.MR
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

@Immutable
data class AnimeProgressSnapshot(
    val watchedCount: Int,
    val totalEpisodes: Int,
    val percent: Float?,
) {
    val progressText: String
        get() {
            val percentText = percent?.let { "${(it * 100f).toInt().coerceIn(0, 100)}%" }
            return buildString {
                append(watchedCount.coerceAtLeast(0))
                append('/')
                append(totalEpisodes.coerceAtLeast(0))
                if (percentText != null) {
                    append(" (")
                    append(percentText)
                    append(')')
                }
            }
        }

    /** Fully watched: every episode of a non-empty list is seen (live stamp predicate). */
    val isCompleted: Boolean
        get() = totalEpisodes > 0 && watchedCount >= totalEpisodes
}

@Immutable
data class AnimeDetailsSnapshot(
    val sourceText: String,
    val dubbingText: String?,
    val ratingValue: Float?,
    val ratingText: String,
    val formatText: String?,
    val statusText: String,
    val statusBadgeText: String,
    val progress: AnimeProgressSnapshot?,
    val episodesText: String,
    val updateText: String,
    val isCompleted: Boolean,
)

fun resolveAnimeDetailsSnapshot(
    anime: Anime,
    watchedCount: Int,
    totalEpisodes: Int,
    sourceName: String,
    selectedDubbing: String?,
    nextUpdate: Instant?,
    sourceRating: Float?,
    animeMetadata: ExternalMetadata?,
    isMetadataLoading: Boolean,
    metadataError: MetadataLoadError?,
    context: Context? = null,
): AnimeDetailsSnapshot {
    val ratingValue = resolveAnimeRatingValue(
        anime = anime,
        sourceRating = sourceRating,
        animeMetadata = animeMetadata,
        metadataError = metadataError,
    )
    val ratingText = when {
        sourceRating != null -> String.format(Locale.US, "%.1f", sourceRating)
        isMetadataLoading -> "..."
        metadataError == MetadataLoadError.NotAuthenticated -> "N/D"
        else -> ratingValue?.let { String.format(Locale.US, "%.1f", it) } ?: "N/D"
    }
    val formatText = when {
        isMetadataLoading -> "..."
        metadataError == MetadataLoadError.NotAuthenticated -> null
        else -> animeMetadata?.displayFormat()
    }
    val statusText = when {
        isMetadataLoading -> "..."
        metadataError == MetadataLoadError.NotAuthenticated ->
            context?.let { AnimeStatusFormatter.formatStatus(it, anime.displayStatus) }
                ?: AnimeStatusFormatter.formatStatus(anime.displayStatus)
        else -> context?.let { ctx ->
            animeMetadata?.displayStatus(ctx) ?: AnimeStatusFormatter.formatStatus(ctx, anime.displayStatus)
        } ?: (animeMetadata?.displayStatus() ?: AnimeStatusFormatter.formatStatus(anime.displayStatus))
    }
    val statusBadgeText = when {
        isMetadataLoading -> "..."
        else -> listOfNotNull(
            formatText?.takeIf { it.isNotBlank() && it != "..." },
            statusText.takeIf { it.isNotBlank() && it != "..." },
        ).joinToString(" | ").ifBlank { statusText }
    }
    val progress = resolveAnimeProgressSnapshot(
        watchedCount = watchedCount,
        totalEpisodes = totalEpisodes,
    )
    val updateText = resolveAnimeUpdateText(
        nextUpdate = nextUpdate,
        isCompleted = animeMetadata?.isCompleted() == true || anime.displayStatus.isAnimeCompleted(),
    )

    return AnimeDetailsSnapshot(
        sourceText = sourceName.trim().ifBlank { "N/D" },
        dubbingText = selectedDubbing.trimOrNull(),
        ratingValue = ratingValue,
        ratingText = ratingText,
        formatText = formatText,
        statusText = statusText,
        statusBadgeText = statusBadgeText,
        progress = progress,
        episodesText = totalEpisodes.toString(),
        updateText = updateText,
        isCompleted = animeMetadata?.isCompleted() == true || anime.displayStatus.isAnimeCompleted(),
    )
}

private fun resolveAnimeRatingValue(
    anime: Anime,
    sourceRating: Float?,
    animeMetadata: ExternalMetadata?,
    metadataError: MetadataLoadError?,
): Float? {
    sourceRating
        ?.takeIf { it >= 0.0 }
        ?.also {
            debugLog(
                "resolveAnimeRatingValue: sourceRating=${it.previewFloat()} title=${anime.title}",
            )
        }
        ?.let { return it }

    if (metadataError == MetadataLoadError.NotAuthenticated) {
        debugLog(
            "resolveAnimeRatingValue: blocked error=$metadataError title=${anime.title}",
        )
        return null
    }

    return animeMetadata?.score
        ?.takeIf { it >= 0.0 }
        ?.toFloat()
        ?.also {
            debugLog(
                "resolveAnimeRatingValue: score=${it.previewFloat()} title=${anime.title}",
            )
        }
}

private fun resolveAnimeProgressSnapshot(
    watchedCount: Int,
    totalEpisodes: Int,
): AnimeProgressSnapshot? {
    if (totalEpisodes <= 0) return null

    val clampedWatchedCount = watchedCount.coerceAtLeast(0).coerceAtMost(totalEpisodes)
    val percent = clampedWatchedCount.toFloat() / totalEpisodes.toFloat()

    return AnimeProgressSnapshot(
        watchedCount = clampedWatchedCount,
        totalEpisodes = totalEpisodes,
        percent = percent,
    )
}

private fun resolveAnimeUpdateText(
    nextUpdate: Instant?,
    isCompleted: Boolean,
): String {
    if (isCompleted) return "N/D"

    val days = nextUpdate?.let {
        Instant.now().until(it, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
    } ?: return "N/D"

    return when (days) {
        0 -> "0d"
        else -> "${days}d"
    }
}

private fun String?.trimOrNull(): String? {
    return this?.trim()?.takeIf { it.isNotBlank() }
}

private fun Long.isAnimeCompleted(): Boolean {
    return when (this.toInt()) {
        SAnime.COMPLETED,
        SAnime.PUBLISHING_FINISHED,
        SAnime.CANCELLED,
        -> true

        else -> false
    }
}

private fun Float.previewFloat(): String = String.format(Locale.US, "%.3f", this)

private fun debugLog(message: String) {
    runCatching { Log.d("AnimeDetailsSnapshot", message) }
}

object AnimeStatusFormatter {

    /**
     * Converts anime status code to readable localized text.
     */
    fun formatStatus(context: Context, status: Long): String {
        return when (status.toInt()) {
            SAnime.ONGOING -> MR.strings.ongoing.getString(context)
            SAnime.COMPLETED -> MR.strings.completed.getString(context)
            SAnime.LICENSED -> MR.strings.licensed.getString(context)
            SAnime.PUBLISHING_FINISHED -> MR.strings.publishing_finished.getString(context)
            SAnime.CANCELLED -> MR.strings.cancelled.getString(context)
            SAnime.ON_HIATUS -> MR.strings.on_hiatus.getString(context)
            else -> MR.strings.unknown.getString(context)
        }
    }

    fun formatStatus(status: Long): String {
        return when (status.toInt()) {
            SAnime.ONGOING -> "ongoing"
            SAnime.COMPLETED -> "completed"
            SAnime.LICENSED -> "licensed"
            SAnime.PUBLISHING_FINISHED -> "publishing finished"
            SAnime.CANCELLED -> "cancelled"
            SAnime.ON_HIATUS -> "on hiatus"
            else -> "unknown"
        }
    }
}
