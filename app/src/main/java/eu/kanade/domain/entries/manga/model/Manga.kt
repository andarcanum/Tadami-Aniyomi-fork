package eu.kanade.domain.entries.manga.model

import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

// TODO: move these into the domain model
val Manga.readingMode: Long
    get() = viewerFlags and ReadingMode.MASK.toLong()

val Manga.readerOrientation: Long
    get() = viewerFlags and ReaderOrientation.MASK.toLong()

val Manga.downloadedFilter: TriState
    get() = when (downloadedFilterRaw) {
        Manga.CHAPTER_SHOW_DOWNLOADED -> TriState.ENABLED_IS
        Manga.CHAPTER_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
        else -> TriState.DISABLED
    }

fun Manga.effectiveDownloadedFilter(downloadedOnly: Boolean): TriState {
    return if (downloadedOnly) TriState.ENABLED_IS else downloadedFilter
}

fun Manga.chaptersFiltered(downloadedOnly: Boolean): Boolean {
    return unreadFilter != TriState.DISABLED ||
        effectiveDownloadedFilter(downloadedOnly) != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED
}

fun Manga.toSManga(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.rating = rating.normalizeRating()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
    // Source-owned context: 1.6 extensions read e.g. a rotating slug back out of this.
    it.memo = memo
}

/**
 * Request object for the combined update API (extensions-lib 1.6).
 *
 * A 1.6 source fills in the object it is handed and may hand it straight back when it only parses
 * chapters. Seeding the stored cover here would make that echo indistinguishable from a freshly
 * parsed cover, so it is left empty: a null cover means "no new cover info" and the stored one is
 * kept, while a cover the source actually parsed is applied as before.
 */
fun Manga.toSMangaUpdateRequest(): SManga = toSManga().also {
    it.thumbnail_url = null
}

fun Manga.copyFrom(other: SManga): Manga {
    val author = other.author ?: author
    val artist = other.artist ?: artist
    val description = other.description ?: description
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        genre
    }
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    val rating = mergeRatings(current = rating, incoming = other.rating)
    return this.copy(
        author = author,
        artist = artist,
        description = description,
        genre = genres,
        rating = rating,
        thumbnailUrl = thumbnailUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
        // Keep what we stored when the source sends nothing back.
        memo = other.memo.takeIf { it.isNotEmpty() } ?: memo,
    )
}

fun SManga.toDomainManga(sourceId: Long): Manga {
    return Manga.create().copy(
        url = url.trim(),
        title = title.trim(),
        artist = artist,
        author = author,
        description = description,
        genre = getGenres(),
        status = status.toLong(),
        rating = rating.normalizeRating(),
        thumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
        memo = memo,
    )
}

fun Manga.hasCustomCover(coverCache: MangaCoverCache): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}

private fun Float.normalizeRating(): Float {
    if (this < 0f) return UNKNOWN_RATING
    val normalized = if (this > 1f) this / 10f else this
    return normalized.coerceIn(0f, 1f)
}

internal fun mergeRatings(current: Float, incoming: Float): Float {
    val normalizedCurrent = current.normalizeRating()
    val normalizedIncoming = incoming.normalizeRating()
    return when {
        normalizedCurrent < 0f -> normalizedIncoming
        normalizedIncoming < 0f -> normalizedCurrent
        else -> maxOf(normalizedCurrent, normalizedIncoming)
    }
}

internal fun resolveIncomingSourceRating(rawRating: Float, description: String?): Float {
    val normalizedRawRating = rawRating.normalizeRating()
    if (normalizedRawRating > 0f) {
        return rawRating
    }
    return SourceMangaRatingParser.parse(description) ?: UNKNOWN_RATING
}

private const val UNKNOWN_RATING = -1f

/**
 * Creates a ComicInfo instance based on the manga and chapter metadata.
 */
fun getComicInfo(
    manga: Manga,
    chapter: Chapter,
    urls: List<String>,
    categories: List<String>?,
    sourceName: String,
) = ComicInfo(
    title = ComicInfo.Title(chapter.name),
    series = ComicInfo.Series(manga.title),
    number = chapter.chapterNumber.takeIf { it >= 0 }?.let {
        if ((it.rem(1) == 0.0)) {
            ComicInfo.Number(it.toInt().toString())
        } else {
            ComicInfo.Number(it.toString())
        }
    },
    web = ComicInfo.Web(urls.joinToString(" ")),
    summary = manga.description?.let { ComicInfo.Summary(it) },
    writer = manga.author?.let { ComicInfo.Writer(it) },
    penciller = manga.artist?.let { ComicInfo.Penciller(it) },
    translator = chapter.scanlator?.let { ComicInfo.Translator(it) },
    genre = manga.genre?.let { ComicInfo.Genre(it.joinToString()) },
    publishingStatus = ComicInfo.PublishingStatusTachiyomi(
        ComicInfoPublishingStatus.toComicInfoValue(manga.status),
    ),
    categories = categories?.let { ComicInfo.CategoriesTachiyomi(it.joinToString()) },
    source = ComicInfo.SourceAniyomi(sourceName),
    inker = null,
    colorist = null,
    letterer = null,
    coverArtist = null,
    tags = null,
)
