package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

class NovelPluginWebViewCoordinatorTest {

    private lateinit var keyValueStore: InMemoryKeyValueStore
    private lateinit var coordinator: NovelPluginWebViewCoordinator
    private val pluginId = "test-plugin-789"

    @BeforeEach
    fun setUp() {
        keyValueStore = InMemoryKeyValueStore()
        coordinator = NovelPluginWebViewCoordinator(keyValueStore)
    }

    @Test
    fun `registerSession creates session with auto-generated ID`() = runBlocking {
        val sessionId = coordinator.registerSession(pluginId)

        sessionId.shouldNotBe("")
        sessionId.shouldContain(pluginId)
        coordinator.hasActiveSession(pluginId) shouldBe true
    }

    @Test
    fun `registerSession creates session with explicit ID`() = runBlocking {
        val explicitId = "my-session-123"
        val sessionId = coordinator.registerSession(pluginId, explicitId)

        sessionId shouldBe explicitId
        coordinator.hasActiveSession(pluginId) shouldBe true
    }

    @Test
    fun `registerSession supports multiple sessions per plugin`() = runBlocking {
        val session1 = coordinator.registerSession(pluginId)
        val session2 = coordinator.registerSession(pluginId)

        session1 shouldNotBe session2
        coordinator.getActiveSessions(pluginId) shouldContainExactly setOf(session1, session2)
    }

    @Test
    fun `unregisterSession removes session`() = runBlocking {
        val sessionId = coordinator.registerSession(pluginId)

        coordinator.unregisterSession(sessionId)

        coordinator.hasActiveSession(pluginId) shouldBe false
        coordinator.getActiveSessions(pluginId).isEmpty() shouldBe true
    }

    @Test
    fun `unregisterSession ignores unknown session ID`() = runBlocking {
        coordinator.registerSession(pluginId)

        coordinator.unregisterSession("unknown-session")

        coordinator.hasActiveSession(pluginId) shouldBe true
    }

    @Test
    fun `hasActiveSession returns false when no sessions`() = runBlocking {
        coordinator.hasActiveSession(pluginId) shouldBe false
    }

    @Test
    fun `hasActiveSession returns false for different plugin`() = runBlocking {
        coordinator.registerSession("other-plugin")

        coordinator.hasActiveSession(pluginId) shouldBe false
    }

    @Test
    fun `getActiveSessions returns empty set when no sessions`() = runBlocking {
        coordinator.getActiveSessions(pluginId).isEmpty() shouldBe true
    }

    @Test
    fun `getActiveSessions returns only sessions for specified plugin`() = runBlocking {
        val session1 = coordinator.registerSession(pluginId)
        coordinator.registerSession("other-plugin")
        val session3 = coordinator.registerSession(pluginId)

        coordinator.getActiveSessions(pluginId) shouldContainExactly setOf(session1, session3)
    }

    @Test
    fun `getStorageBridge returns bridge for plugin`() {
        val bridge = coordinator.getStorageBridge(pluginId)

        bridge shouldNotBe null
    }

    @Test
    fun `getStorageBridge returns same bridge for same plugin`() {
        val bridge1 = coordinator.getStorageBridge(pluginId)
        val bridge2 = coordinator.getStorageBridge(pluginId)

        bridge1 shouldBe bridge2
    }

    @Test
    fun `getStorageBridge returns different bridges for different plugins`() {
        val bridge1 = coordinator.getStorageBridge(pluginId)
        val bridge2 = coordinator.getStorageBridge("other-plugin")

        bridge1 shouldNotBe bridge2
    }

    @Test
    fun `syncAfterAuthFlow stores web storage data in localStorage`() = runBlocking {
        val authData = mapOf(
            "auth_token" to "abc123",
            "refresh_token" to "xyz789",
        )

        coordinator.syncAfterAuthFlow(pluginId, authData)

        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageGet("auth_token") shouldBe "abc123"
        bridge.localStorageGet("refresh_token") shouldBe "xyz789"
    }

