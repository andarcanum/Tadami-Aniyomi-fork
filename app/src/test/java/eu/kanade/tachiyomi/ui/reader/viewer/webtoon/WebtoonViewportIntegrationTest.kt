package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WebtoonViewportIntegrationTest {
    private class FakeDecoder : ImageRegionDecoder {
        val requests = mutableListOf<Pair<Rect, Int>>()
        override fun init(context: Context, provider: InputProvider) = Point(4000, 40000)
        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            requests += Rect(sRect) to sampleSize
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        override fun isReady() = true
        override fun recycle() = Unit
    }

    private class TestView(context: Context) : WebtoonSubsamplingImageView(context) {
        val fake = FakeDecoder()
        override fun createRegionDecoder(): ImageRegionDecoder = fake
    }

    private class SingleItemAdapter(private val child: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return object : RecyclerView.ViewHolder(child) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

        override fun getItemCount(): Int = 1
    }

    @Test
    fun recyclerZoomAndScrollPushViewportWithoutResizingThePage() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val recycler = WebtoonRecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            setHasFixedSize(false)
        }
        val view = TestView(activity).apply {
            layoutParams = RecyclerView.LayoutParams(400, 4000)
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
        }
        recycler.adapter = SingleItemAdapter(view)
        root.addView(recycler, FrameLayout.LayoutParams(400, 400))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        fun render() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 400, 400)
            root.draw(Canvas(canvasBitmap))
            ShadowLooper.idleMainLooper()
        }
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(3) { render() }

            view.fake.requests.clear()
            recycler.pivotX = 0f
            recycler.pivotY = 0f
            recycler.scaleX = 2f
            recycler.scaleY = 2f
            recycler.updateChildViewports()
            repeat(2) { render() }
            assertTrue(
                view.fake.requests.any { it.second == 4 },
                "Recycler zoom must push displayScale=2 and request 1/4 resolution tiles",
            )
            assertEquals(4000, view.height)

            view.fake.requests.clear()
            recycler.scrollBy(0, 2000)
            repeat(2) { render() }
            assertTrue(view.fake.requests.isNotEmpty())
            assertTrue(
                view.fake.requests.all { it.first.top >= 15_000 },
                "Decoded rows should follow the pushed scrolled viewport",
            )
            assertEquals(4000, view.height)
        } finally {
            view.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun flingLookaheadExtendsForwardAndIdleContractsWhileRetainSurvivesDetach() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val recycler = WebtoonRecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
        }
        val view = TestView(activity).apply {
            layoutParams = RecyclerView.LayoutParams(400, 4000)
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
        }
        recycler.adapter = SingleItemAdapter(view)
        root.addView(recycler, FrameLayout.LayoutParams(400, 400))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        fun render() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 400, 400)
            root.draw(Canvas(canvasBitmap))
            ShadowLooper.idleMainLooper()
        }
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(3) { render() }

            // Start a downward fling: lookahead expands prefetch past y=400 (sRect.top >= 4000).
            view.fake.requests.clear()
            recycler.fling(0, 4000)
            recycler.onScrollStateChanged(RecyclerView.SCROLL_STATE_SETTLING)
            recycler.onScrolled(0, 40)
            repeat(2) { render() }
            assertTrue(
                view.fake.requests.any { it.first.top >= 4000 && it.second == 8 },
                "Fling lookahead must prefetch detail tiles ahead of the visible band",
            )

            // Detaching into cache stops new prefetch but keeps retained tiles inside retain margin (y <= 600).
            recycler.stopScroll()
            recycler.onScrollStateChanged(RecyclerView.SCROLL_STATE_IDLE)
            repeat(2) { render() }
            root.removeView(recycler)
            root.addView(recycler, FrameLayout.LayoutParams(400, 400))
            view.fake.requests.clear()
            // Re-sync with y=[0, 400]: previously decoded tiles must stay retained and not re-decode.
            recycler.updateChildViewports()
            repeat(2) { render() }
            assertEquals(
                0,
                view.fake.requests.count { it.first.bottom <= 4000 && it.second == 8 },
                "Retained tiles in mCachedViews detach cycle must not re-decode on return",
            )
        } finally {
            view.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }
}
