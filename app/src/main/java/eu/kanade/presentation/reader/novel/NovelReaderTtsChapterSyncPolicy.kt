package eu.kanade.presentation.reader.novel

internal fun resolveTtsAutoAdvancedChapterNavigationTarget(
    currentChapterId: Long,
    activeTtsChapterId: Long?,
    nextChapterId: Long?,
): Long? {
    if (activeTtsChapterId == null) return null
    if (activeTtsChapterId == currentChapterId) return null
    return activeTtsChapterId.takeIf { nextChapterId == null || it == nextChapterId }
}