    @Test
    fun `syncAfterAuthFlow overwrites existing values`() = runBlocking {
        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageSet("auth_token", "old_token")

        coordinator.syncAfterAuthFlow(pluginId, mapOf("auth_token" to "new_token"))

        bridge.localStorageGet("auth_token") shouldBe "new_token"
    }

    @Test
    fun `syncAfterAuthFlow removes missing values`() = runBlocking {
        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageSet("old_key", "old_value")

        coordinator.syncAfterAuthFlow(pluginId, mapOf("new_key" to "new_value"))

        bridge.localStorageGet("old_key").shouldBeNull()
        bridge.localStorageGet("new_key") shouldBe "new_value"
    }

    @Test
    fun `syncAfterAuthFlow handles empty data`() = runBlocking {
        coordinator.syncAfterAuthFlow(pluginId, emptyMap())

        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageKeys().isEmpty() shouldBe true
    }

    @Test
    fun `syncAfterPageMutation stores localStorage data`() = runBlocking {
        val localData = mapOf("key1" to "value1", "key2" to "value2")

        coordinator.syncAfterPageMutation(pluginId, localData)

        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageGet("key1") shouldBe "value1"
        bridge.localStorageGet("key2") shouldBe "value2"
    }

    @Test
    fun `syncAfterPageMutation stores sessionStorage data`() = runBlocking {
        val sessionData = mapOf("session_key" to "session_value")

        coordinator.syncAfterPageMutation(
            pluginId,
            localStorageData = emptyMap(),
            sessionStorageData = sessionData,
        )

        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.sessionStorageGet("session_key") shouldBe "session_value"
    }

    @Test
    fun `syncAfterPageMutation stores both localStorage and sessionStorage`() = runBlocking {
        val localData = mapOf("local_key" to "local_value")
        val sessionData = mapOf("session_key" to "session_value")

        coordinator.syncAfterPageMutation(pluginId, localData, sessionData)

        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageGet("local_key") shouldBe "local_value"
        bridge.sessionStorageGet("session_key") shouldBe "session_value"
    }

    @Test
    fun `syncAfterPageMutation handles null sessionStorage`() = runBlocking {
        val localData = mapOf("key" to "value")

        coordinator.syncAfterPageMutation(pluginId, localData, null)

        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageGet("key") shouldBe "value"
    }

    @Test
    fun `syncAfterPageMutation removes missing local and session values`() = runBlocking {
        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageSet("old_local", "old_local_value")
        bridge.sessionStorageSet("old_session", "old_session_value")

        coordinator.syncAfterPageMutation(
            pluginId,
            localStorageData = mapOf("new_local" to "new_local_value"),
            sessionStorageData = mapOf("new_session" to "new_session_value"),
        )

        bridge.localStorageGet("old_local").shouldBeNull()
        bridge.localStorageGet("new_local") shouldBe "new_local_value"
        bridge.sessionStorageGet("old_session").shouldBeNull()
        bridge.sessionStorageGet("new_session") shouldBe "new_session_value"
    }

    @Test
    fun `syncBeforeParsing returns snapshot with localStorage`() = runBlocking {
        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageSet("key1", "value1")
        bridge.localStorageSet("key2", "value2")

        val snapshot = coordinator.syncBeforeParsing(pluginId)

        snapshot.localStorage shouldContainExactly mapOf("key1" to "value1", "key2" to "value2")
    }

    @Test
    fun `syncBeforeParsing returns snapshot with sessionStorage`() = runBlocking {
        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.sessionStorageSet("session_key", "session_value")

        val snapshot = coordinator.syncBeforeParsing(pluginId)

        snapshot.sessionStorage shouldContainExactly mapOf("session_key" to "session_value")
    }

