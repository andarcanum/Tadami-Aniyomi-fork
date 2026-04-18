package eu.kanade.presentation.series.manga

fun resolveMangaSeriesOrdinalLabel(index: Int, totalCount: Int): String? =
    if (totalCount > 1) (index + 1).toString() else null
