package eu.kanade.presentation.easteregg.aurora

import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min

/**
 * Aurora Motion v2 — «время как материал».
 *
 * Единые токены движения для всех экранов «Сердца Авроры»:
 *  - медленное проявление вместо резких появлений;
 *  - «дыхание» — один спокойный цикл, а не дешёвые бесконечные пульсации;
 *  - обязательные reduced-motion ветки (см. [rememberAuroraReducedMotion]).
 */
object AuroraMotion {
    /** Медленное проявление крупных поверхностей (поле, карточки). */
    const val EMERGE_SLOW_MS = 900

    /** Быстрое проявление мелких элементов (подсказки, кнопки). */
    const val EMERGE_FAST_MS = 450

    /** Полный цикл «дыхания» сияния. */
    const val BREATH_CYCLE_MS = 7000

    /** Проявление одной строки текста загадки. */
    const val LINE_REVEAL_MS = 1000

    /** Пауза между строками. */
    const val LINE_PAUSE_MS = 500
}

/** Уровень визуального качества под устройство и настройки доступности. */
enum class AuroraTier {
    /** AGSL-шейдеры, синкай-слои, полный motion. */
    FULL,

    /** Градиентные фолбэки, полный motion (до Android 13 — без AGSL). */
    LITE,

    /** Reduced motion: статичные композиции, мгновенные проявления. */
    STATIC,
}

/**
 * true, если система просит убрать анимации (Accessibility «Remove
 * animations» / Developer options → animator duration scale = 0).
 * Все экраны Авроры обязаны иметь ветку для этого режима.
 */
@Composable
fun rememberAuroraReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** Тир качества: reduced motion → STATIC, Android 13+ → FULL, иначе LITE. */
@Composable
fun rememberAuroraTier(): AuroraTier {
    val reducedMotion = rememberAuroraReducedMotion()
    return remember(reducedMotion) {
        when {
            reducedMotion -> AuroraTier.STATIC
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> AuroraTier.FULL
            else -> AuroraTier.LITE
        }
    }
}

/**
 * Близость текущего часа к «часу сумерек» (≈6:00 и ≈19:00), 0..1.
 * Драматургический инструмент: в сумерки Аврора светится чуть ярче.
 * Чистая функция — тестируется без Android.
 */
fun auroraTwilightCloseness(hourOfDay: Int): Float {
    val h = ((hourOfDay % 24) + 24) % 24
    fun circular(a: Int, b: Int): Int {
        val d = abs(a - b)
        return min(d, 24 - d)
    }
    val dist = min(circular(h, 6), circular(h, 19))
    return (1f - dist / 4f).coerceIn(0f, 1f)
}

@Composable
fun rememberAuroraTwilight(): Float = remember {
    auroraTwilightCloseness(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
}
