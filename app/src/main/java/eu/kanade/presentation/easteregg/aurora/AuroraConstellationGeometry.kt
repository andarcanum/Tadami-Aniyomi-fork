package eu.kanade.presentation.easteregg.aurora

import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Геометрия «Созвездия» — переосмысленного способа ввода ответа.
 *
 * Логика намеренно совместима со старой 3×3 панелью: те же 9 узлов
 * (1..9) и тот же «мостовой» проход промежуточных узлов, поэтому
 * кодирование в AuroraChannels.sigil() и криптопроверка в
 * AuroraQuest.offer() не меняются. Меняется только презентация:
 * узлы разбросаны как звёзды (детерминированный джиттер), а ввод —
 * непрерывная «нить света», которая рвётся при спешке («тонкая грань»).
 *
 * Чистый Kotlin без Android/Compose — тестируется юнитами.
 */
internal object AuroraConstellationGeometry {

    /** Максимальная скорость ведения нити, доли поля в миллисекунду. */
    private const val THREAD_BREAK_SPEED = 0.0045f

    /** Радиус захвата звезды, доли поля. */
    private const val STAR_HIT_RADIUS = 1f / 7.5f

    private fun fract(x: Float): Float = x - floor(x)

    private fun hash(i: Int): Float = fract(sin(i * 127.1f) * 43758.547f)

    /** X-центр звезды в единичном пространстве [0..1]. */
    fun starX(cell: Int): Float {
        val col = (cell - 1) % 3
        val jitter = (hash(cell) - 0.5f) * 0.16f
        return (col + 0.5f) / 3f + jitter
    }

    /** Y-центр звезды в единичном пространстве [0..1]. */
    fun starY(cell: Int): Float {
        val row = (cell - 1) / 3
        val jitter = (hash(cell * 31 + 7) - 0.5f) * 0.16f
        return (row + 0.5f) / 3f + jitter
    }

    /** Звезда под точкой касания (координаты — доли поля) или null. */
    fun hitStar(x: Float, y: Float): Int? {
        var best: Int? = null
        var bestDist = STAR_HIT_RADIUS
        for (cell in 1..9) {
            val dx = x - starX(cell)
            val dy = y - starY(cell)
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < bestDist) {
                best = cell
                bestDist = dist
            }
        }
        return best
    }

    /**
     * Промежуточный узел на прямой между a и b (совместимость с
     * графическим ключом: 2 → 8 всегда проходит через 5), иначе null.
     */
    fun bridgeCell(a: Int, b: Int): Int? {
        val ar = (a - 1) / 3
        val ac = (a - 1) % 3
        val br = (b - 1) / 3
        val bc = (b - 1) % 3
        if ((ar + br) % 2 != 0 || (ac + bc) % 2 != 0) return null
        val mid = ((ar + br) / 2) * 3 + (ac + bc) / 2 + 1
        return if (mid != a && mid != b) mid else null
    }

    /**
     * Регистрирует звезду в пути, добавляя пропущенный «мост».
     * @return true, если путь изменился.
     */
    fun registerStar(cell: Int?, path: MutableList<Int>): Boolean {
        if (cell == null || cell in path) return false
        val last = path.lastOrNull()
        if (last != null) {
            val bridge = bridgeCell(last, cell)
            if (bridge != null && bridge !in path) path.add(bridge)
        }
        path.add(cell)
        return true
    }

    /**
     * «Тонкая грань»: нить не выдерживает спешки.
     * @param distanceFraction пройденное расстояние в долях поля
     * @param dtMillis время между сэмплами
     */
    fun threadBreaks(distanceFraction: Float, dtMillis: Long): Boolean {
        if (dtMillis <= 0L) return false
        return distanceFraction / dtMillis > THREAD_BREAK_SPEED
    }
}
