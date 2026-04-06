package eu.kanade.presentation.entries.manga.components.aurora

import android.util.Log
import androidx.compose.runtime.Immutable
import eu.kanade.domain.entries.manga.model.SourceMangaRatingParser
import eu.kanade.domain.entries.manga.model.SourceMangaRatingResolver
import eu.kanade.domain.metadata.model.MetadataLoadError
import eu.kanade.presentation.entries.components.displayFormat
import eu.kanade.presentation.entries.components.displayStatus
import eu.kanade.presentation.entries.components.isCompleted
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.metadata.model.ExternalMetadata
import kotlin.math.roundToInt
import java.util.Locale

@Immutable
data class MangaProgressSnapshot(
    val currentChapterIndex: Int?,
    val totalChapters: Int,
    val percent: Float?,
) {
    val hasProgress: Boolean
        get() = percent != null

    val isCompleted: Boolean
        get() = percent?.let { it >= COMPLETED_THRESHOLD } == true

    val chaptersText: String
        get() = totalChapters.toString()

    val progressText: String
        get() {
            val currentChapter = (currentChapterIndex ?: -1) + 1
            val percentText = percent?.let { "${(it * 100f).roundToInt().coerceIn(0, 100)}%" }
            return buildString {
                append(currentChapter.coerceAtLeast(0))
                append(" / ")
                append(totalChapters)
                if (percentText != null) {
                    append(" (")
                    append(percentText)
                    append(')')
                }
            }
        }

    companion object {
        private const val COMPLETED_THRESHOLD = 0.99999f
    }
}

@Immutable
data class MangaDetailsSnapshot(
    val sourceTitle: String?,
    val translationText: String?,
    val ratingValue: Float?,
    val ratingText: String?,
    val formatText: String?,
    val statusText: String,
    val progress: MangaProgressSnapshot?,
    val isCompleted: Boolean,
)

fun resolveMangaDetailsSnapshot(
    sourceTitle: String?,
    sourceName: String?,
    sourceLanguage: String?,
    manga: Manga,
    chapters: List<Chapter>,
    selectedScanlator: String?,
    scanlatorChapterCounts: Map<String, Int>,
    mangaMetadata: ExternalMetadata?,
    isMetadataLoading: Boolean,
    metadataError: MetadataLoadError?,
): MangaDetailsSnapshot {
    val ratingValue = resolveMangaRatingValue(
        manga = manga,
        mangaMetadata = mangaMetadata,
        isMetadataLoading = isMetadataLoading,
        metadataError = metadataError,
        sourceName = sourceName,
    )
    val ratingText = ratingValue?.let { RatingParser.formatRating((it * 10f).coerceIn(0f, 10f)) }
    val formatText = when {
        isMetadataLoading -> "..."
        metadataError == MetadataLoadError.NotAuthenticated -> null
        else -> mangaMetadata?.displayFormat()
    }
    val statusText = when {
        isMetadataLoading -> "..."
        metadataError == MetadataLoadError.NotAuthenticated -> MangaStatusFormatter.formatStatus(manga.status)
        else -> mangaMetadata?.displayStatus() ?: MangaStatusFormatter.formatStatus(manga.status)
    }
    val progress = resolveMangaProgressSnapshot(chapters)
    val metadataCompleted = mangaMetadata?.let { it.isCompleted() } == true

    return MangaDetailsSnapshot(
        sourceTitle = sourceTitle,
        translationText = resolveMangaTranslationText(
            selectedScanlator = selectedScanlator,
            scanlatorChapterCounts = scanlatorChapterCounts,
            sourceLanguage = sourceLanguage,
        ),
        ratingValue = ratingValue,
        ratingText = ratingText,
        formatText = formatText,
        statusText = statusText,
        progress = progress,
        isCompleted = metadataCompleted || progress?.isCompleted == true,
    )
}

