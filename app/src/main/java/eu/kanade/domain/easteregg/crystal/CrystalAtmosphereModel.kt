package eu.kanade.domain.easteregg.crystal

/** Local calendar inputs are supplied by the host; the model never reads a clock or time zone. */
internal data class CrystalCalendarMoment(val year: Int, val monthZeroBased: Int, val day: Int, val hour: Int)

internal enum class CrystalDayPhase { DAWN, DAY, SUNSET, NIGHT }
internal enum class CrystalWeather { CLEAR, RAIN, STORM }

internal data class CrystalWeatherWindow(
    val day: Int,
    val startHour: Int,
    val endHour: Int,
    val weather: CrystalWeather,
)

/** Cache-free, offline parity with crystal_atmosphere_model.js (string seeds, UTF-16 FNV-1a/Mulberry32). */
internal object CrystalAtmosphereModel {
    fun phaseAt(moment: CrystalCalendarMoment): CrystalDayPhase = when (moment.hour) {
        in 5..7 -> CrystalDayPhase.DAWN
        in 8..16 -> CrystalDayPhase.DAY
        in 17..20 -> CrystalDayPhase.SUNSET
        else -> CrystalDayPhase.NIGHT
    }

    fun scheduleForMonth(year: Int, monthZeroBased: Int, seed: String): List<CrystalWeatherWindow> {
        require(monthZeroBased in 0..11)
        val random = MonthRandom("string:$seed:$year:$monthZeroBased")
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = when (monthZeroBased) {
            1 -> if (leap) 29 else 28
            3, 5, 8, 10 -> 30
            else -> 31
        }
        val count = 3 + (random.next() * 2).toInt()
        // Sample a compressed calendar, then restore >=3-day gaps without retries.
        val pool = IntArray(days - 2 * (count - 1)) { it + 1 }
        for (index in 0 until count) {
            val chosen = index + (random.next() * (pool.size - index)).toInt()
            val previous = pool[index]
            pool[index] = pool[chosen]
            pool[chosen] = previous
        }
        val positions = pool.take(count).sorted()
        val stormIndex = if (random.next() < 0.5) (random.next() * count).toInt() else -1
        return positions.mapIndexed { index, position ->
            val startHour = (random.next() * 18).toInt()
            CrystalWeatherWindow(
                day = position + 2 * index,
                startHour = startHour,
                endHour = startHour + 6,
                weather = if (index == stormIndex) CrystalWeather.STORM else CrystalWeather.RAIN,
            )
        }
    }

    fun weatherAt(moment: CrystalCalendarMoment, seed: String): CrystalWeather =
        scheduleForMonth(moment.year, moment.monthZeroBased, seed).firstOrNull {
            it.day == moment.day && moment.hour >= it.startHour && moment.hour < it.endHour
        }?.weather ?: CrystalWeather.CLEAR

    /** A decorative install-local finding, never a grant/XP/receipt operation. */
    fun shouldRecordEncounter(weather: CrystalWeather, forced: Boolean, visible: Boolean): Boolean =
        visible && !forced && weather != CrystalWeather.CLEAR

    private class MonthRandom(key: String) {
        private var state = 0x811c9dc5.toInt()

        init {
            // Kotlin Char iteration and JS charCodeAt both use UTF-16 code units, not code points.
            for (char in key) state = (state xor char.code) * 16777619
        }

        fun next(): Double {
            state += 0x6d2b79f5
            var value = (state xor (state ushr 15)) * (state or 1)
            value = value xor (value + (value xor (value ushr 7)) * (value or 61))
            return ((value xor (value ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
        }
    }
}
