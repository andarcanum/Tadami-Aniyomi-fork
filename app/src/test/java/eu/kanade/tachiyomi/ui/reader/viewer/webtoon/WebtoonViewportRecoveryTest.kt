package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WebtoonViewportRecoveryTest {

    private class FakeDecoder : ImageRegionDecoder {
        val requests = mutableListOf<Pair<Rect, Int>>()

        override fun init(context: Context, provider: InputProvider): Point = Point(4000, 40000)

        override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
            requests += Rect(sRect) to sampleSize
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        override fun isReady(): Boolean = true

        override fun recycle() = Unit
    }

    private class TestWebtoonView(context: Context) : WebtoonSubsamplingImageView(context) {
        val fake = FakeDecoder()

        override fun createRegionDecoder(): ImageRegionDecoder = fake
    }

    private class TwoItemAdapter(
        private val firstChild: View,
        private val secondChild: View,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val child = if (viewType == 0) firstChild else secondChild
            return object : RecyclerView.ViewHolder(child) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

        override fun getItemCount(): Int = 2
    }

    @Test
    fun reattachedWebtoonViewSyncsViewportWithoutRelayout() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val recycler = WebtoonRecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            setItemViewCacheSize(2)
        }
        val view = TestWebtoonView(activity).apply {
            layoutParams = RecyclerView.LayoutParams(400, 4000)
            setExecutor(Executor { it.run() })
            setMaxTileSize(512)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
        }
        val spacer = View(activity).apply {
            layoutParams = RecyclerView.LayoutParams(400, 4000)
        }
        recycler.adapter = TwoItemAdapter(view, spacer)
        root.addView(recycler, FrameLayout.LayoutParams(400, 400))
        activity.setContentView(root)
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 400, 400)
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            root.draw(Canvas(canvasBitmap))
            ShadowLooper.idleMainLooper()

            val externalViewportField = SubsamplingScaleImageView::class.java
                .getDeclaredField("externalViewport")
                .apply { isAccessible = true }

            // Scroll offscreen so RecyclerView detaches the view into mCachedViews (clearing externalViewport),
            // then scroll back so it re-attaches without rebind or onLayout(changed=true).
            recycler.scrollBy(0, 4500)
            ShadowLooper.idleMainLooper()
            assertFalse(view.isAttachedToWindow, "Scrolling past item 0 must detach it into mCachedViews")
            assertTrue(
                (externalViewportField.get(view) as RectF).isEmpty,
                "Detaching offscreen view must clear externalViewport",
            )
            view.fake.requests.clear()

            recycler.scrollBy(0, -4500)
            ShadowLooper.idleMainLooper()
            root.draw(Canvas(canvasBitmap))
            ShadowLooper.idleMainLooper()

            assertTrue(view.isAttachedToWindow, "Scrolling back must re-attach item 0 from mCachedViews")
            val syncedViewport = externalViewportField.get(view) as RectF
            assertFalse(
                syncedViewport.isEmpty,
                "Re-attached WebtoonSubsamplingImageView must immediately receive a non-empty viewport",
            )
        } finally {
            view.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun recycleClearsStaleExternalViewportCache() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val view = TestWebtoonView(activity)
        try {
            view.updateViewport(
                visible = RectF(0f, 0f, 400f, 800f),
                prefetch = RectF(0f, 0f, 400f, 1200f),
                retain = RectF(0f, 0f, 400f, 1600f),
                displayScale = 2f,
            )
            val retainField = WebtoonSubsamplingImageView::class.java
                .getDeclaredField("retainViewport")
                .apply { isAccessible = true }
            val scaleField = WebtoonSubsamplingImageView::class.java
                .getDeclaredField("lastDisplayScale")
                .apply { isAccessible = true }
            assertFalse((retainField.get(view) as RectF).isEmpty)
            assertEquals(2f, scaleField.getFloat(view))

            view.recycle()

            assertTrue(
                (retainField.get(view) as RectF).isEmpty,
                "recycle() must clear cached retainViewport so the next bound image starts clean",
            )
            assertEquals(
                1f,
                scaleField.getFloat(view),
                "recycle() must reset lastDisplayScale to 1f",
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun readerPageImageViewDispatchesTileLoadErrorOncePerBoundImage() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        var callbackCount = 0
        val pageView = ReaderPageImageView(activity).apply {
            onTileLoadError = { callbackCount++ }
        }
        try {
            pageView.onTileLoadError(IllegalStateException("tile failure #1"))
            pageView.onTileLoadError(IllegalStateException("tile failure #2"))
            assertEquals(
                1,
                callbackCount,
                "onTileLoadError must fire at most once per bound image to avoid retry storms",
            )

            pageView.recycle()
            pageView.onTileLoadError(IllegalStateException("tile failure after recycle"))
            assertEquals(
                2,
                callbackCount,
                "recycle() must reset tile error dispatch guard for the next image",
            )
        } finally {
            pageView.recycle()
            controller.pause().stop().destroy()
        }
    }
}