fun resolveMangaRatingValue(
    manga: Manga,
    mangaMetadata: ExternalMetadata?,
    isMetadataLoading: Boolean,
    metadataError: MetadataLoadError?,
    sourceName: String? = null,
): Float? {
    manga.rating.takeIf { it >= 0f }?.let {
        val normalized = it.coerceIn(0f, 1f)
        debugLog(
            "resolveMangaRatingValue: direct manga.rating=${it.previewFloat()} normalized=${normalized.previewFloat()} sourceName=$sourceName",
        )
        return normalized
    }

    SourceMangaRatingResolver.resolve(sourceName, manga.description)?.let {
        val normalized = (it / 10f).coerceIn(0f, 1f)
        debugLog(
            "resolveMangaRatingValue: source resolver rating=${it.previewFloat()} normalized=${normalized.previewFloat()} sourceName=$sourceName loading=$isMetadataLoading error=$metadataError",
        )
        return normalized
    }

    if (isMetadataLoading || metadataError == MetadataLoadError.NotAuthenticated) {
        debugLog(
            "resolveMangaRatingValue: metadata blocked loading=$isMetadataLoading error=$metadataError sourceName=$sourceName",
        )
        return null
    }

    mangaMetadata?.score?.takeIf { it >= 0.0 }?.let {
        val normalized = (it / 10.0).toFloat().coerceIn(0f, 1f)
        debugLog(
            "resolveMangaRatingValue: metadata score=${it.previewDouble()} normalized=${normalized.previewFloat()} sourceName=$sourceName",
        )
        return normalized
    }

    return SourceMangaRatingParser.parse(manga.description)?.let {
        val normalized = (it / 10f).coerceIn(0f, 1f)
        debugLog(
            "resolveMangaRatingValue: fallback parser rating=${it.previewFloat()} normalized=${normalized.previewFloat()} sourceName=$sourceName",
        )
        normalized
    }
}

fun resolveMangaProgressSnapshot(chapters: List<Chapter>): MangaProgressSnapshot? {
    val chapterItems = chapters
    if (chapterItems.isEmpty()) return null

    val currentChapterIndex = chapterItems.indexOfLast { chapter ->
        chapter.read || chapter.lastPageRead > 0L
    }.takeIf { it >= 0 }

    val percent = currentChapterIndex?.let { index ->
        (index + 1).toFloat() / chapterItems.size.toFloat()
    }

    return MangaProgressSnapshot(
        currentChapterIndex = currentChapterIndex,
        totalChapters = chapterItems.size,
        percent = percent,
    )
}

fun resolveMangaTranslationText(
    selectedScanlator: String?,
    scanlatorChapterCounts: Map<String, Int>,
    sourceLanguage: String?,
): String? {
    val candidate = buildList {
        sourceLanguage?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        selectedScanlator?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        scanlatorChapterCounts.entries.singleOrNull()?.key?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.firstOrNull() ?: return null

    return resolveLocaleLabel(candidate)
}

private fun resolveLocaleLabel(candidate: String): String? {
    val normalizedCandidate = candidate.trim().replace('_', '-')
    val languageTagLocale = if (normalizedCandidate.matches(Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$"))) {
        runCatching { Locale.forLanguageTag(normalizedCandidate) }
            .getOrNull()
            ?.takeIf { it.language.isNotBlank() && it.language != "und" }
    } else {
        null
    }
    languageTagLocale?.let { locale ->
        return locale.getDisplayLanguage(locale).toDisplayTitleCase(locale)
    }

    val matchedLocale = Locale.getAvailableLocales().find { locale ->
        val nativeDisplayName = locale.getDisplayName(locale)
        val englishDisplayName = locale.getDisplayName(Locale.ENGLISH)
        val nativeLanguage = locale.getDisplayLanguage(locale)
        val englishLanguage = locale.getDisplayLanguage(Locale.ENGLISH)
        candidate.equals(nativeDisplayName, ignoreCase = true) ||
            candidate.equals(englishDisplayName, ignoreCase = true) ||
            candidate.equals(nativeLanguage, ignoreCase = true) ||
            candidate.equals(englishLanguage, ignoreCase = true)
    }

    return matchedLocale?.let { locale ->
        locale.getDisplayLanguage(locale).toDisplayTitleCase(locale)
    }
}

private fun String.toDisplayTitleCase(locale: Locale): String {
    return replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
    }
}

private fun Float.previewFloat(): String = String.format(Locale.US, "%.3f", this)

private fun Double.previewDouble(): String = String.format(Locale.US, "%.3f", this)

private fun debugLog(message: String) {
    runCatching { Log.d("MangaDetailsSnapshot", message) }
}
