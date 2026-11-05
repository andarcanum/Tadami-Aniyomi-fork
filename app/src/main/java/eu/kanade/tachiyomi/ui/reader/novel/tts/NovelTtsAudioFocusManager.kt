package eu.kanade.tachiyomi.ui.reader.novel.tts

enum class NovelTtsAudioFocusChange {
    GAIN,
    LOSS,
    LOSS_TRANSIENT,
    LOSS_TRANSIENT_CAN_DUCK,
}

interface NovelTtsAudioFocusBridge {
    fun requestAudioFocus(onFocusChange: (NovelTtsAudioFocusChange) -> Unit): Boolean

    fun abandonAudioFocus()

    fun registerHeadsetDisconnectListener(onDisconnect: () -> Unit)

    fun unregisterHeadsetDisconnectListener()
}

interface NovelTtsAudioFocusController {
    fun requestPlaybackFocus(): Boolean

    fun abandonPlaybackFocus()
}

class NovelTtsAudioFocusManager(
    private val bridge: NovelTtsAudioFocusBridge,
    private val onPauseRequested: () -> Unit,
) : NovelTtsAudioFocusController {
    private var hasPlaybackFocus = false

    override fun requestPlaybackFocus(): Boolean {
        // Re-requesting with a new platform listener replaces this app's current focus owner.
        // Android then reports focus loss to the replaced listener, which pauses the utterance
        // that a skip action has just started.
        if (hasPlaybackFocus) return true

        val granted = bridge.requestAudioFocus(::handleFocusChange)
        hasPlaybackFocus = granted
        if (granted) {
            bridge.registerHeadsetDisconnectListener(onPauseRequested)
        }
        return granted
    }

    override fun abandonPlaybackFocus() {
        hasPlaybackFocus = false
        bridge.unregisterHeadsetDisconnectListener()
        bridge.abandonAudioFocus()
    }

    private fun handleFocusChange(change: NovelTtsAudioFocusChange) {
        when (change) {
            NovelTtsAudioFocusChange.LOSS,
            NovelTtsAudioFocusChange.LOSS_TRANSIENT,
            NovelTtsAudioFocusChange.LOSS_TRANSIENT_CAN_DUCK,
            -> {
                hasPlaybackFocus = false
                onPauseRequested()
            }
            NovelTtsAudioFocusChange.GAIN -> hasPlaybackFocus = true
        }
    }
}
