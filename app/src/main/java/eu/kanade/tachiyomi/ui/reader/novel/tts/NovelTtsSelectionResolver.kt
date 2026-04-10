@file:Suppress("ktlint:standard:filename")

package eu.kanade.tachiyomi.ui.reader.novel.tts

data class NovelTtsResolvedVoiceSelection(
    val selectedVoice: NovelTtsVoiceDescriptor?,
    val selectedVoiceId: String,
    val selectedLocaleTag: String,
    val showLocaleFallback: Boolean,
)

fun resolveNovelTtsVoiceSelection(
    availableVoices: List<NovelTtsVoiceDescriptor>,
    availableLocales: List<String>,
    capabilities: NovelTtsEngineCapabilities,
    preferredVoiceId: String,
    preferredLocaleTag: String,
): NovelTtsResolvedVoiceSelection {
    val showLocaleFallback = !capabilities.supportsVoiceEnumeration || availableVoices.isEmpty()
    val normalizedPreferredVoiceId = preferredVoiceId.takeIf { it.isNotBlank() }
    val normalizedPreferredLocaleTag = preferredLocaleTag.takeIf { it.isNotBlank() }

    val selectedVoice = if (showLocaleFallback) {
        null
    } else {
        availableVoices.firstOrNull { it.id == normalizedPreferredVoiceId }
            ?: availableVoices.firstOrNull { it.localeTag == normalizedPreferredLocaleTag }
            ?: availableVoices.firstOrNull()
    }

    val selectedLocaleTag = when {
        selectedVoice != null -> selectedVoice.localeTag
        normalizedPreferredLocaleTag != null && availableLocales.contains(normalizedPreferredLocaleTag) -> {
            normalizedPreferredLocaleTag
        }
        availableLocales.isNotEmpty() -> availableLocales.first()
        normalizedPreferredLocaleTag != null -> normalizedPreferredLocaleTag
        else -> ""
    }

    return NovelTtsResolvedVoiceSelection(
        selectedVoice = selectedVoice,
        selectedVoiceId = selectedVoice?.id.orEmpty(),
        selectedLocaleTag = selectedLocaleTag,
        showLocaleFallback = showLocaleFallback,
    )
}
