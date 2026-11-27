package eu.kanade.presentation.easteregg.crystal

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.tadami.aurora.R

/** The original six-voice chime, preloaded locally and played only after an explicit start tap. */
internal class CrystalMemorialAudio(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val soundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()

    @Volatile private var ready = false

    @Volatile private var released = false
    private var stream = 0
    private val sound: Int

    init {
        soundPool.setOnLoadCompleteListener { _, _, status -> if (!released && status == 0) ready = true }
        sound = soundPool.load(context.applicationContext, R.raw.crystal_chime, 1)
    }

    fun play() {
        if (!ready || released || audioManager.isMusicActive ||
            audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        ) {
            return
        }
        stop()
        stream = soundPool.play(sound, 0.65f, 0.65f, 1, 0, 1f)
    }

    fun stop() {
        if (!released && stream != 0) soundPool.stop(stream)
        stream = 0
    }

    fun release() {
        if (released) return
        stop()
        released = true
        ready = false
        soundPool.setOnLoadCompleteListener(null)
        soundPool.release()
    }
}
