package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.HomeHeaderLayoutElement
import eu.kanade.domain.ui.model.HomeHeaderLayoutSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class UserProfilePreferencesTest {

    @Test
    fun `home header preferences have expected defaults`() {
        val prefs = UserProfilePreferences(InMemoryPreferenceStore())

        prefs.showHomeGreeting().get() shouldBe true
        prefs.showHomeStreak().get() shouldBe true
        prefs.homeHeaderNicknameAlignRight().get() shouldBe false
        prefs.homeHubLastSection().get() shouldBe "anime"
        prefs.greetingFont().get() shouldBe "default"
        prefs.greetingFontSize().get() shouldBe 12
        prefs.greetingColor().get() shouldBe "theme"
        prefs.greetingCustomColorHex().get() shouldBe "#FFFFFF"
        prefs.greetingDecoration().get() shouldBe "none"
        prefs.greetingItalic().get() shouldBe false
        prefs.homeHeaderLayoutJson().get() shouldBe ""
        prefs.getHomeHeaderLayoutOrDefault() shouldBe HomeHeaderLayoutSpec.default()
    }

    @Test
    fun `home header layout helpers round trip custom layout`() {
        val prefs = UserProfilePreferences(MutablePreferenceStore())
        val layout = HomeHeaderLayoutSpec.default().withPosition(
            HomeHeaderLayoutElement.Avatar,
            x = 1f,
            y = 4f,
        )

        prefs.setHomeHeaderLayout(layout)

        prefs.homeHeaderLayoutJson().get() shouldNotBe ""
        prefs.getHomeHeaderLayoutOrNull() shouldBe layout
        prefs.getHomeHeaderLayoutOrDefault() shouldBe layout
    }

    @Test
    fun `home header layout helpers fall back to default for invalid json`() {
        val prefs = UserProfilePreferences(InMemoryPreferenceStore())

        prefs.homeHeaderLayoutJson().set("{bad json")

        prefs.getHomeHeaderLayoutOrNull() shouldBe null
        prefs.getHomeHeaderLayoutOrDefault() shouldBe HomeHeaderLayoutSpec.default()
    }

    @Test
    fun `home header layout helpers migrate legacy grid json`() {
        val prefs = UserProfilePreferences(MutablePreferenceStore())

        prefs.homeHeaderLayoutJson().set(
            """
            {"version":1,"grid":{"columns":12,"rows":10},"elements":{"avatar":{"x":9,"y":2}}}
            """.trimIndent(),
        )

        val layout = prefs.getHomeHeaderLayoutOrNull()

        layout shouldNotBe null
        layout?.version shouldBe HomeHeaderLayoutSpec.CURRENT_VERSION
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
