package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
class AndroidNovelTtsPlatformEngineStopTest {

    /** Captures the [UtteranceProgressListener] the engine installs on the platform TTS instance. */
    private class CapturingEngine(
        private val appContext: Context,
    ) : AndroidNovelTtsPlatformEngine(appContext) {
        var pendingInitListener: TextToSpeech.OnInitListener? = null
        var capturedProgressListener: UtteranceProgressListener? = null

        override fun createTtsInstance(
            listener: TextToSpeech.OnInitListener,
            enginePackage: String?,
        ): TextToSpeech {
            pendingInitListener = listener
            return object : TextToSpeech(appContext, TextToSpeech.OnInitListener { }) {
                override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?): Int {
                    capturedProgressListener = listener
                    return TextToSpeech.SUCCESS
                }
            }
        }
    }

    @Test
    fun `platform onStop is not surfaced as an utterance error`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val engine = CapturingEngine(context)
        runBlocking {
            val init = launch { engine.initialize(null) }
            yield()
            assertNotNull("initialize must create a TTS instance", engine.pendingInitListener)
            engine.pendingInitListener!!.onInit(TextToSpeech.SUCCESS)
            init.join()
        }
        assertNotNull("engine must install an utterance progress listener", engine.capturedProgressListener)

        var errorCalls = 0
        engine.setProgressListener(
            object : NovelTtsPlaybackProgressListener {
                override fun onUtteranceError(utteranceId: String) {
                    errorCalls++
                }
            },
        )
        val listener = engine.capturedProgressListener!!

        // Plumbing probe: a direct platform error callback must reach the progress listener.
        listener.onError("probe", TextToSpeech.ERROR)
        assertEquals("direct onError must forward to the progress listener", 1, errorCalls)

        // The AOSP default of UtteranceProgressListener.onStop calls onError(utteranceId), and
        // every user-initiated pause/skip/stop flushes the active utterance. Without an explicit
        // onStop override each of those normal controls painted a persistent speak-error banner.
        listener.onStop("utterance-1", true)
        assertEquals("onStop must not be forwarded as an utterance error", 1, errorCalls)

        // Robolectric does not execute the framework default body above, so pin the override
        // structurally: onStop must resolve to the engine's own listener, not to the platform
        // base class whose default routes into onError on real devices.
        val stopMethod = listener.javaClass.getMethod(
            "onStop",
            String::class.java,
            Boolean::class.javaPrimitiveType,
        )
        assertEquals(
            "engine listener must override onStop (the platform default reports it as onError)",
            listener.javaClass,
            stopMethod.declaringClass,
        )
    }
}
