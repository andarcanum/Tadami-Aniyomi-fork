package eu.kanade.presentation.easteregg.crystal

import eu.kanade.domain.easteregg.crystal.CrystalDayPhase
import eu.kanade.domain.easteregg.crystal.CrystalWeather
import kotlin.math.roundToInt

internal data class CrystalLivingCornerColors(
    val wall: Int,
    val floor: Int,
    val trim: Int,
    val skyHigh: Int,
    val skyLow: Int,
    val heading: Int,
    val muted: Int,
    val sunlight: Int,
    val sunStrength: Float,
    val shelterHigh: Int,
    val shelterLow: Int,
    val palette: CrystalPalette,
    val eInk: Boolean,
    val monochrome: Boolean,
) {
    fun token(name: String, fallback: Int): Int = normalize(
        when (name.removePrefix("--")) {
            "room-wall" -> wall
            "room-floor" -> floor
            "room-trim" -> trim
            "room-ink" -> heading
            "sky-high" -> skyHigh
            "sky-low" -> skyLow
            "scene-gold" -> if (monochrome) 0xFF444444.toInt() else palette.gold
            "scene-rose" -> if (monochrome) 0xFF777777.toInt() else palette.rose
            "scene-mint" -> if (monochrome) 0xFF999999.toInt() else palette.mint
            "cat-cream" -> 0xFFEEDABC.toInt()
            "cat-fur" -> 0xFFC29C71.toInt()
            "cat-stripe" -> 0xFF674F3C.toInt()
            "sunlight" -> sunlight
            "shelter-high" -> shelterHigh
            "shelter-low" -> shelterLow
            else -> fallback
        },
    )

    fun normalize(color: Int): Int {
        if (!monochrome) return color
        val luminance = (
            ((color ushr 16 and 255) * 0.2126) +
                ((color ushr 8 and 255) * 0.7152) +
                ((color and 255) * 0.0722)
            ).roundToInt()
        return (color and 0xFF000000.toInt()) or (luminance shl 16) or (luminance shl 8) or luminance
    }
}

internal fun resolveCrystalLivingColors(
    phase: CrystalDayPhase,
    weather: CrystalWeather,
    palette: CrystalPalette,
    eInk: Boolean,
    isDark: Boolean,
    isAmoled: Boolean,
    colorEInk: Boolean = false,
): CrystalLivingCornerColors {
    fun hex(value: String) = (0xFF000000L or value.toLong(16)).toInt()
    val base = when (phase) {
        CrystalDayPhase.DAWN -> arrayOf(
            "283d49",
            "344c54",
            "78969a",
            "4f587d",
            "f0b698",
            "f7e6ce",
            "c6d6d3",
            "ffdfb3",
            "fcebd9",
            "b89f8b",
        )
        CrystalDayPhase.DAY -> arrayOf(
            "2f4b57",
            "3d5c63",
            "84a6a8",
            "5fa4ba",
            "dcebd8",
            "f9ecda",
            "cfe0dd",
            "fff2c8",
            "fff4e3",
            "c2ab91",
        )
        CrystalDayPhase.SUNSET -> arrayOf(
            "293544",
            "38434f",
            "8c8996",
            "5c4b73",
            "eb9e7c",
            "fae4ca",
            "d4c9c6",
            "ffc996",
            "f8e3d0",
            "b39887",
        )
        CrystalDayPhase.NIGHT -> arrayOf(
            "172c3b",
            "223b45",
            "6b8c90",
            "122a3d",
            "3f6575",
            "f4dfbd",
            "bdd0cd",
            "bce0e6",
            "e6dad0",
            "a4968e",
        )
    }.map(::hex).toMutableList()
    var strength = when (phase) {
        CrystalDayPhase.DAWN -> 0.28f
        CrystalDayPhase.DAY -> 0.34f
        CrystalDayPhase.SUNSET -> 0.36f
        CrystalDayPhase.NIGHT -> 0.14f
    }
    if (weather != CrystalWeather.CLEAR) {
        val nighttime = phase == CrystalDayPhase.NIGHT
        val wet = if (weather == CrystalWeather.STORM) {
            arrayOf("1b2b36", "233640", "6f8791", "1d3142", "4f6875", "f2e8d5", "c2d0cc", "e2d8ce", "9c8f88")
        } else {
            arrayOf(
                if (nighttime) "182d3a" else "243c48", if (nighttime) "223b45" else "2f4b54",
                "7b9699", "364f63", "6f8b91", "f2e5d2", "c5d6d2", "ece2d5", "a89c90",
            )
        }
        listOf(0, 1, 2, 3, 4, 5, 6, 8, 9).forEachIndexed { index, slot -> base[slot] = hex(wet[index]) }
        strength = if (weather == CrystalWeather.STORM) 0.05f else 0.08f
    }
    if (isAmoled && phase == CrystalDayPhase.NIGHT && !eInk) base[0] = hex("0e202b")
    if (eInk) {
        base[0] = hex(if (isDark) "171717" else "f3f3f3")
        base[1] = hex(if (isDark) "262626" else "e4e4e4")
        base[2] = hex(if (isDark) "aaaaaa" else "555555")
        base[3] = hex(if (isDark) "333333" else "dddddd")
        base[4] = hex(if (isDark) "555555" else "f7f7f7")
        base[5] = hex(if (isDark) "eeeeee" else "111111")
        base[6] = hex(if (isDark) "cccccc" else "333333")
        base[8] = hex(if (isDark) "cccccc" else "f6f6f6")
        base[9] = hex(if (isDark) "777777" else "bbbbbb")
        strength = 0f
    }
    return CrystalLivingCornerColors(
        base[0],
        base[1],
        base[2],
        base[3],
        base[4],
        base[5],
        base[6],
        base[7],
        strength,
        base[8],
        base[9],
        palette,
        eInk,
        eInk && !colorEInk,
    )
}
