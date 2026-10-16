@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.tachiyomi.ui.home

import android.content.ContentResolver
import android.content.Context
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cafe.adriel.voyager.core.model.ScreenModelStore
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.ui.discovery.manualRefreshWorkInfoFlow
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.i18n.aniyomi.AYMR
import java.io.ByteArrayInputStream
import java.util.UUID

class BaseHomeHubScreenModelTest {

    @OptIn(
        kotlinx.coroutines.ExperimentalCoroutinesApi::class,
        cafe.adriel.voyager.core.annotation.InternalVoyagerApi::class,
    )
    @Test
    fun `failures received before UI collection are retained independently per media`() =
        kotlinx.coroutines.runBlocking {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            val holderKey = UUID.randomUUID().toString()
            ScreenModelStore.getOrPut(holderKey, tag = null) { screenModel }
            val preferences = DiscoveryPreferences(PersistingPreferenceStore())
            val animeId = UUID.randomUUID()
            val novelId = UUID.randomUUID()
            preferences.manualRefreshWorkId(DiscoveryMediaType.ANIME).set(animeId.toString())
            preferences.manualRefreshWorkId(DiscoveryMediaType.NOVEL).set(novelId.toString())
            val animeWork = MutableStateFlow<WorkInfo?>(null)
            val novelWork = MutableStateFlow<WorkInfo?>(null)
            val manager = mockk<WorkManager>()
            io.mockk.mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
            io.mockk.mockkStatic("eu.kanade.tachiyomi.ui.discovery.DiscoveryFeedScreenModelKt")
            every { mockContext.workManager } returns manager
            every { manualRefreshWorkInfoFlow(manager, preferences, DiscoveryMediaType.ANIME) } returns animeWork
            every { manualRefreshWorkInfoFlow(manager, preferences, DiscoveryMediaType.NOVEL) } returns novelWork
            try {
                screenModel.observeFailureForTest(preferences, DiscoveryMediaType.ANIME)
                screenModel.observeFailureForTest(preferences, DiscoveryMediaType.NOVEL)
                animeWork.value = WorkInfo(animeId, WorkInfo.State.FAILED, emptySet(), Data.EMPTY)
                withTimeout(5_000) {
                    screenModel.discoveryRefreshFailures.first { it[DiscoveryMediaType.ANIME] == animeId.toString() }
                }
                novelWork.value = WorkInfo(novelId, WorkInfo.State.FAILED, emptySet(), Data.EMPTY)
                withTimeout(5_000) {
                    screenModel.discoveryRefreshFailures.first { it[DiscoveryMediaType.NOVEL] == novelId.toString() }
                }

                val pending = withTimeout(5_000) { screenModel.discoveryRefreshFailures.first() }
                pending shouldBe mapOf(
                    DiscoveryMediaType.ANIME to animeId.toString(),
                    DiscoveryMediaType.NOVEL to novelId.toString(),
                )
                pending.forEach { (mediaType, requestId) ->
                    preferences.claimManualRefreshFailure(mediaType, requestId) shouldBe true
                    preferences.claimManualRefreshFailure(mediaType, requestId) shouldBe false
                    screenModel.acknowledgeDiscoveryRefreshFailure(mediaType, requestId)
                }
                screenModel.discoveryRefreshFailures.value shouldBe emptyMap()

                val nextAnimeId = UUID.randomUUID()
                preferences.manualRefreshWorkId(DiscoveryMediaType.ANIME).set(nextAnimeId.toString())
                animeWork.value = WorkInfo(nextAnimeId, WorkInfo.State.FAILED, emptySet(), Data.EMPTY)
                withTimeout(5_000) {
                    screenModel.discoveryRefreshFailures.first {
                        it[DiscoveryMediaType.ANIME] == nextAnimeId.toString()
                    }
                }
                // A late acknowledgement of an older failure cannot erase its successor.
                screenModel.acknowledgeDiscoveryRefreshFailure(DiscoveryMediaType.ANIME, animeId.toString())
                screenModel.discoveryRefreshFailures.value shouldBe
                    mapOf(DiscoveryMediaType.ANIME to nextAnimeId.toString())
                // Replacement before consumption makes this pending failure stale.
                preferences.manualRefreshWorkId(DiscoveryMediaType.ANIME).set(UUID.randomUUID().toString())
                preferences.claimManualRefreshFailure(DiscoveryMediaType.ANIME, nextAnimeId.toString()) shouldBe false
                screenModel.acknowledgeDiscoveryRefreshFailure(DiscoveryMediaType.ANIME, nextAnimeId.toString())
                screenModel.discoveryRefreshFailures.value shouldBe emptyMap()

                val nextNovelId = UUID.randomUUID()
                preferences.manualRefreshWorkId(DiscoveryMediaType.NOVEL).set(nextNovelId.toString())
                // A full-screen consumer can handle the UUID before Home drains it.
                preferences.claimManualRefreshFailure(DiscoveryMediaType.NOVEL, nextNovelId.toString()) shouldBe true
                novelWork.value = WorkInfo(nextNovelId, WorkInfo.State.FAILED, emptySet(), Data.EMPTY)
                val alreadyClaimed = withTimeout(5_000) {
                    screenModel.discoveryRefreshFailures.first {
                        it[DiscoveryMediaType.NOVEL] == nextNovelId.toString()
                    }
                }
                alreadyClaimed.forEach { (mediaType, requestId) ->
                    preferences.claimManualRefreshFailure(mediaType, requestId) shouldBe false
                    screenModel.acknowledgeDiscoveryRefreshFailure(mediaType, requestId)
                }
                screenModel.discoveryRefreshFailures.value shouldBe emptyMap()
            } finally {
                ScreenModelStore.onDisposeNavigator(holderKey)
                io.mockk.unmockkAll()
                Dispatchers.resetMain()
            }
            Unit
        }

