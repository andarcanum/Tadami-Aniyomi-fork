package eu.kanade.presentation.series.anime

fun resolveAnimeSeriesOrdinalLabel(index: Int, totalCount: Int): String? =
    if (totalCount > 1) (index + 1).toString() else null
