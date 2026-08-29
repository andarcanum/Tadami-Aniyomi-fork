package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AndroidNovelTtsPlatformEngineInitRetryTest {

    /**
     * Records instance creations and hands the init listener to the test so the
     * test itself decides what the "engine" reports (ERROR/SUCCESS). The real
     * TextToSpeech gets a muted listener because Robolectric's shadow binder
     * behavior is not relied upon.
     */
    private class RecordingEngine(
        private val appContext: Context,
    ) : AndroidNovelTtsPlatformEngine(appContext) {
        var creations = 0
        var pendingListener: TextToSpeech.OnInitListener? = null

        override fun createTtsInstance(
            listener: TextToSpeech.OnInitListener,
            enginePackage: String?,
        ): TextToSpeech {
            creations++
            pendingListener = listener
            return TextToSpeech(appContext, TextToSpeech.OnInitListener { })
        }
    }

    @Test
    fun `failed engine init is retried with a fresh instance on next initialize call`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val engine = RecordingEngine(context)
        runBlocking {
            val first = launch { engine.initialize(null) }
            yield()
            assertNotNull("first initialize should create a TTS instance", engine.pendingListener)
            engine.pendingListener!!.onInit(TextToSpeech.ERROR)
            first.join()

            engine.pendingListener = null
            val retry = launch { engine.initialize(null) }
            yield()
            // Regression: after a failed OnInit the dead instance used to stay in
            // the `tts` field while `initializedEnginePackage` stayed null, so the
            // fast-path guard `tts != null && initializedEnginePackage == engine`
            // short-circuited every retry for the default engine — no new instance
            // was ever created and TTS stayed bricked until app restart.
            assertNotNull(
                "retry after failed init must create a fresh TTS instance",
                engine.pendingListener,
            )
            engine.pendingListener!!.onInit(TextToSpeech.SUCCESS)
            retry.join()
            assertEquals(2, engine.creations)
        }
    }

    @Test
    fun `successful init is not recreated for the same engine package`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val engine = RecordingEngine(context)
        runBlocking {
            val first = launch { engine.initialize(null) }
            yield()
            assertNotNull(engine.pendingListener)
            engine.pendingListener!!.onInit(TextToSpeech.SUCCESS)
            first.join()

            // The fast-path guard must stay intact: an already-initialized engine
            // for the same package is reused, not recreated.
            engine.initialize(null)
            assertEquals(1, engine.creations)
        }
    }
}
