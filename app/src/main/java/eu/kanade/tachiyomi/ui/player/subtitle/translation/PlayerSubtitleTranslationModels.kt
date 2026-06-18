package eu.kanade.tachiyomi.ui.player.subtitle.translation

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.animesource.model.Track

@Immutable
data class PlayerSubtitleTranslationTrack(
    val id: Int,
    val title: String,
    val language: String,
    val url: String?,
    val kind: PlayerSubtitleTranslationTrackKind,
    val streamSpecifier: String? = null,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
) {
    fun toExternalTrack(): Track? = url?.let { Track(it, language) }
}

enum class PlayerSubtitleTranslationTrackKind {
    External,
    Embedded,
}

@Immutable
data class PlayerSubtitleTranslationUiState(
    val tracks: List<PlayerSubtitleTranslationTrack> = emptyList(),
    val selectedTrackId: Int? = null,
    val providerId: SubtitleTranslationProviderId = SubtitleTranslationProviderId.Google,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "",
    val useCache: Boolean = true,
    val allowAi: Boolean = false,
    val progress: SubtitleTranslationProgress? = null,
    val isTranslating: Boolean = false,
    val error: String? = null,
) {
    val selectedTrack: PlayerSubtitleTranslationTrack?
        get() = tracks.firstOrNull { it.id == selectedTrackId }
}
