package eu.kanade.presentation.easteregg.aurora

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Хаптический словарь Авроры (Pillar 4 плана).
 * Только штатные HapticFeedbackConstants: без разрешения VIBRATE
 * и с автоматическим уважением системной настройки хаптики.
 * View берите через LocalView.current в Compose.
 */
object AuroraSensory {

    /** Лёгкий тик: палец зацепил узел сигиля. */
    fun node(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Средний: промежуточный акцент (мост, шаг ритуала). */
    fun bridge(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Сильный: сигиль замкнут / важное действие принято. */
    fun seal(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Двойной пульс с задержкой: верный ответ (вызывать из оверлея). */
    fun solve(view: View) {
        seal(view)
        view.postDelayed({ node(view) }, 140L)
    }
}
