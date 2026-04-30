package eu.kanade.tachiyomi.ui.player.utils

import androidx.core.os.LocaleListCompat
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class TrackSelect(
    private val subtitlePreferences: SubtitlePreferences = Injekt.get(),
    private val audioPreferences: AudioPreferences = Injekt.get(),
) {

    fun getPreferredTrackIndex(tracks: List<VideoTrack>, subtitle: Boolean = true): VideoTrack? {
        if (tracks.isEmpty()) return null

        val prefLangs = if (subtitle) {
            subtitlePreferences.preferredSubLanguages().get()
        } else {
            audioPreferences.preferredAudioLanguages().get()
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val whitelist = if (subtitle) {
            subtitlePreferences.subtitleWhitelist().get()
        } else {
            ""
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val blacklist = if (subtitle) {
            subtitlePreferences.subtitleBlacklist().get()
        } else {
            ""
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val preferExactSubtitleMatch = subtitle && subtitlePreferences.preferExactSubtitleMatch().get()

        val locales = prefLangs.map(Locale::forLanguageTag).ifEmpty {
            listOf(LocaleListCompat.getDefault()[0]!!)
        }

        val filteredTracks = tracks.filterNot { track ->
            blacklist.any { track.name.contains(it, true) }
        }

        val chosenLocale = locales.firstOrNull { locale ->
            filteredTracks.any { track -> containsLang(track, locale) }
        } ?: return null

        val localeMatches = filteredTracks.filter { track ->
            containsLang(track, chosenLocale)
        }

        val prioritizedMatches = if (preferExactSubtitleMatch) {
            localeMatches.filter { track ->
                matchesExactLang(track, chosenLocale)
            }.takeIf { it.isNotEmpty() } ?: localeMatches
        } else {
            localeMatches
        }

        return prioritizedMatches.firstOrNull { track ->
            whitelist.any { track.name.contains(it, true) }
        } ?: prioritizedMatches.firstOrNull()
    }

    private fun containsLang(track: VideoTrack, locale: Locale): Boolean {
        val localName = locale.getDisplayName(locale)
        val englishName = locale.getDisplayName(Locale.ENGLISH).substringBefore(" (")
        val localeLanguageTokens = localeLanguageTokens(locale)
        val langRegex = Regex(
            """\b(?:${localeLanguageTokens.joinToString("|") { Regex.escape(it) }})\b""",
            RegexOption.IGNORE_CASE,
        )

        return track.name.contains(localName, true) ||
            track.name.contains(englishName, true) ||
            track.language?.let { langRegex.find(it) != null } == true
    }

    private fun matchesExactLang(track: VideoTrack, locale: Locale): Boolean {
        val normalizedTrackName = track.name.trim().lowercase(Locale.ROOT)
        val normalizedTrackLanguage = track.language?.trim()?.lowercase(Locale.ROOT)

        val exactDisplayNames = setOf(
            locale.getDisplayName(locale).trim().lowercase(Locale.ROOT),
            locale.getDisplayName(Locale.ENGLISH).substringBefore(" (").trim().lowercase(Locale.ROOT),
        )
        val exactLanguageTokens = localeLanguageTokens(locale).map { it.lowercase(Locale.ROOT) }.toSet()

        return normalizedTrackName in exactDisplayNames ||
            normalizedTrackName in exactLanguageTokens ||
            (normalizedTrackName.isBlank() && normalizedTrackLanguage in exactLanguageTokens)
    }

    private fun localeLanguageTokens(locale: Locale): List<String> {
        return buildList {
            add(locale.language)
            runCatching { locale.isO3Language }.getOrNull()?.let { add(it) }
            add(locale.toLanguageTag())
        }.filter { it.isNotBlank() }
    }
}
