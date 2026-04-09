package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsAudioFocusManagerTest {

    @Test
    fun `requestPlaybackFocus requests audio focus before playback`() {
        val bridge = FakeAudioFocusBridge(requestResult = true)
        val manager = NovelTtsAudioFocusManager(
            bridge = bridge,
            onPauseRequested = {},
        )

        val granted = manager.requestPlaybackFocus()

        granted shouldBe true
        bridge.requestCalls shouldBe 1
    }

    @Test
    fun `focus loss triggers pause callback`() {
        val events = mutableListOf<String>()
        val bridge = FakeAudioFocusBridge(requestResult = true)
        val manager = NovelTtsAudioFocusManager(
            bridge = bridge,
            onPauseRequested = { events += "pause" },
        )

        manager.requestPlaybackFocus()
        bridge.emitFocusChange(NovelTtsAudioFocusChange.LOSS_TRANSIENT)

        events shouldContainExactly listOf("pause")
    }

    @Test
    fun `headset disconnect triggers pause callback`() {
        val events = mutableListOf<String>()
        val bridge = FakeAudioFocusBridge(requestResult = true)
        val manager = NovelTtsAudioFocusManager(
            bridge = bridge,
            onPauseRequested = { events += "pause" },
        )

        manager.requestPlaybackFocus()
        bridge.emitHeadsetDisconnect()

        events shouldContainExactly listOf("pause")
    }

    private class FakeAudioFocusBridge(
        private val requestResult: Boolean,
    ) : NovelTtsAudioFocusBridge {
        private var focusListener: ((NovelTtsAudioFocusChange) -> Unit)? = null
        private var headsetListener: (() -> Unit)? = null

        var requestCalls = 0
        var abandonCalls = 0

        override fun requestAudioFocus(onFocusChange: (NovelTtsAudioFocusChange) -> Unit): Boolean {
            requestCalls += 1
            focusListener = onFocusChange
            return requestResult
        }

        override fun abandonAudioFocus() {
            abandonCalls += 1
        }

        override fun registerHeadsetDisconnectListener(onDisconnect: () -> Unit) {
            headsetListener = onDisconnect
        }

        override fun unregisterHeadsetDisconnectListener() {
            headsetListener = null
        }

        fun emitFocusChange(change: NovelTtsAudioFocusChange) {
            focusListener?.invoke(change)
        }

        fun emitHeadsetDisconnect() {
            headsetListener?.invoke()
        }
    }
}
