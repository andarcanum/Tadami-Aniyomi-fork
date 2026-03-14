package eu.kanade.presentation.library

import kotlin.math.roundToInt

private fun resolveLibraryCardProgressPercent(
    completedCount: Long,
    totalCount: Long,
): Int? {
    if (totalCount <= 0L) return null

    val normalizedCompleted = completedCount.coerceIn(0L, totalCount)
    return ((normalizedCompleted.toDouble() / totalCount.toDouble()) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
}

internal fun resolveAnimeLibraryCardProgressPercent(
    seenCount: Long,
    totalCount: Long,
): Int? {
    return resolveLibraryCardProgressPercent(
        completedCount = seenCount,
        totalCount = totalCount,
    )
}

internal fun resolveMangaLibraryCardProgressPercent(
    readCount: Long,
    totalCount: Long,
): Int? {
    return resolveLibraryCardProgressPercent(
        completedCount = readCount,
        totalCount = totalCount,
    )
}

internal fun resolveNovelLibraryCardProgressPercent(
    readCount: Long,
    totalCount: Long,
): Int? {
    return resolveLibraryCardProgressPercent(
        completedCount = readCount,
        totalCount = totalCount,
    )
}
