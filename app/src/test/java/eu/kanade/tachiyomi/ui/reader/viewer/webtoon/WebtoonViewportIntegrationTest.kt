package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
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

    @Test
    fun ancestorZoomAndTranslationReachTheTileViewportWithoutResizingThePage() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val view = TestView(activity)
        root.addView(view, FrameLayout.LayoutParams(400, 4000))
        activity.setContentView(root)
        view.setExecutor(Executor { it.run() })
        view.setMaxTileSize(512)
        view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
        val canvasBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        fun render() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 400, 400)
            root.viewTreeObserver.dispatchOnPreDraw()
            root.draw(Canvas(canvasBitmap))
            ShadowLooper.idleMainLooper()
        }
        try {
            view.setImage(ImageSource.inputStream(byteArrayOf(1).inputStream()))
            ShadowLooper.idleMainLooper()
            repeat(3) { render() }
            view.fake.requests.clear()
            root.pivotX = 0f
            root.pivotY = 0f
            root.scaleX = 2f
            root.scaleY = 2f
            repeat(2) { render() }
            assertTrue(
                view.fake.requests.any {
                    it.second == 4
                },
                "Parent zoom must request 1/4 resolution, not keep 1/8 tiles",
            )
            assertEquals(4000, view.height)
            view.fake.requests.clear()
            view.translationY = -2000f
            repeat(2) { render() }
            assertTrue(view.fake.requests.isNotEmpty())
            assertTrue(
                view.fake.requests.all {
                    it.first.top >= 15_000
                },
                "Decoded rows should follow the clipped visible band",
            )
            assertEquals(4000, view.height)
        } finally {
            view.recycle()
            canvasBitmap.recycle()
            controller.pause().stop().destroy()
        }
    }
}