    @Test
    fun `syncBeforeParsing returns snapshot with both storage types`() = runBlocking {
        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageSet("local_key", "local_value")
        bridge.sessionStorageSet("session_key", "session_value")

        val snapshot = coordinator.syncBeforeParsing(pluginId)

        snapshot.localStorage shouldContainExactly mapOf("local_key" to "local_value")
        snapshot.sessionStorage shouldContainExactly mapOf("session_key" to "session_value")
    }

    @Test
    fun `syncBeforeParsing returns snapshot with timestamps`() = runBlocking {
        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageSet("key", "value")
        bridge.sessionStorageSet("skey", "svalue")

        val snapshot = coordinator.syncBeforeParsing(pluginId)

        snapshot.localSyncTimestamp shouldNotBe 0L
        snapshot.sessionSyncTimestamp shouldNotBe 0L
    }

    @Test
    fun `syncBeforeParsing returns empty snapshot when no data`() = runBlocking {
        val snapshot = coordinator.syncBeforeParsing(pluginId)

        snapshot.localStorage.isEmpty() shouldBe true
        snapshot.sessionStorage.isEmpty() shouldBe true
    }

    @Test
    fun `generateStorageInjectionScript creates localStorage setItem calls`() {
        val snapshot = NovelPluginWebViewCoordinator.StorageSnapshot(
            localStorage = mapOf("key1" to "value1"),
            sessionStorage = emptyMap(),
            localSyncTimestamp = 0L,
            sessionSyncTimestamp = 0L,
        )

        val script = coordinator.generateStorageInjectionScript(snapshot)

        script.shouldContain("localStorage.setItem(\"key1\", \"value1\")")
    }

    @Test
    fun `generateStorageInjectionScript creates sessionStorage setItem calls`() {
        val snapshot = NovelPluginWebViewCoordinator.StorageSnapshot(
            localStorage = emptyMap(),
            sessionStorage = mapOf("skey" to "svalue"),
            localSyncTimestamp = 0L,
            sessionSyncTimestamp = 0L,
        )

        val script = coordinator.generateStorageInjectionScript(snapshot)

        script.shouldContain("sessionStorage.setItem(\"skey\", \"svalue\")")
    }

    @Test
    fun `generateStorageInjectionScript escapes special characters`() {
        val snapshot = NovelPluginWebViewCoordinator.StorageSnapshot(
            localStorage = mapOf("key" to "value with \"quotes\" and \nnewline"),
            sessionStorage = emptyMap(),
            localSyncTimestamp = 0L,
            sessionSyncTimestamp = 0L,
        )

        val script = coordinator.generateStorageInjectionScript(snapshot)

        script.shouldContain("localStorage.setItem(\"key\", \"value with \\\"quotes\\\" and \\nnewline\")")
    }

    @Test
    fun `generateStorageInjectionScript wraps in IIFE`() {
        val snapshot = NovelPluginWebViewCoordinator.StorageSnapshot(
            localStorage = mapOf("key" to "value"),
            sessionStorage = emptyMap(),
            localSyncTimestamp = 0L,
            sessionSyncTimestamp = 0L,
        )

        val script = coordinator.generateStorageInjectionScript(snapshot)

        script.shouldContain("(function()")
        script.shouldContain("})();")
    }

    @Test
    fun `generateStorageInjectionScript handles empty snapshot`() {
        val snapshot = NovelPluginWebViewCoordinator.StorageSnapshot(
            localStorage = emptyMap(),
            sessionStorage = emptyMap(),
            localSyncTimestamp = 0L,
            sessionSyncTimestamp = 0L,
        )

        val script = coordinator.generateStorageInjectionScript(snapshot)

        script.shouldContain("(function()")
        script.shouldContain("localStorage.clear()")
        script.shouldContain("sessionStorage.clear()")
        script.shouldNotContain("setItem")
    }

