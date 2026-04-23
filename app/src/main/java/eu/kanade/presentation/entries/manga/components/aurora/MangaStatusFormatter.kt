package eu.kanade.presentation.entries.manga.components.aurora

import android.content.Context
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.i18n.MR

/**
 * Utility object for formatting manga status as readable text.
 */
object MangaStatusFormatter {

    /**
     * Converts manga status code to readable localized text.
     */
    fun formatStatus(context: Context, statusCode: Long): String {
        return when (statusCode.toInt()) {
            SManga.ONGOING -> MR.strings.ongoing.getString(context)
            SManga.COMPLETED -> MR.strings.completed.getString(context)
            SManga.LICENSED -> MR.strings.licensed.getString(context)
            SManga.PUBLISHING_FINISHED -> MR.strings.publishing_finished.getString(context)
            SManga.CANCELLED -> MR.strings.cancelled.getString(context)
            SManga.ON_HIATUS -> MR.strings.on_hiatus.getString(context)
            SManga.UNKNOWN -> MR.strings.unknown.getString(context)
            else -> MR.strings.unknown.getString(context)
        }
    }

    fun formatStatus(statusCode: Long): String {
        return when (statusCode.toInt()) {
            SManga.ONGOING -> "ongoing"
            SManga.COMPLETED -> "completed"
            SManga.LICENSED -> "licensed"
            SManga.PUBLISHING_FINISHED -> "publishing finished"
            SManga.CANCELLED -> "cancelled"
            SManga.ON_HIATUS -> "on hiatus"
            SManga.UNKNOWN -> "unknown"
            else -> "unknown"
        }
    }
}
