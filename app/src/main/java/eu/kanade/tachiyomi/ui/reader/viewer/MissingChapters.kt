package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.data.database.models.manga.toDomainChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlin.math.floor
import tachiyomi.domain.items.chapter.service.calculateChapterGap as domainCalculateChapterGap

fun calculateChapterGap(higherReaderChapter: ReaderChapter?, lowerReaderChapter: ReaderChapter?): Int {
    return domainCalculateChapterGap(
        higherReaderChapter?.chapter?.toDomainChapter(),
        lowerReaderChapter?.chapter?.toDomainChapter(),
    )
}

fun calculateVisibleChapterGap(
    higherReaderChapter: ReaderChapter?,
    lowerReaderChapter: ReaderChapter?,
    allReaderChapters: List<ReaderChapter>,
): Int {
    val rawGap = calculateChapterGap(higherReaderChapter, lowerReaderChapter)
    if (rawGap <= 0) return 0

    val higherChapter = higherReaderChapter?.chapter?.toDomainChapter() ?: return rawGap
    val lowerChapter = lowerReaderChapter?.chapter?.toDomainChapter() ?: return rawGap
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return 0

    val higherNumber = floor(higherChapter.chapterNumber).toInt()
    val lowerNumber = floor(lowerChapter.chapterNumber).toInt()

    val existingIntermediateCount = allReaderChapters.count { readerChapter ->
        val chapter = readerChapter.chapter.toDomainChapter() ?: return@count false
        if (!chapter.isRecognizedNumber) return@count false
        val number = floor(chapter.chapterNumber).toInt()
        number > lowerNumber && number < higherNumber
    }

    return (rawGap - existingIntermediateCount).coerceAtLeast(0)
}
