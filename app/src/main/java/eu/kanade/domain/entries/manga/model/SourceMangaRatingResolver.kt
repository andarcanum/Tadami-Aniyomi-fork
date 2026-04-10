package eu.kanade.domain.entries.manga.model

import android.util.Log

internal object SourceMangaRatingResolver {
    private const val TAG = "MangaRatingResolver"

    fun resolve(sourceName: String?, description: String?): Float? {
        if (!SourceMangaRatingSourceMatcher.isGroupLeSource(sourceName)) {
            debugLog("resolve: skip source=$sourceName")
            return null
        }

        val parsed = SourceMangaRatingParser.parse(description)
        debugLog(
            "resolve: source=$sourceName parsed=$parsed descriptionPreview=${description.previewForLog()}",
        )
        return parsed
    }

    private fun String?.previewForLog(limit: Int = 120): String {
        return this
            ?.replace(Regex("\\s+"), " ")
            ?.take(limit)
            .orEmpty()
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }
}