    @OptIn(
        kotlinx.coroutines.ExperimentalCoroutinesApi::class,
        cafe.adriel.voyager.core.annotation.InternalVoyagerApi::class,
    )
    @Test
    fun `departing collector leaves failed request available to another surface and Home reentry`() =
        kotlinx.coroutines.runBlocking {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            val holderKey = UUID.randomUUID().toString()
            ScreenModelStore.getOrPut(holderKey, tag = null) { screenModel }
            val preferences = DiscoveryPreferences(PersistingPreferenceStore())
            val media = DiscoveryMediaType.NOVEL
            val request = UUID.randomUUID()
            preferences.manualRefreshWorkId(media).set(request.toString())
            val work = MutableStateFlow<WorkInfo?>(null)
            val manager = mockk<WorkManager>()
            io.mockk.mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
            io.mockk.mockkStatic("eu.kanade.tachiyomi.ui.discovery.DiscoveryFeedScreenModelKt")
            every { mockContext.workManager } returns manager
            every { manualRefreshWorkInfoFlow(manager, preferences, media) } returns work
            val observed = CompletableDeferred<Map<DiscoveryMediaType, String>>()
            val consume = CompletableDeferred<Unit>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                screenModel.discoveryRefreshFailures.collect {
                    if (it.containsKey(media)) {
                        observed.complete(it)
                        consume.await()
                    }
                }
            }
            try {
                screenModel.observeFailureForTest(preferences, media)
                work.value = WorkInfo(request, WorkInfo.State.FAILED, emptySet(), Data.EMPTY)
                withTimeout(5_000) { observed.await() }
                collector.cancelAndJoin()

                // A full-screen consumer must still be able to claim after Home leaves before handling.
                preferences.claimManualRefreshFailure(media, request.toString()) shouldBe true
                val replayed = withTimeout(5_000) { screenModel.discoveryRefreshFailures.first() }
                replayed shouldBe mapOf(media to request.toString())
                preferences.claimManualRefreshFailure(media, replayed.getValue(media)) shouldBe false
                screenModel.acknowledgeDiscoveryRefreshFailure(media, replayed.getValue(media))
                screenModel.discoveryRefreshFailures.value shouldBe emptyMap()

                // Home can consume a later unclaimed failure after departure and reentry.
                val nextRequest = UUID.randomUUID()
                preferences.manualRefreshWorkId(media).set(nextRequest.toString())
                work.value = WorkInfo(nextRequest, WorkInfo.State.FAILED, emptySet(), Data.EMPTY)
                val pending = withTimeout(5_000) {
                    screenModel.discoveryRefreshFailures.first { it[media] == nextRequest.toString() }
                }
                val onReentry = withTimeout(5_000) { screenModel.discoveryRefreshFailures.first() }
                onReentry shouldBe pending
                preferences.claimManualRefreshFailure(media, onReentry.getValue(media)) shouldBe true
                preferences.claimManualRefreshFailure(media, onReentry.getValue(media)) shouldBe false
                screenModel.acknowledgeDiscoveryRefreshFailure(media, onReentry.getValue(media))
                screenModel.discoveryRefreshFailures.value shouldBe emptyMap()
            } finally {
                collector.cancelAndJoin()
                ScreenModelStore.onDisposeNavigator(holderKey)
                io.mockk.unmockkAll()
                Dispatchers.resetMain()
            }
            Unit
        }

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

    // runBlocking, not runTest: this body is synchronous, and runTest additionally fails on any
    // uncaught exception raised anywhere in the JVM before it starts - including background
    // collectors leaked by other test classes, which has nothing to do with this assertion.
    @Test
    fun `resolveAndSetGreeting resolves greeting and updates state`() = kotlinx.coroutines.runBlocking {
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

    @Test
    fun `emitted effect reaches an active collector`() = kotlinx.coroutines.runBlocking {
        val received = CompletableDeferred<HomeHubEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            screenModel.effects.collect { received.complete(it) }
        }

        screenModel.emitForTest(HomeHubEffect.DiscoveryPoolLimited)

        received.await() shouldBe HomeHubEffect.DiscoveryPoolLimited
        collector.cancel()
    }

    @Test
    fun `effect emitted with no subscriber is not replayed to a late collector`() {
        kotlinx.coroutines.runBlocking {
            screenModel.emitForTest(HomeHubEffect.DiscoveryPoolLimited)

            withTimeoutOrNull(100) { screenModel.effects.first() } shouldBe null
        }
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

        fun emitForTest(effect: HomeHubEffect) = emitEffect(effect)

        fun observeFailureForTest(preferences: DiscoveryPreferences, mediaType: DiscoveryMediaType) =
            observeDiscoveryRefreshFailure(preferences, mediaType)
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
