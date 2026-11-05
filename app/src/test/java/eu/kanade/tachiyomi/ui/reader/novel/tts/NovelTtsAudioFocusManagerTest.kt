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
    fun `requesting playback focus again keeps the current focus owner`() {
        val events = mutableListOf<String>()
        val bridge = FakeAudioFocusBridge(
            requestResult = true,
            dispatchLossToReplacedOwner = true,
        )
        val manager = NovelTtsAudioFocusManager(
            bridge = bridge,
            onPauseRequested = { events += "pause" },
        )

        manager.requestPlaybackFocus() shouldBe true
        manager.requestPlaybackFocus() shouldBe true

        bridge.requestCalls shouldBe 1
        events shouldContainExactly emptyList()
    }

    @Test
    fun `focus can be requested again after focus loss`() {
        val events = mutableListOf<String>()
        val bridge = FakeAudioFocusBridge(requestResult = true)
        val manager = NovelTtsAudioFocusManager(
            bridge = bridge,
            onPauseRequested = { events += "pause" },
        )

        manager.requestPlaybackFocus()
        bridge.emitFocusChange(NovelTtsAudioFocusChange.LOSS_TRANSIENT)
        manager.requestPlaybackFocus()

        bridge.requestCalls shouldBe 2
        events shouldContainExactly listOf("pause")
    }

    @Test
    fun `focus can be requested again after it is abandoned`() {
        val bridge = FakeAudioFocusBridge(requestResult = true)
        val manager = NovelTtsAudioFocusManager(
            bridge = bridge,
            onPauseRequested = {},
        )

        manager.requestPlaybackFocus()
        manager.abandonPlaybackFocus()
        manager.requestPlaybackFocus()

        bridge.requestCalls shouldBe 2
        bridge.abandonCalls shouldBe 1
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
        private val dispatchLossToReplacedOwner: Boolean = false,
    ) : NovelTtsAudioFocusBridge {
        private var focusListener: ((NovelTtsAudioFocusChange) -> Unit)? = null
        private var headsetListener: (() -> Unit)? = null

        var requestCalls = 0
        var abandonCalls = 0

        override fun requestAudioFocus(onFocusChange: (NovelTtsAudioFocusChange) -> Unit): Boolean {
            requestCalls += 1
            val replacedListener = focusListener
            focusListener = onFocusChange
            if (dispatchLossToReplacedOwner) {
                replacedListener?.invoke(NovelTtsAudioFocusChange.LOSS)
            }
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
