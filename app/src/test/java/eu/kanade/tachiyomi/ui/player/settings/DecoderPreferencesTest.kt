package eu.kanade.tachiyomi.ui.player.settings

import eu.kanade.tachiyomi.ui.player.Anime4KShaderPreset
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.DecoderPreset
import eu.kanade.tachiyomi.ui.player.MotionInterpolationMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class DecoderPreferencesTest {

    @Test
    fun `decoder preset defaults to device`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())

        prefs.decoderPreset().get() shouldBe DecoderPreset.Device
    }

    @Test
    fun `anime4k preset defaults to off`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())

        prefs.anime4kShaderPreset().get() shouldBe Anime4KShaderPreset.Off
    }

    @Test
    fun `motion interpolation defaults to off`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())

        prefs.motionInterpolationMode().get() shouldBe MotionInterpolationMode.Off
    }

    @Test
    fun `useYUV420P defaults to off`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())

        prefs.useYUV420P().get() shouldBe false
    }

    @Test
    fun `useZeroCopyHwdec defaults to off`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())

        prefs.useZeroCopyHwdec().get() shouldBe false
    }

    @Test
    fun `mid preset enables balanced shaders without motion interpolation`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())
        prefs.useZeroCopyHwdec().set(true)

        DecoderPreset.Mid.applyTo(prefs)

        prefs.tryHWDecoding().get() shouldBe true
        prefs.useZeroCopyHwdec().get() shouldBe false
        prefs.gpuNext().get() shouldBe true
        prefs.videoDebanding().get() shouldBe Debanding.GPU
        prefs.anime4kShaderPreset().get() shouldBe Anime4KShaderPreset.Balanced
        prefs.motionInterpolationMode().get() shouldBe MotionInterpolationMode.Off
        prefs.useYUV420P().get() shouldBe false
    }

    @Test
    fun `low preset disables all optional decoder features`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())

        DecoderPreset.Low.applyTo(prefs)

        prefs.tryHWDecoding().get() shouldBe true
        prefs.gpuNext().get() shouldBe false
        prefs.videoDebanding().get() shouldBe Debanding.None
        prefs.anime4kShaderPreset().get() shouldBe Anime4KShaderPreset.Off
        prefs.motionInterpolationMode().get() shouldBe MotionInterpolationMode.Off
        prefs.useYUV420P().get() shouldBe false
    }

    @Test
    fun `high preset enables the quality profile`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())

        DecoderPreset.High.applyTo(prefs)

        prefs.tryHWDecoding().get() shouldBe true
        prefs.gpuNext().get() shouldBe true
        prefs.videoDebanding().get() shouldBe Debanding.GPU
        prefs.anime4kShaderPreset().get() shouldBe Anime4KShaderPreset.Quality
        prefs.motionInterpolationMode().get() shouldBe MotionInterpolationMode.Always
        prefs.useYUV420P().get() shouldBe false
    }

    @Test
    fun `device preset keeps interpolation and yuv420p off`() {
        val prefs = DecoderPreferences(MutablePreferenceStore())

        DecoderPreset.Device.applyTo(prefs)

        prefs.motionInterpolationMode().get() shouldBe MotionInterpolationMode.Off
        prefs.useYUV420P().get() shouldBe false
    }

    private class MutablePreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, Any?>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            MutablePreference(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            MutablePreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            MutablePreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            MutablePreference(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            MutablePreference(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            MutablePreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = MutablePreference(key, defaultValue)

        override fun getAll(): Map<String, *> = values.toMap()

        private inner class MutablePreference<T>(
            private val keyName: String,
            private val default: T,
        ) : Preference<T> {
            private val state = MutableStateFlow(get())

            override fun key(): String = keyName

            @Suppress("UNCHECKED_CAST")
            override fun get(): T = values[keyName] as? T ?: default

            override fun set(value: T) {
                values[keyName] = value
                state.value = value
            }

            override fun isSet(): Boolean = values.containsKey(keyName)

            override fun delete() {
                values.remove(keyName)
                state.value = default
            }

            override fun defaultValue(): T = default

            override fun changes(): Flow<T> = state

            override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
        }
    }
}
