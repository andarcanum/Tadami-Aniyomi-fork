package eu.kanade.tachiyomi.ui.reader.novel.translation

/**
 * Represents the translation backend used for chapter translation.
 * Used to make cache keys backend-specific so ML Kit and Google results don't collide.
 */
enum class ChapterTranslationBackend {
    ML_KIT,
    GOOGLE,
    ;

    fun fingerprint(): String = name
}
