package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import tachiyomi.domain.translation.service.TranslationFragmentPlanner

/** Book adoption changes the logical chapter, never ownership of the resident content model. */
internal suspend fun translationSourceForChapter(
    chapterId: Long,
    residentOwnerId: Long?,
    bookActive: Boolean,
    resident: () -> List<String>,
    load: suspend (Long) -> List<String>,
): List<String> = if (!bookActive && chapterId == residentOwnerId) resident() else load(chapterId)

/** One source/coverage contract for reader, book, TTS and session-only delivery. */
internal fun translationArtifactForSource(
    entry: GeminiTranslationCacheEntry?,
    settings: NovelReaderSettings,
    replaceRulesFingerprint: String,
    source: List<String>,
    allowLegacyDisplay: Boolean = true,
): GeminiTranslationCacheEntry? {
    if (entry == null || source.isEmpty()) return null
    if (entry.translatedByIndex.keys != source.indices.toSet() ||
        entry.translatedByIndex.values.any { it.isBlank() }
    ) {
        return null
    }
    var requirements = settings.toTranslationCacheRequirements(
        replaceRulesFingerprint,
        TranslationFragmentPlanner.sourceFingerprint(source),
    )
    // Cache-disabled means no disk reads/writes, not that a delivered session result is unusable.
    requirements = requirements.copy(geminiDisableCache = false)
    val legacy = entry.sourceFingerprint.isBlank() && entry.generationFingerprint.isBlank()
    if (legacy &&
        allowLegacyDisplay
    ) {
        requirements = requirements.copy(sourceFingerprint = "", generationFingerprint = "")
    }
    if (!legacy &&
        (
            entry.sourceFingerprint != requirements.sourceFingerprint ||
                entry.generationFingerprint != requirements.generationFingerprint
            )
    ) {
        return null
    }
    if (!NovelReaderTranslationCacheResolver.matches(entry, requirements)) return null
    if (!legacy && entry.sourceSegmentCount != source.size) return null
    // This is a display-only legacy geometry check, never a source-hash backfill or disk rewrite.
    return if (legacy) entry.copy(sourceSegmentCount = source.size) else entry
}
