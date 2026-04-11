package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

class NovelPluginWebStorageBridgeTest {

    private lateinit var keyValueStore: InMemoryKeyValueStore
    private lateinit var bridge: NovelPluginWebStorageBridge
    private val pluginId = "test-plugin-456"

    @BeforeEach
    fun setUp() {
        keyValueStore = InMemoryKeyValueStore()
        bridge = NovelPluginWebStorageBridge(pluginId, keyValueStore)
    }

    @Test
    fun `localStorageGet returns null for non-existent key`() {
        bridge.localStorageGet("missing").shouldBeNull()
    }

    @Test
    fun `localStorageSet stores value and localStorageGet retrieves it`() {
        bridge.localStorageSet("key1", "value1")
        bridge.localStorageGet("key1") shouldBe "value1"
    }

    @Test
    fun `localStorageSet overwrites existing value`() {
        bridge.localStorageSet("key", "old")
        bridge.localStorageSet("key", "new")
        bridge.localStorageGet("key") shouldBe "new"
    }

    @Test
    fun `localStorageRemove removes specific key`() {
        bridge.localStorageSet("key1", "value1")
        bridge.localStorageSet("key2", "value2")

        bridge.localStorageRemove("key1")

        bridge.localStorageGet("key1").shouldBeNull()
        bridge.localStorageGet("key2") shouldBe "value2"
    }

    @Test
    fun `localStorageClear removes all localStorage entries`() {
        bridge.localStorageSet("key1", "value1")
        bridge.localStorageSet("key2", "value2")

        bridge.localStorageClear()

        bridge.localStorageGet("key1").shouldBeNull()
        bridge.localStorageGet("key2").shouldBeNull()
    }

    @Test
    fun `localStorageClear does not affect sessionStorage`() {
        bridge.localStorageSet("lkey", "lvalue")
        bridge.sessionStorageSet("skey", "svalue")

        bridge.localStorageClear()

        bridge.sessionStorageGet("skey") shouldBe "svalue"
    }

    @Test
    fun `localStorageKeys returns only localStorage keys without prefix`() {
        bridge.localStorageSet("key1", "value1")
        bridge.localStorageSet("key2", "value2")

        val keys = bridge.localStorageKeys()
        keys shouldContainExactly setOf("key1", "key2")
    }

    @Test
    fun `localStorageKeys excludes sessionStorage keys`() {
        bridge.localStorageSet("lkey", "lvalue")
        bridge.sessionStorageSet("skey", "svalue")

        val keys = bridge.localStorageKeys()
        keys shouldContainExactly setOf("lkey")
    }

    @Test
    fun `sessionStorageGet returns null for non-existent key`() {
        bridge.sessionStorageGet("missing").shouldBeNull()
    }

    @Test
    fun `sessionStorageSet stores value and sessionStorageGet retrieves it`() {
        bridge.sessionStorageSet("key1", "value1")
        bridge.sessionStorageGet("key1") shouldBe "value1"
    }

    @Test
    fun `sessionStorageSet overwrites existing value`() {
        bridge.sessionStorageSet("key", "old")
        bridge.sessionStorageSet("key", "new")
        bridge.sessionStorageGet("key") shouldBe "new"
    }

    @Test
    fun `sessionStorageRemove removes specific key`() {
        bridge.sessionStorageSet("key1", "value1")
        bridge.sessionStorageSet("key2", "value2")

        bridge.sessionStorageRemove("key1")

        bridge.sessionStorageGet("key1").shouldBeNull()
        bridge.sessionStorageGet("key2") shouldBe "value2"
    }

    @Test
    fun `sessionStorageClear removes all sessionStorage entries`() {
        bridge.sessionStorageSet("key1", "value1")
        bridge.sessionStorageSet("key2", "value2")

        bridge.sessionStorageClear()

        bridge.sessionStorageGet("key1").shouldBeNull()
        bridge.sessionStorageGet("key2").shouldBeNull()
    }

    @Test
    fun `sessionStorageClear does not affect localStorage`() {
        bridge.localStorageSet("lkey", "lvalue")
        bridge.sessionStorageSet("skey", "svalue")

        bridge.sessionStorageClear()

        bridge.localStorageGet("lkey") shouldBe "lvalue"
    }

    @Test
    fun `sessionStorageKeys returns only sessionStorage keys`() {
        bridge.sessionStorageSet("key1", "value1")
        bridge.sessionStorageSet("key2", "value2")

        val keys = bridge.sessionStorageKeys()
        keys shouldContainExactly setOf("key1", "key2")
    }