    @Test
    fun `generateStorageInjectionScript clears before repopulating storage`() {
        val snapshot = NovelPluginWebViewCoordinator.StorageSnapshot(
            localStorage = mapOf("key1" to "value1"),
            sessionStorage = mapOf("skey" to "svalue"),
            localSyncTimestamp = 0L,
            sessionSyncTimestamp = 0L,
        )

        val script = coordinator.generateStorageInjectionScript(snapshot)

        script.shouldContain("localStorage.clear()")
        script.shouldContain("sessionStorage.clear()")
        script.shouldContain("localStorage.setItem(\"key1\", \"value1\")")
        script.shouldContain("sessionStorage.setItem(\"skey\", \"svalue\")")
    }

    @Test
    fun `cleanup removes all sessions for plugin`() = runBlocking {
        coordinator.registerSession(pluginId)
        coordinator.registerSession(pluginId)
        coordinator.registerSession("other-plugin")

        coordinator.cleanup(pluginId)

        coordinator.hasActiveSession(pluginId) shouldBe false
        coordinator.hasActiveSession("other-plugin") shouldBe true
    }

    @Test
    fun `cleanup removes storage bridge for plugin`() = runBlocking {
        val bridge = coordinator.getStorageBridge(pluginId)
        bridge.localStorageSet("key", "value")

        coordinator.cleanup(pluginId)

        val newBridge = coordinator.getStorageBridge(pluginId)
        newBridge.localStorageGet("key").shouldBeNull()
    }

    @Test
    fun `cleanupAll removes all sessions`() = runBlocking {
        coordinator.registerSession(pluginId)
        coordinator.registerSession("other-plugin")

        coordinator.cleanupAll()

        coordinator.hasActiveSession(pluginId) shouldBe false
        coordinator.hasActiveSession("other-plugin") shouldBe false
    }

    @Test
    fun `cleanupAll removes all storage bridges`() = runBlocking {
        val bridge1 = coordinator.getStorageBridge(pluginId)
        val bridge2 = coordinator.getStorageBridge("other-plugin")
        bridge1.localStorageSet("key1", "value1")
        bridge2.localStorageSet("key2", "value2")

        coordinator.cleanupAll()

        val newBridge1 = coordinator.getStorageBridge(pluginId)
        val newBridge2 = coordinator.getStorageBridge("other-plugin")
        newBridge1.localStorageGet("key1").shouldBeNull()
        newBridge2.localStorageGet("key2").shouldBeNull()
    }

    @Test
    fun `WebViewSession stores properties correctly`() {
        val session = NovelPluginWebViewCoordinator.WebViewSession(
            pluginId = "test-plugin",
            sessionId = "session-123",
            createdAt = 1234567890L,
        )

        session.pluginId shouldBe "test-plugin"
        session.sessionId shouldBe "session-123"
        session.createdAt shouldBe 1234567890L
    }

    @Test
    fun `StorageSnapshot stores properties correctly`() {
        val snapshot = NovelPluginWebViewCoordinator.StorageSnapshot(
            localStorage = mapOf("lkey" to "lvalue"),
            sessionStorage = mapOf("skey" to "svalue"),
            localSyncTimestamp = 100L,
            sessionSyncTimestamp = 200L,
        )

        snapshot.localStorage shouldContainExactly mapOf("lkey" to "lvalue")
        snapshot.sessionStorage shouldContainExactly mapOf("skey" to "svalue")
        snapshot.localSyncTimestamp shouldBe 100L
        snapshot.sessionSyncTimestamp shouldBe 200L
    }

    private class InMemoryKeyValueStore : NovelPluginKeyValueStore {
        private val store = mutableMapOf<String, MutableMap<String, String>>()

        override fun get(pluginId: String, key: String): String? {
            return store[pluginId]?.get(key)
        }

        override fun set(pluginId: String, key: String, value: String) {
            store.getOrPut(pluginId) { mutableMapOf() }[key] = value
        }

        override fun remove(pluginId: String, key: String) {
            store[pluginId]?.remove(key)
        }

        override fun clear(pluginId: String) {
            store.remove(pluginId)
        }

        override fun clearAll() {
            store.clear()
        }

        override fun keys(pluginId: String): Set<String> {
            return store[pluginId]?.keys ?: emptySet()
        }
    }
}
