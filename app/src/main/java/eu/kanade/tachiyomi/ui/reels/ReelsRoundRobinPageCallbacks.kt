package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem

/**
 * The video-page callbacks of one pager page, bound to the round-robin render owner that was
 * current when the page was composed.
 *
 * A player disposed by an owner change can still deliver a late callback. Reading the owner
 * from the live state at delivery time would attribute that event to the new generation; the
 * page-scope owner captured here makes the model reject it as stale instead. A page composed
 * without an owner keeps the ordinary (non-RR) callbacks.
 */
internal class ReelsRoundRobinPageCallbacks(
    private val screenModel: ReelsFeedScreenModel,
    private val owner: ReelsRoundRobinRenderOwner?,
) {
    fun onVideoCompleted(itemId: String) {
        if (owner != null) {
            screenModel.onRoundRobinVideoCompleted(owner, itemId)
        } else {
            screenModel.onVideoCompleted(itemId)
        }
    }

    fun onVisitStarted(startedItem: ShortVideoItem) {
        if (owner != null) {
            screenModel.onRoundRobinVisitStarted(owner, startedItem)
        } else {
            screenModel.onVisitStarted(startedItem)
        }
    }

    fun onFirstFramePresented(itemId: String) {
        if (owner != null) {
            screenModel.onRoundRobinFirstFrame(owner, itemId)
        } else {
            screenModel.onFirstFramePresented(itemId)
        }
    }

    fun onPlaybackSample(itemId: String, positionMs: Long, eligible: Boolean) {
        if (owner != null) {
            screenModel.onRoundRobinPlaybackSample(owner, itemId, positionMs, eligible)
        } else {
            screenModel.onPlaybackSample(itemId, positionMs, eligible)
        }
    }

    fun onVisitEnded(
        item: ShortVideoItem,
        positionMs: Long,
        positionFraction: Float,
        durationSec: Float,
    ) {
        if (owner != null) {
            screenModel.onRoundRobinVisitEnded(owner, item, positionMs, positionFraction, durationSec)
        } else {
            screenModel.onVisitEnded(item, positionMs, positionFraction, durationSec)
        }
    }
}