    @Test
    fun `sessionStorageKeys excludes localStorage keys`() {
        bridge.localStorageSet("lkey", "lvalue")
        bridge.sessionStorageSet("skey", "svalue")

        val keys = bridge.sessionStorageKeys()
        keys shouldContainExactly setOf("skey")
    }

    @Test
    fun `different plugins have isolated localStorage`() {
        val bridge1 = NovelPluginWebStorageBridge("plugin-1", keyValueStore)
        val bridge2 = NovelPluginWebStorageBridge("plugin-2", keyValueStore)

        bridge1.localStorageSet("key", "value-1")
        bridge2.localStorageSet("key", "value-2")

        bridge1.localStorageGet("key") shouldBe "value-1"
        bridge2.localStorageGet("key") shouldBe "value-2"
    }

    @Test
    fun `different plugins have isolated sessionStorage`() {
        val bridge1 = NovelPluginWebStorageBridge("plugin-1", keyValueStore)
        val bridge2 = NovelPluginWebStorageBridge("plugin-2", keyValueStore)

        bridge1.sessionStorageSet("key", "value-1")
        bridge2.sessionStorageSet("key", "value-2")

        bridge1.sessionStorageGet("key") shouldBe "value-1"
        bridge2.sessionStorageGet("key") shouldBe "value-2"
    }

    @Test
    fun `localStorage uses separate namespace from generic plugin storage`() {
        bridge.localStorageSet("key", "web-value")
        keyValueStore.set(pluginId, "key", "generic-value")

        bridge.localStorageGet("key") shouldBe "web-value"
        keyValueStore.get(pluginId, "key") shouldBe "generic-value"
    }

    @Test
    fun `localStorageClear does not remove generic plugin storage keys`() {
        bridge.localStorageSet("webkey", "webvalue")
        keyValueStore.set(pluginId, "generickey", "genericvalue")

        bridge.localStorageClear()

        bridge.localStorageGet("webkey").shouldBeNull()
        keyValueStore.get(pluginId, "generickey") shouldBe "genericvalue"
    }

    @Test
    fun `localStorageSyncTimestamp returns 0 initially`() {
        bridge.localStorageSyncTimestamp() shouldBe 0L
    }

    @Test
    fun `localStorageSet updates sync timestamp`() {
        val before = System.currentTimeMillis()
        bridge.localStorageSet("key", "value")
        val after = System.currentTimeMillis()

        val timestamp = bridge.localStorageSyncTimestamp()
        timestamp shouldNotBe 0L
        timestamp shouldBe (timestamp.coerceIn(before, after))
    }

    @Test
    fun `localStorageRemove updates sync timestamp`() {
        bridge.localStorageSet("key", "value")
        val afterSet = bridge.localStorageSyncTimestamp()

        Thread.sleep(10)
        bridge.localStorageRemove("key")
        val afterRemove = bridge.localStorageSyncTimestamp()

        afterRemove shouldBeGreaterThan afterSet
    }

    @Test
    fun `localStorageClear updates sync timestamp`() {
        bridge.localStorageSet("key", "value")
        val afterSet = bridge.localStorageSyncTimestamp()

        Thread.sleep(10)
        bridge.localStorageClear()
        val afterClear = bridge.localStorageSyncTimestamp()

        afterClear shouldBeGreaterThan afterSet
    }

    @Test
    fun `sessionStorageSyncTimestamp returns 0 initially`() {
        bridge.sessionStorageSyncTimestamp() shouldBe 0L
    }

    @Test
    fun `sessionStorageSet updates sync timestamp`() {
        val before = System.currentTimeMillis()
        bridge.sessionStorageSet("key", "value")
        val after = System.currentTimeMillis()

        val timestamp = bridge.sessionStorageSyncTimestamp()
        timestamp shouldNotBe 0L
        timestamp shouldBe (timestamp.coerceIn(before, after))
    }

    @Test
    fun `sessionStorageRemove updates sync timestamp`() {
        bridge.sessionStorageSet("key", "value")
        val afterSet = bridge.sessionStorageSyncTimestamp()

        Thread.sleep(10)
        bridge.sessionStorageRemove("key")
        val afterRemove = bridge.sessionStorageSyncTimestamp()

        afterRemove shouldBeGreaterThan afterSet
    }

    @Test
    fun `sessionStorageClear updates sync timestamp`() {
        bridge.sessionStorageSet("key", "value")
        val afterSet = bridge.sessionStorageSyncTimestamp()

        Thread.sleep(10)
        bridge.sessionStorageClear()
        val afterClear = bridge.sessionStorageSyncTimestamp()

        afterClear shouldBeGreaterThan afterSet
    }

    private infix fun Long.shouldBeGreaterThan(other: Long) {
        (this > other) shouldBe true
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
