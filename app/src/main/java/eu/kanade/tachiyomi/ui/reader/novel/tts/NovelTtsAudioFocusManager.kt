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
    override fun requestPlaybackFocus(): Boolean {
        val granted = bridge.requestAudioFocus(::handleFocusChange)
        if (granted) {
            bridge.registerHeadsetDisconnectListener(onPauseRequested)
        }
        return granted
    }

    override fun abandonPlaybackFocus() {
        bridge.unregisterHeadsetDisconnectListener()
        bridge.abandonAudioFocus()
    }

    private fun handleFocusChange(change: NovelTtsAudioFocusChange) {
        when (change) {
            NovelTtsAudioFocusChange.LOSS,
            NovelTtsAudioFocusChange.LOSS_TRANSIENT,
            NovelTtsAudioFocusChange.LOSS_TRANSIENT_CAN_DUCK,
            -> onPauseRequested()
            NovelTtsAudioFocusChange.GAIN -> Unit
        }
    }
}
