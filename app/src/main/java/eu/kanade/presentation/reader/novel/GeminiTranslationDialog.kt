package eu.kanade.presentation.reader.novel

internal enum class TranslationKind {
    Gemini,
    Google,
}

internal data class TranslationSwitchRequest(
    val from: TranslationKind,
    val to: TranslationKind,
    val profileId: String? = null,
    val forceRetranslate: Boolean = false,
)
