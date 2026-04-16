package eu.kanade.presentation.entries.components

import eu.kanade.domain.metadata.model.MetadataLoadError
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataSource
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
 * Returns display status for external metadata.
 * TODO: This currently returns hardcoded Russian strings.
 * Call sites should use stringResource() with the returned key:
 * - "Завершён" → MR.strings.status_finished
 * - "Онгоинг" → MR.strings.status_releasing
 * - "Анонс" → MR.strings.status_not_yet_released
 * - "Отменён" → MR.strings.status_cancelled
 * - "На паузе" → MR.strings.status_hiatus
 * - "Брошен" → MR.strings.status_discontinued
 */
internal fun ExternalMetadata.displayStatus(): String? {
    val rawStatus = status?.trim().orEmpty()
    if (rawStatus.isEmpty()) return null

    return when (source) {
        MetadataSource.ANILIST -> when (rawStatus.uppercase()) {
            "FINISHED" -> "Завершён"
            "RELEASING" -> "Онгоинг"
            "NOT_YET_RELEASED" -> "Анонс"
            "CANCELLED" -> "Отменён"
            "HIATUS" -> "На паузе"
            else -> rawStatus
        }
        MetadataSource.SHIKIMORI -> when (rawStatus.lowercase()) {
            "anons" -> "Анонс"
            "ongoing" -> "Онгоинг"
            "released" -> "Завершён"
            "discontinued" -> "Брошен"
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
    if (!useMetadataCovers || isMetadataLoading) {
        return ResolvedCover(baseCoverUrl, null)
    }

    val metadataCoverUrl = metadata?.coverUrl?.takeIf { it.isNotBlank() }
    val metadataCoverUrlFallback = metadata?.coverUrlFallback?.takeIf { it.isNotBlank() }
    return if (metadataCoverUrl != null) {
        ResolvedCover(metadataCoverUrl, metadataCoverUrlFallback ?: baseCoverUrl)
    } else {
        ResolvedCover(baseCoverUrl, null)
    }
}
