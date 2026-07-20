package eu.kanade.domain.easteregg.aurora

import java.util.Calendar

/**
 * «Тонкий час» — окно, когда граница миров тоньше всего.
 * Вместо жёсткого hour == 3 (враждебно к ритуалам и краям часа) —
 * мягкое окно 02:45–04:15 локального времени.
 * Использовать во ВСЕХ триггерах вместо проверок часа вручную.
 */
object AuroraNight {

    const val START_MINUTES = 2 * 60 + 45
    const val END_MINUTES = 4 * 60 + 15

    fun isVeilThin(calendar: Calendar = Calendar.getInstance()): Boolean {
        val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return minutes in START_MINUTES..END_MINUTES
    }
}
