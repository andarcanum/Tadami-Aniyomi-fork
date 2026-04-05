package eu.kanade.presentation.util

import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen

internal fun shouldSuppressTransitionForInternalChapterReplace(
    initialScreen: Screen?,
    targetScreen: Screen?,
    internalChapterReplacePending: Boolean,
): Boolean {
    return resolveShouldSuppressTransitionForInternalChapterReplace(
        initialIsNovelReader = initialScreen is NovelReaderScreen,
        targetIsNovelReader = targetScreen is NovelReaderScreen,
        internalChapterReplacePending = internalChapterReplacePending,
    )
}

internal fun resolveShouldSuppressTransitionForInternalChapterReplace(
    initialIsNovelReader: Boolean,
    targetIsNovelReader: Boolean,
    internalChapterReplacePending: Boolean,
): Boolean {
    return internalChapterReplacePending && initialIsNovelReader && targetIsNovelReader
}
