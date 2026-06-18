package eu.kanade.presentation.entries.components

import android.content.Context
import eu.kanade.domain.metadata.model.MetadataLoadError
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.i18n.MR
import java.util.Locale

internal data class ResolvedCover(
    val coverUrl: String,
    val coverUrlFallback: String?,
)

internal fun ExternalMetadata.displayScore(): String? {
    return score?.let { String.format(Locale.US, "%.1f", it) }
}

internal fun ExternalMetadata.displayFormat(): String? {
    return format?.uppercase()
}

/**
 * Returns display status for external metadata, localized.
 */
internal fun ExternalMetadata.displayStatus(context: Context): String? {
    val rawStatus = status?.trim().orEmpty()
    if (rawStatus.isEmpty()) return null

    return when (source) {
        MetadataSource.ANILIST -> when (rawStatus.uppercase()) {
            "FINISHED" -> MR.strings.status_finished.getString(context)
            "RELEASING" -> MR.strings.status_releasing.getString(context)
            "NOT_YET_RELEASED" -> MR.strings.status_not_yet_released.getString(context)
            "CANCELLED" -> MR.strings.status_cancelled.getString(context)
            "HIATUS" -> MR.strings.status_hiatus.getString(context)
            else -> rawStatus
        }
        MetadataSource.SHIKIMORI -> when (rawStatus.lowercase()) {
            "anons" -> MR.strings.status_not_yet_released.getString(context)
            "ongoing" -> MR.strings.status_releasing.getString(context)
            "released" -> MR.strings.status_finished.getString(context)
            "discontinued" -> MR.strings.status_discontinued.getString(context)
            else -> rawStatus
        }
        MetadataSource.NONE -> rawStatus
    }
}

/**
 * Returns display status for external metadata, untranslated (English fallback).
 */
internal fun ExternalMetadata.displayStatus(): String? {
    val rawStatus = status?.trim().orEmpty()
    if (rawStatus.isEmpty()) return null

    return when (source) {
        MetadataSource.ANILIST -> when (rawStatus.uppercase()) {
            "FINISHED" -> "Finished"
            "RELEASING" -> "Releasing"
            "NOT_YET_RELEASED" -> "Announced"
            "CANCELLED" -> "Cancelled"
            "HIATUS" -> "On hiatus"
            else -> rawStatus
        }
        MetadataSource.SHIKIMORI -> when (rawStatus.lowercase()) {
            "anons" -> "Announced"
            "ongoing" -> "Releasing"
            "released" -> "Finished"
            "discontinued" -> "Discontinued"
            else -> rawStatus
        }
        MetadataSource.NONE -> rawStatus
    }
}

internal fun ExternalMetadata.isCompleted(): Boolean {
    return when (source) {
        MetadataSource.ANILIST -> status?.equals("FINISHED", ignoreCase = true) == true ||
            status?.equals("CANCELLED", ignoreCase = true) == true
        MetadataSource.SHIKIMORI -> status?.equals("released", ignoreCase = true) == true ||
            status?.equals("discontinued", ignoreCase = true) == true
        MetadataSource.NONE -> false
    }
}

internal fun resolveExternalMetadataCover(
    baseCoverUrl: String,
    metadata: ExternalMetadata?,
    isMetadataLoading: Boolean,
    metadataError: MetadataLoadError?,
    useMetadataCovers: Boolean,
): ResolvedCover {
    if (!useMetadataCovers) {
        val resolved = ResolvedCover(baseCoverUrl, null)
        debugTitleCoverFlow(
            scope = "metadata-cover",
            message = "skip useMetadataCovers=$useMetadataCovers loading=$isMetadataLoading base=${previewTitleCoverUrl(
                baseCoverUrl,
            )} result=${previewTitleCoverUrl(resolved.coverUrl)}",
        )
        return resolved
    }

    val metadataCoverUrl = metadata?.coverUrl?.takeIf { it.isNotBlank() }
    val metadataCoverUrlFallback = metadata?.coverUrlFallback?.takeIf { it.isNotBlank() }
    val resolved = if (metadataCoverUrl != null) {
        ResolvedCover(metadataCoverUrl, metadataCoverUrlFallback ?: baseCoverUrl)
    } else {
        ResolvedCover(baseCoverUrl, null)
    }
    debugTitleCoverFlow(
        scope = "metadata-cover",
        message = "resolved metadata=${previewTitleCoverUrl(
            metadataCoverUrl,
        )} fallback=${previewTitleCoverUrl(
            metadataCoverUrlFallback,
        )} base=${previewTitleCoverUrl(
            baseCoverUrl,
        )} error=${metadataError?.javaClass?.simpleName ?: "none"} result=${previewTitleCoverUrl(
            resolved.coverUrl,
        )} resultFallback=${previewTitleCoverUrl(resolved.coverUrlFallback)}",
    )
    return resolved
}
