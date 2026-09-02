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

/**
 * Resolves the chapter the TTS-driven reader navigation should open, if any.
 *
 * A pending handoff (auto-advance over the chapter boundary) wins over the session-derived
 * target, but the current chapter is never a navigation target: after a seamless in-place switch
 * the pending handoff still names the chapter that is already on screen, and navigating "to" it
 * would replace the whole reader onto the same chapter (full reload, engine restart, re-speak).
 */
internal fun resolveTtsChapterNavigationTarget(
    pendingChapterHandoffId: Long?,
    currentChapterId: Long,
    activeTtsChapterId: Long?,
    nextChapterId: Long?,
): Long? {
    val target = pendingChapterHandoffId
        ?: resolveTtsAutoAdvancedChapterNavigationTarget(
            currentChapterId = currentChapterId,
            activeTtsChapterId = activeTtsChapterId,
            nextChapterId = nextChapterId,
        )
        ?: return null
    return target.takeUnless { it == currentChapterId }
}
