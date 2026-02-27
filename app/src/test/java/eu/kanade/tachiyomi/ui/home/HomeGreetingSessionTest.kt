package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.UserProfilePreferences
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.aniyomi.AYMR
import java.util.concurrent.atomic.AtomicInteger

class HomeGreetingSessionTest {

    private lateinit var prefs: UserProfilePreferences

    @BeforeEach
    fun setup() {
        HomeGreetingSession.resetForTests()
        prefs = UserProfilePreferences(MutablePreferenceStore())
    }

    @AfterEach
    fun tearDown() {
        HomeGreetingSession.resetForTests()
    }

    @Test
    fun `resolveGreeting updates preference history only once per session`() = runTest {
        val callCount = AtomicInteger(0)
        val selector = HomeGreetingSelector { request ->
            callCount.incrementAndGet()
            GreetingProvider.GreetingSelection(
                greeting = AYMR.strings.aurora_greeting_ready,
                greetingId = "ready_${request.totalLaunches}",
                scenarioId = "general",
            )
        }

        val first = HomeGreetingSession.resolveGreeting(
            userProfilePreferences = prefs,
            nowMillis = { 1_700_000_000_000L },
            selector = selector,
        )
        val second = HomeGreetingSession.resolveGreeting(
            userProfilePreferences = prefs,
            nowMillis = { 1_700_000_000_100L },
            selector = selector,
        )

        first.greetingId shouldBe second.greetingId
        callCount.get() shouldBe 1
        prefs.totalLaunches().get() shouldBe 1L
        prefs.getRecentGreetingHistory() shouldContainExactly listOf(first.greetingId)
        prefs.getRecentScenarioHistory() shouldContainExactly listOf(first.scenarioId)
    }

    @Test
    fun `resolveGreeting is race-safe for parallel calls`() = runTest {
        val callCount = AtomicInteger(0)
        val selector = HomeGreetingSelector {
            callCount.incrementAndGet()
            GreetingProvider.GreetingSelection(
                greeting = AYMR.strings.aurora_greeting_ready,
                greetingId = "ready_once",
                scenarioId = "general",
            )
        }

        val results = List(10) {
            async {
                HomeGreetingSession.resolveGreeting(
                    userProfilePreferences = prefs,
                    nowMillis = { 1_700_000_000_000L },
                    selector = selector,
                )
            }
        }.awaitAll()

        results.map { it.greetingId }.toSet() shouldBe setOf("ready_once")
        callCount.get() shouldBe 1
        prefs.totalLaunches().get() shouldBe 1L
        prefs.getRecentGreetingHistory() shouldContainExactly listOf("ready_once")
    }

    @Test
    fun `resolveGreeting uses first in-flight selector result across concurrent callers`() = runTest {
        val selector = HomeGreetingSelector { request ->
            GreetingProvider.GreetingSelection(
                greeting = AYMR.strings.aurora_greeting_ready,
                greetingId = "ready_${request.achievementCount}",
                scenarioId = "scenario_${request.achievementCount}",
            )
        }

        val first = async {
            HomeGreetingSession.resolveGreeting(
                userProfilePreferences = prefs,
                stats = HomeGreetingStats(achievementCount = 1),
                selector = selector,
            )
        }
        val second = async {
            HomeGreetingSession.resolveGreeting(
                userProfilePreferences = prefs,
                stats = HomeGreetingStats(achievementCount = 99),
                selector = selector,
            )
        }

        val results = awaitAll(first, second)

        results.map { it.greetingId }.toSet().size shouldBe 1
        results.map { it.scenarioId }.toSet().size shouldBe 1
        prefs.totalLaunches().get() shouldBe 1L
        prefs.getRecentGreetingHistory().size shouldBe 1
        prefs.getRecentScenarioHistory().size shouldBe 1
    }

    @Test
    fun `resolveGreeting passes 24h blocked ids and records shown event`() = runTest {
        val now = 1_700_000_000_000L
        val hour = 60L * 60L * 1000L
        prefs.appendRecentGreetingEvent("blocked_recent", now - hour)

        var capturedBlocked: Set<String> = emptySet()
        val selection = HomeGreetingSession.resolveGreeting(
            userProfilePreferences = prefs,
            nowMillis = { now },
            selector = HomeGreetingSelector { request ->
                capturedBlocked = request.blockedGreetingIds
                GreetingProvider.GreetingSelection(
                    greeting = AYMR.strings.aurora_greeting_ready,
                    greetingId = "fresh_id",
                    scenarioId = "general",
                )
            },
        )

        selection.greetingId shouldBe "fresh_id"
        capturedBlocked.contains("blocked_recent") shouldBe true
        prefs.getGreetingIdsShownWithin(windowMs = 24L * hour, nowMillis = now).contains("fresh_id") shouldBe true
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
