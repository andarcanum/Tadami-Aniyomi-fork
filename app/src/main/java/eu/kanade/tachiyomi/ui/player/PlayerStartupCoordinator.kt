package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils

internal class PlayerStartupCoordinator(
    private val chaptersProvider: () -> List<IndexedSegment>,
    private val currentPositionProvider: () -> Float,
    private val setWaitingSkipIntro: (Int) -> Unit,
    private val aniSkipResponse: suspend (Int?) -> List<TimeStamp>?,
    private val updateChapters: (List<IndexedSegment>) -> Unit,
    private val setChapter: (Float) -> Unit,
) {
    suspend fun handleAniSkip(
        playerDuration: Int?,
        waitingSkipIntro: Int,
        introSkipEnabled: Boolean,
        aniSkipEnabled: Boolean,
        disableAniSkipOnChapters: Boolean,
    ) {
        setWaitingSkipIntro(waitingSkipIntro)

        val currentChapters = chaptersProvider()
        if (!introSkipEnabled || !aniSkipEnabled) return
        if (disableAniSkipOnChapters && currentChapters.isNotEmpty()) return

        aniSkipResponse(playerDuration)?.let { stamps ->
            updateChapters(
                ChapterUtils.mergeChapters(
                    currentChapters = currentChapters,
                    stamps = stamps,
                    duration = playerDuration,
                ),
            )
            setChapter(currentPositionProvider())
        }
    }
}
