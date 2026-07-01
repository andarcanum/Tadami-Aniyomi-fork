package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.widget.LinearLayout
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage
import eu.kanade.tachiyomi.widget.ViewPagerAdapter

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

    private fun setupZoomSynchronization() {
        leftPageHolder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val leftView = leftPageHolder.getImageView() as? SubsamplingScaleImageView
            val rightView = rightPageHolder.getImageView() as? SubsamplingScaleImageView

            leftView?.setOnStateChangedListener(object : SubsamplingScaleImageView.OnStateChangedListener {
                override fun onScaleChanged(newScale: Float, origin: Int) {
                    syncZoom(leftView, rightView)
                }

                override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                    syncZoom(leftView, rightView)
                }
            })

            rightView?.setOnStateChangedListener(object : SubsamplingScaleImageView.OnStateChangedListener {
                override fun onScaleChanged(newScale: Float, origin: Int) {
                    syncZoom(rightView, leftView)
                }

                override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                    syncZoom(rightView, leftView)
                }
            })
        }
    }

    private fun syncZoom(source: SubsamplingScaleImageView, target: SubsamplingScaleImageView?) {
        if (isSyncing || target == null) return
        isSyncing = true
        try {
            val scale = source.scale
            val center = source.center
            if (center != null && (target.scale != scale || target.center != center)) {
                target.setScaleAndCenter(scale, center)
            }
        } catch (e: Exception) {
            // Ignore if view is not ready
        } finally {
            isSyncing = false
        }
    }
}
