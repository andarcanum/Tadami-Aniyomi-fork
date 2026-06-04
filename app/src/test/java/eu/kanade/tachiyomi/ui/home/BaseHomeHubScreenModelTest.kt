package eu.kanade.tachiyomi.ui.home

import android.content.ContentResolver
import android.content.Context
import eu.kanade.domain.ui.UserProfilePreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.aniyomi.AYMR
import java.io.ByteArrayInputStream

class BaseHomeHubScreenModelTest {

    private lateinit var prefs: UserProfilePreferences
    private lateinit var screenModel: TestHomeHubScreenModel
    private lateinit var mockContext: Context
    private val initialState = HomeHubUiState(
        userName = "",
        userAvatar = "",
        greeting = AYMR.strings.aurora_welcome_back,
        greetingReady = false,
        isLoading = false,
        showWelcome = true,
    )

    @BeforeEach
    fun setup() {
        prefs = UserProfilePreferences(MutablePreferenceStore())
        mockContext = mockk(relaxed = true)
        screenModel = TestHomeHubScreenModel(initialState, prefs, mockContext)
    }

    @Test
    fun `updateUserName updates state, updates preference, marks name as edited, and writes to cache`() {
        prefs.name().get() shouldBe ""
        prefs.nameEdited().get() shouldBe false

        screenModel.updateUserName("Test User")

        screenModel.state.value.userName shouldBe "Test User"
        prefs.name().get() shouldBe "Test User"
        prefs.nameEdited().get() shouldBe true
        screenModel.cachedName shouldBe "Test User"
    }

    @Test
    fun `updateUserAvatar copies file, updates state, updates preference, and writes to cache`() {
        io.mockk.mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        every { android.net.Uri.parse(any()) } returns mockUri

        val mockResolver = mockk<ContentResolver>(relaxed = true)
        val mockInputStream = ByteArrayInputStream("dummy image data".toByteArray())

        every { mockContext.contentResolver } returns mockResolver
        every { mockResolver.openInputStream(any()) } returns mockInputStream

        val tempDir = java.io.File(System.getProperty("java.io.tmpdir")!!)
        every { mockContext.filesDir } returns tempDir

        screenModel.updateUserAvatar("content://media/external/images/media/1")

        val expectedPath = java.io.File(tempDir, "user_avatar.jpg").absolutePath
        screenModel.state.value.userAvatar shouldBe expectedPath
        prefs.avatarUrl().get() shouldBe expectedPath
        screenModel.cachedAvatar shouldBe expectedPath

        io.mockk.unmockkStatic(android.net.Uri::class)
    }

    @Test
    fun `resolveAndSetGreeting resolves greeting and updates state`() = kotlinx.coroutines.test.runTest {
        mockkObject(HomeGreetingSession)
        val expectedGreeting = AYMR.strings.aurora_greeting_ready
        coEvery {
            HomeGreetingSession.resolveGreeting(any(), any(), any(), any())
        } returns GreetingProvider.GreetingSelection(expectedGreeting, "ready", "scenario")

        screenModel.state.value.greetingReady shouldBe false

        screenModel.resolveAndSetGreeting()

        screenModel.state.value.greetingReady shouldBe true
        screenModel.state.value.greeting shouldBe expectedGreeting

        unmockkObject(HomeGreetingSession)
    }

    internal class TestHomeHubScreenModel(
        initialState: HomeHubUiState,
        userProfilePreferences: UserProfilePreferences,
        context: Context,
    ) : BaseHomeHubScreenModel(context, initialState, userProfilePreferences) {
        override val avatarFileName: String = "user_avatar.jpg"
        var cachedName: String? = null
        var cachedAvatar: String? = null

        override fun updateCacheUserName(name: String) {
            cachedName = name
        }

        override fun updateCacheUserAvatar(path: String) {
            cachedAvatar = path
        }
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
