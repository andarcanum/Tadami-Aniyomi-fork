package eu.kanade.presentation.entries.components

import eu.kanade.domain.metadata.model.MetadataLoadError
import tachiyomi.domain.metadata.model.ExternalMetadata
import tachiyomi.domain.metadata.model.MetadataSource

internal data class ResolvedCover(
    val coverUrl: String,
    val coverUrlFallback: String?,
)

internal fun ExternalMetadata.displayScore(): String? {
    return score?.let { String.format("%.1f", it) }
}

internal fun ExternalMetadata.displayFormat(): String? {
    return format?.uppercase()
}

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
    if (!useMetadataCovers || isMetadataLoading || metadataError != null) {
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
