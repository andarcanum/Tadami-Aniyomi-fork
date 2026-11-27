package eu.kanade.tachiyomi.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitlesBorderStyle
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import `is`.xyz.mpv.MPVLib
import tachiyomi.i18n.aniyomi.AYMR

data class SubtitleStyleSnapshot(
    val fontSize: Int,
    val bold: Boolean,
    val italic: Boolean,
    val textColor: Int,
    val borderColor: Int,
    val backgroundColor: Int,
    val borderSize: Int,
    val borderStyle: SubtitlesBorderStyle,
)

enum class SubtitleStylePreset(
    val titleRes: StringResource,
    val snapshot: SubtitleStyleSnapshot,
) {
    Classic(
        titleRes = AYMR.strings.player_subtitle_preset_classic,
        snapshot = SubtitleStyleSnapshot(
            fontSize = 55,
            bold = false,
            italic = false,
            textColor = Color.White.toArgb(),
            borderColor = Color.Black.toArgb(),
            backgroundColor = Color.Transparent.toArgb(),
            borderSize = 3,
            borderStyle = SubtitlesBorderStyle.OutlineAndShadow,
        ),
    ),
    CinemaBox(
        titleRes = AYMR.strings.player_subtitle_preset_cinema_box,
        snapshot = SubtitleStyleSnapshot(
            fontSize = 54,
            bold = false,
            italic = false,
            textColor = Color.White.toArgb(),
            borderColor = Color.Black.copy(alpha = 0.72f).toArgb(),
            backgroundColor = Color.Black.copy(alpha = 0.68f).toArgb(),
            borderSize = 2,
            borderStyle = SubtitlesBorderStyle.BackgroundBox,
        ),
    ),
    HighContrastYellow(
        titleRes = AYMR.strings.player_subtitle_preset_yellow,
        snapshot = SubtitleStyleSnapshot(
            fontSize = 58,
            bold = true,
            italic = false,
            textColor = Color(0xFFFFE066).toArgb(),
            borderColor = Color.Black.toArgb(),
            backgroundColor = Color.Transparent.toArgb(),
            borderSize = 4,
            borderStyle = SubtitlesBorderStyle.OutlineAndShadow,
        ),
    ),
    NightSoft(
        titleRes = AYMR.strings.player_subtitle_preset_night_soft,
        snapshot = SubtitleStyleSnapshot(
            fontSize = 50,
            bold = false,
            italic = false,
            textColor = Color(0xFFD6D6D6).toArgb(),
            borderColor = Color(0xFF141414).toArgb(),
            backgroundColor = Color.Black.copy(alpha = 0.45f).toArgb(),
            borderSize = 2,
            borderStyle = SubtitlesBorderStyle.OutlineAndShadow,
        ),
    ),
    ;

    fun matches(current: SubtitleStyleSnapshot): Boolean = current == snapshot

    fun applyTo(
        preferences: SubtitlePreferences,
        applyLiveToMpv: Boolean = false,
    ) {
        preferences.subtitleFontSize().set(snapshot.fontSize)
        preferences.boldSubtitles().set(snapshot.bold)
        preferences.italicSubtitles().set(snapshot.italic)
        preferences.textColorSubtitles().set(snapshot.textColor)
        preferences.borderColorSubtitles().set(snapshot.borderColor)
        preferences.backgroundColorSubtitles().set(snapshot.backgroundColor)
        preferences.subtitleBorderSize().set(snapshot.borderSize)
        preferences.borderStyleSubtitles().set(snapshot.borderStyle)

        if (applyLiveToMpv) {
            runCatching {
                MPVLib.setPropertyInt("sub-font-size", snapshot.fontSize)
                MPVLib.setPropertyBoolean("sub-bold", snapshot.bold)
                MPVLib.setPropertyBoolean("sub-italic", snapshot.italic)
                MPVLib.setPropertyString("sub-color", snapshot.textColor.toMpvHexColor())
                MPVLib.setPropertyString("sub-border-color", snapshot.borderColor.toMpvHexColor())
                MPVLib.setPropertyString("sub-back-color", snapshot.backgroundColor.toMpvHexColor())
                MPVLib.setPropertyInt("sub-border-size", snapshot.borderSize)
                MPVLib.setPropertyString("sub-border-style", snapshot.borderStyle.value)
            }
        }
    }

    companion object {
        fun snapshotFrom(preferences: SubtitlePreferences): SubtitleStyleSnapshot {
            return SubtitleStyleSnapshot(
                fontSize = preferences.subtitleFontSize().get(),
                bold = preferences.boldSubtitles().get(),
                italic = preferences.italicSubtitles().get(),
                textColor = preferences.textColorSubtitles().get(),
                borderColor = preferences.borderColorSubtitles().get(),
                backgroundColor = preferences.backgroundColorSubtitles().get(),
                borderSize = preferences.subtitleBorderSize().get(),
                borderStyle = preferences.borderStyleSubtitles().get(),
            )
        }

        fun resolveActive(snapshot: SubtitleStyleSnapshot): SubtitleStylePreset? {
            return entries.firstOrNull { it.matches(snapshot) }
        }
    }
}

internal fun Int.toMpvHexColor(): String {
    return String.format("#%08X", this)
}
