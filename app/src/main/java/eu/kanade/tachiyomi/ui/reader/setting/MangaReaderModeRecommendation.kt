package eu.kanade.tachiyomi.ui.reader.setting

import tachiyomi.domain.entries.manga.model.Manga
import java.util.Locale

private const val TALL_PAGE_RATIO = 3f
private const val EXTREME_TALL_PAGE_RATIO = 5f
private const val MIN_TALL_PAGES = 2

private val WEBTOON_FORMAT_HINTS = setOf(
    "webtoon",
    "web toon",
    "long strip",
    "long-strip",
    "longstrip",
    "manhwa",
    "манхва",
)

private val SOURCE_NAME_HINTS = setOf(
    "webtoon",
    "manhwa",
    "манхва",
)

internal data class MangaReaderPageDimensions(
    val width: Int,
    val height: Int,
)

internal fun recommendReadingModeForMangaFormat(
    genres: List<String>?,
    sourceName: String?,
): Int? {
    val hasGenreHint = genres.orEmpty().any { value ->
        WEBTOON_FORMAT_HINTS.any { hint -> value.normalizedFormatText().contains(hint) }
    }
    val hasSourceHint = sourceName
        ?.normalizedFormatText()
        ?.let { source -> SOURCE_NAME_HINTS.any(source::contains) }
        ?: false

    return if (hasGenreHint || hasSourceHint) {
        ReadingMode.WEBTOON.flagValue
    } else {
        null
    }
}

internal fun recommendReadingModeForMangaFormat(
    manga: Manga,
    sourceName: String?,
): Int? {
    return recommendReadingModeForMangaFormat(
        genres = manga.genre,
        sourceName = sourceName,
    )
}

internal fun isLikelyWebtoonFromPageDimensions(
    dimensions: Collection<MangaReaderPageDimensions>,
): Boolean {
    var tallPages = 0
    dimensions.forEach { dimension ->
        if (dimension.width <= 0 || dimension.height <= 0) return@forEach
        val ratio = dimension.height.toFloat() / dimension.width.toFloat()
        if (ratio >= EXTREME_TALL_PAGE_RATIO) {
            return true
        }
        if (ratio >= TALL_PAGE_RATIO) {
            tallPages += 1
        }
    }
    return tallPages >= MIN_TALL_PAGES
}

private fun String.normalizedFormatText(): String {
    return lowercase(Locale.ROOT).trim()
}
