package eu.kanade.presentation.easteregg.crystal

import eu.kanade.domain.easteregg.crystal.CrystalAtmosphereModel
import eu.kanade.domain.easteregg.crystal.CrystalCalendarMoment
import eu.kanade.domain.easteregg.crystal.CrystalDayPhase
import eu.kanade.domain.easteregg.crystal.CrystalWeather
import eu.kanade.domain.easteregg.crystal.CrystalWeatherWindow
import org.junit.jupiter.api.Test

class CrystalAtmosphereModelTest {
    @Test
    fun phasesUseExactLocalHourBoundaries() {
        val expected = listOf(
            CrystalDayPhase.NIGHT to 0..4,
            CrystalDayPhase.DAWN to 5..7,
            CrystalDayPhase.DAY to 8..16,
            CrystalDayPhase.SUNSET to 17..20,
            CrystalDayPhase.NIGHT to 21..23,
        )
        for ((phase, hours) in expected) {
            for (hour in hours) {
                check(CrystalAtmosphereModel.phaseAt(CrystalCalendarMoment(2024, 1, 29, hour)) == phase)
            }
        }
    }

    @Test
    fun stringSeedsMatchIndependentJavaScriptFnvAndMulberryGoldens() {
        // Captured from the approved dependency-free JS model; strings are NOT numeric seeds.
        check(
            CrystalAtmosphereModel.scheduleForMonth(2024, 1, "crystal-browser") == listOf(
                window(3, 3),
                window(11, 10),
                window(16, 13, CrystalWeather.STORM),
                window(23, 6),
            ),
        )
        check(
            CrystalAtmosphereModel.scheduleForMonth(2026, 10, "0") == listOf(
                window(16, 0),
                window(21, 4, CrystalWeather.STORM),
                window(28, 10),
            ),
        )
        check(
            CrystalAtmosphereModel.scheduleForMonth(2026, 10, "4294967295") == listOf(
                window(7, 2),
                window(16, 13, CrystalWeather.STORM),
                window(21, 12),
                window(27, 11),
            ),
        )
        // UTF-16 code units (including surrogate pairs) must hash exactly as JS charCodeAt.
        check(
            CrystalAtmosphereModel.scheduleForMonth(2000, 1, "Кристалл🐈") == listOf(
                window(5, 13),
                window(16, 13),
                window(22, 2),
            ),
        )
        check(
            CrystalAtmosphereModel.scheduleForMonth(1900, 1, "0") == listOf(
                window(12, 9),
                window(17, 11),
                window(28, 1),
            ),
        )
    }

    @Test
    fun monthPlansStaySparseSortedLeapSafeAndDeterministicWithoutSharedState() {
        val counts = mutableSetOf<Int>()
        val storms = mutableSetOf<Int>()
        for (year in listOf(1900, 2000, 2024, 2025, 2026, 2027, 2028)) {
            for (month in 0..11) {
                for (seed in 0 until 80) {
                    val events = CrystalAtmosphereModel.scheduleForMonth(year, month, seed.toString())
                    check(events.size in 3..4)
                    counts += events.size
                    check(events == CrystalAtmosphereModel.scheduleForMonth(year, month, seed.toString()))
                    check(events.all { it.day in 1..daysInMonth(year, month) })
                    check(events.all { it.startHour in 0..17 && it.endHour == it.startHour + 6 })
                    check(events.zipWithNext().all { (a, b) -> b.day - a.day >= 3 })
                    val stormCount = events.count { it.weather == CrystalWeather.STORM }
                    check(stormCount <= 1)
                    check(events.none { it.weather == CrystalWeather.CLEAR })
                    storms += stormCount
                }
            }
        }
        check(counts == setOf(3, 4))
        check(storms == setOf(0, 1))
        val before = CrystalAtmosphereModel.scheduleForMonth(2026, 10, "0")
        CrystalAtmosphereModel.scheduleForMonth(2000, 1, "different-install")
        check(before == CrystalAtmosphereModel.scheduleForMonth(2026, 10, "0"))
    }

    @Test
    fun weatherWindowsAreHalfOpenLocalHoursAndNeverSpillIntoAnotherDay() {
        val seed = "4294967295"
        val events = CrystalAtmosphereModel.scheduleForMonth(2026, 10, seed)
        for (day in 1..30) {
            for (hour in 0..23) {
                val expected = events.firstOrNull {
                    it.day == day && hour >= it.startHour && hour < it.endHour
                }?.weather ?: CrystalWeather.CLEAR
                check(CrystalAtmosphereModel.weatherAt(CrystalCalendarMoment(2026, 10, day, hour), seed) == expected)
            }
        }
    }

    @Test
    fun onlyVisibleAutomaticRainOrStormCanRecordTheDecorativeEncounter() {
        for (weather in CrystalWeather.entries) {
            for (forced in listOf(false, true)) {
                for (visible in listOf(false, true)) {
                    check(
                        CrystalAtmosphereModel.shouldRecordEncounter(weather, forced, visible) ==
                            (weather != CrystalWeather.CLEAR && !forced && visible),
                    )
                }
            }
        }
    }

    private fun window(day: Int, hour: Int, weather: CrystalWeather = CrystalWeather.RAIN) =
        CrystalWeatherWindow(day, hour, hour + 6, weather)

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        3, 5, 8, 10 -> 30
        else -> 31
    }
}
