package eu.kanade.presentation.entries.manga.components.aurora

import eu.kanade.tachiyomi.source.model.SManga

/**
 * Utility object for formatting manga status as readable text.
 */
object MangaStatusFormatter {

    /**
     * Converts manga status code to readable Russian text.
     */
    fun formatStatus(statusCode: Long): String {
        return when (statusCode.toInt()) {
            SManga.ONGOING -> "Выходит"
            SManga.COMPLETED -> "Завершено"
            SManga.LICENSED -> "Платно"
            SManga.PUBLISHING_FINISHED -> "Публикация завершена"
            SManga.CANCELLED -> "Отменено"
            SManga.ON_HIATUS -> "Перерыв"
            SManga.UNKNOWN -> "Неизвестно"
            else -> "Неизвестно"
        }
    }
}
