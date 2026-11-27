package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.widget.LinearLayout
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlin.math.abs

/**
 * A page holder that displays two pages side-by-side.
 */
@SuppressLint("ViewConstructor")
class JoinedPagerPageHolder(
    context: Context,
    val viewer: PagerViewer,
    val page: JoinedReaderPage,
) : LinearLayout(context), ViewPagerAdapter.PositionableView {

    override val item get() = page

    private val leftPageHolder: PagerPageHolder
    private val rightPageHolder: PagerPageHolder

    private var isSyncing = false

    init {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        leftPageHolder = PagerPageHolder(context, viewer, page.firstPage).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        rightPageHolder = PagerPageHolder(context, viewer, page.secondPage).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }

        addView(leftPageHolder)
        addView(rightPageHolder)

        setupZoomSynchronization()
    }

    /**
     * Returns the nested half-page holder for [page], if any. The viewer's page-holder lookup
     * only saw direct pager children, leaving pan navigation and onPageSelected(forward) dead
     * for spread pages without this.
     */
    fun findPageHolder(page: ReaderPage): PagerPageHolder? = when {
        page === this.page -> leftPageHolder
        else -> listOf(leftPageHolder, rightPageHolder).firstOrNull { it.item == page }
    }

    fun onPageSelected(forward: Boolean) {
        leftPageHolder.onPageSelected(forward)
        rightPageHolder.onPageSelected(forward)
    }

    fun setDecodePriorityBoost(boost: Boolean) {
        leftPageHolder.setDecodePriorityBoost(boost)
        rightPageHolder.setDecodePriorityBoost(boost)
    }

    fun setViewportVisible(visible: Boolean) {
        leftPageHolder.setViewportVisible(visible)
        rightPageHolder.setViewportVisible(visible)
    }

    private fun setupZoomSynchronization() {
        leftPageHolder.onScaleChanged = { syncZoom(leftPageHolder, rightPageHolder) }
        leftPageHolder.onCenterChanged = { syncZoom(leftPageHolder, rightPageHolder) }
        rightPageHolder.onScaleChanged = { syncZoom(rightPageHolder, leftPageHolder) }
        rightPageHolder.onCenterChanged = { syncZoom(rightPageHolder, leftPageHolder) }
    }

    private fun syncZoom(sourceHolder: PagerPageHolder, targetHolder: PagerPageHolder) {
        if (isSyncing) return
        val source = sourceHolder.getImageView() as? SubsamplingScaleImageView ?: return
        val target = targetHolder.getImageView() as? SubsamplingScaleImageView ?: return
        if (
            !source.isReady ||
            !target.isReady ||
            source.minScale <= 0f ||
            target.minScale <= 0f ||
            source.sWidth <= 0 ||
            source.sHeight <= 0 ||
            target.sWidth <= 0 ||
            target.sHeight <= 0
        ) {
            return
        }
        val sourceCenter = source.center ?: return
        val normalizedScale = (source.scale / source.minScale) * target.minScale
        val normalizedCenter = PointF(
            sourceCenter.x / source.sWidth * target.sWidth,
            sourceCenter.y / source.sHeight * target.sHeight,
        )
        val currentTargetCenter = target.center
        val scaleUnchanged = abs(target.scale - normalizedScale) <= 1e-4f
        val centerUnchanged = currentTargetCenter != null &&
            abs(currentTargetCenter.x - normalizedCenter.x) <= 0.5f &&
            abs(currentTargetCenter.y - normalizedCenter.y) <= 0.5f
        if (scaleUnchanged && centerUnchanged) return

        isSyncing = true
        try {
            target.setScaleAndCenter(normalizedScale, normalizedCenter)
        } catch (_: Exception) {
            // Ignore if view is not ready
        } finally {
            isSyncing = false
        }
    }

    val isHdr: Boolean
        get() = leftPageHolder.isHdr || rightPageHolder.isHdr

    fun recycle() {
        leftPageHolder.recycle()
        rightPageHolder.recycle()
    }

    fun trimMemory(level: Int) {
        leftPageHolder.trimMemory(level)
        rightPageHolder.trimMemory(level)
    }
}
