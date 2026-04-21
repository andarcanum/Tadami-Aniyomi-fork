package eu.kanade.presentation.series.novel

fun resolveNovelSeriesOrdinalLabel(index: Int, totalCount: Int): String? =
    if (totalCount > 1) (index + 1).toString() else null
