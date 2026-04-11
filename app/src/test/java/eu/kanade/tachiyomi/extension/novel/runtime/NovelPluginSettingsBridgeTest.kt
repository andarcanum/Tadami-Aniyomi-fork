package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

class NovelPluginSettingsBridgeTest {

    private lateinit var keyValueStore: InMemoryKeyValueStore
    private lateinit var bridge: NovelPluginSettingsBridge
    private val json = Json { ignoreUnknownKeys = true }
    private val pluginId = "test-plugin-123"

    @BeforeEach
    fun setUp() {
        keyValueStore = InMemoryKeyValueStore()
        bridge = NovelPluginSettingsBridge(pluginId, keyValueStore, json)
    }

    @Test
    fun `parseSettingsSchema returns empty list for empty JSON array`() {
        val schema = "[]"
        val result = bridge.parseSettingsSchema(schema)
        result shouldBe emptyList()
    }

    @Test
    fun `parseSettingsSchema parses single text input setting`() {
        val schema = """
            [
                {
                    "key": "apiKey",
                    "type": "Text",
                    "default": "",
                    "title": "API Key"
                }
            ]
        """.trimIndent()

        val result = bridge.parseSettingsSchema(schema)
        result.size shouldBe 1
        result[0].key shouldBe "apiKey"
        result[0].type shouldBe "Text"
        result[0].default shouldBe JsonPrimitive("")
        result[0].title shouldBe "API Key"
    }

    @Test
    fun `parseSettingsSchema parses multiple settings`() {
        val schema = """
            [
                {
                    "key": "apiKey",
                    "type": "Text",
                    "default": "",
                    "title": "API Key"
                },
                {
                    "key": "baseUrl",
                    "type": "Text",
                    "default": "https://example.com",
                    "title": "Base URL"
                },
                {
                    "key": "useProxy",
                    "type": "Switch",
                    "default": false,
                    "title": "Use Proxy"
                }
            ]
        """.trimIndent()

        val result = bridge.parseSettingsSchema(schema)
        result.size shouldBe 3
        result[0].key shouldBe "apiKey"
        result[1].key shouldBe "baseUrl"
        result[2].key shouldBe "useProxy"
    }

    @Test
    fun `setSetting stores value with namespaced key`() {
        bridge.setSetting("apiKey", "my-secret-key")

        val stored = keyValueStore.get(pluginId, "setting:apiKey")
        stored shouldContain "\"value\":\"my-secret-key\""
    }

    @Test
    fun `getSetting retrieves stored value`() {
        keyValueStore.set(pluginId, "setting:apiKey", "stored-value")

        val result = bridge.getSetting("apiKey")
        result shouldBe "stored-value"
    }

    @Test
    fun `setSetting overwrites existing value`() {
        bridge.setSetting("apiKey", "old-value")
        bridge.setSetting("apiKey", "new-value")

        val result = bridge.getSetting("apiKey")
        result shouldBe "new-value"
    }

    @Test
    fun `different plugins have isolated settings`() {
        val bridge1 = NovelPluginSettingsBridge("plugin-1", keyValueStore, json)
        val bridge2 = NovelPluginSettingsBridge("plugin-2", keyValueStore, json)

        bridge1.setSetting("key", "value-1")
        bridge2.setSetting("key", "value-2")

        bridge1.getSetting("key") shouldBe "value-1"
        bridge2.getSetting("key") shouldBe "value-2"
    }

    @Test
    fun `getSetting returns null when no value stored and no schema`() {
        val result = bridge.getSetting("nonExistentKey")
        result.shouldBeNull()
    }

    @Test
    fun `getSettingWithDefault returns default from schema when no value stored`() {
        val schema = """
            [
                {
                    "key": "apiKey",
                    "type": "Text",
                    "default": "default-key",
                    "title": "API Key"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(schema)
        val result = bridge.getSettingWithDefault("apiKey")
        result shouldBe "default-key"
    }

    @Test
    fun `getSettingWithDefault returns stored value over default`() {
        val schema = """
            [
                {
                    "key": "apiKey",
                    "type": "Text",
                    "default": "default-key",
                    "title": "API Key"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(schema)
        bridge.setSetting("apiKey", "stored-value")

        val result = bridge.getSettingWithDefault("apiKey")
        result shouldBe "stored-value"
    }

    @Test
    fun `getSettingWithDefault returns null for unknown key without default`() {
        val schema = """
            [
                {
                    "key": "apiKey",
                    "type": "Text",
                    "default": "default-key",
                    "title": "API Key"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(schema)
        val result = bridge.getSettingWithDefault("unknownKey")
        result.shouldBeNull()
    }

    @Test
    fun `getSetting returns stored value even if key removed from schema`() {
        val initialSchema = """
            [
                {
                    "key": "oldKey",
                    "type": "Text",
                    "default": "",
                    "title": "Old Key"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(initialSchema)
        bridge.setSetting("oldKey", "persisted-value")

        val newSchema = """
            [
                {
                    "key": "newKey",
                    "type": "Text",
                    "default": "",
                    "title": "New Key"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(newSchema)

        bridge.getSetting("oldKey") shouldBe "persisted-value"
        bridge.getSettingWithDefault("oldKey") shouldBe "persisted-value"
    }

    @Test
    fun `getAllSettings returns all stored values`() {
        bridge.setSetting("key1", "value1")
        bridge.setSetting("key2", "value2")
        bridge.setSetting("key3", "value3")

        val allSettings = bridge.getAllSettings()
        allSettings.size shouldBe 3
        allSettings["key1"] shouldBe "value1"
        allSettings["key2"] shouldBe "value2"
        allSettings["key3"] shouldBe "value3"
    }

    @Test
    fun `removeSetting removes a specific setting`() {
        bridge.setSetting("key1", "value1")
        bridge.setSetting("key2", "value2")

        bridge.removeSetting("key1")

        bridge.getSetting("key1").shouldBeNull()
        bridge.getSetting("key2") shouldBe "value2"
    }

    @Test
    fun `clearSettings removes all settings for plugin`() {
        bridge.setSetting("key1", "value1")
        bridge.setSetting("key2", "value2")

        bridge.clearSettings()

        bridge.getSetting("key1").shouldBeNull()
        bridge.getSetting("key2").shouldBeNull()
    }

    @Test
    fun `getSettingWithDefault handles boolean default`() {
        val schema = """
            [
                {
                    "key": "enabled",
                    "type": "Switch",
                    "default": true,
                    "title": "Enable Feature"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(schema)
        val result = bridge.getSettingWithDefault("enabled")
        result shouldBe "true"
    }

    @Test
    fun `getSettingWithDefault handles integer default`() {
        val schema = """
            [
                {
                    "key": "timeout",
                    "type": "Text",
                    "default": 30,
                    "title": "Timeout"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(schema)
        val result = bridge.getSettingWithDefault("timeout")
        result shouldBe "30"
    }

    @Test
    fun `getSettingValuesWithDefault returns checkbox group default selection`() {
        val schema = """
            [
                {
                    "key": "genres",
                    "type": "Checkbox",
                    "default": ["action", "comedy"],
                    "title": "Genres",
                    "values": ["action", "comedy", "drama"]
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(schema)

        val result = bridge.getSettingValuesWithDefault("genres")

        result shouldBe setOf("action", "comedy")
    }

    @Test
    fun `setSettingValues stores checkbox group selection as payload`() {
        bridge.setSettingValues("genres", setOf("action", "drama"))

        val stored = keyValueStore.get(pluginId, "setting:genres")
        stored shouldContain "\"value\":[\"action\",\"drama\"]"
        bridge.getSettingValues("genres") shouldBe setOf("action", "drama")
    }

    @Test
    fun `schema with picker type parses options`() {
        val schema = """
            [
                {
                    "key": "theme",
                    "type": "Picker",
                    "default": "light",
                    "title": "Theme",
                    "options": ["light", "dark", "auto"]
                }
            ]
        """.trimIndent()

        val result = bridge.parseSettingsSchema(schema)
        result.size shouldBe 1
        result[0].key shouldBe "theme"
        result[0].type shouldBe "Picker"
        result[0].options shouldBe listOf("light", "dark", "auto")
    }

    @Test
    fun `schema with checkbox group type parses values`() {
        val schema = """
            [
                {
                    "key": "genres",
                    "type": "Checkbox",
                    "default": ["action"],
                    "title": "Genres",
                    "values": ["action", "comedy", "drama"]
                }
            ]
        """.trimIndent()

        val result = bridge.parseSettingsSchema(schema)
        result.size shouldBe 1
        result[0].key shouldBe "genres"
        result[0].type shouldBe "Checkbox"
        result[0].values shouldBe listOf("action", "comedy", "drama")
    }

    // Golden-plugin regression tests for settings-dependent plugins

    @Test
    fun `narou-style filter settings with multiple checkbox options`() {
        // Narou plugin allows filtering by multiple genres/tags
        val narouSchema = """
            [
                {
                    "key": "includedGenres",
                    "type": "Checkbox",
                    "default": [],
                    "title": "Include Genres",
                    "values": ["Fantasy", "Action", "Romance", "Comedy", "Drama"]
                },
                {
                    "key": "excludedGenres",
                    "type": "Checkbox",
                    "default": [],
                    "title": "Exclude Genres",
                    "values": ["Horror", "Gore", "Mature"]
                }
            ]
        """.trimIndent()

        val result = bridge.parseSettingsSchema(narouSchema)
        result.size shouldBe 2
        result[0].key shouldBe "includedGenres"
        result[0].values?.size shouldBe 5
        result[1].key shouldBe "excludedGenres"
        result[1].values?.size shouldBe 3
    }

    @Test
    fun `syosetu-premium-style quality and view settings`() {
        // Syosetu Premium plugin has quality and view mode settings
        val premiumSchema = """
            [
                {
                    "key": "imageQuality",
                    "type": "Picker",
                    "default": "high",
                    "title": "Image Quality",
                    "options": ["low", "medium", "high", "original"]
                },
                {
                    "key": "viewMode",
                    "type": "Picker",
                    "default": "vertical",
                    "title": "Reading Mode",
                    "options": ["vertical", "horizontal", "paged"]
                },
                {
                    "key": "showComments",
                    "type": "Switch",
                    "default": true,
                    "title": "Show Reader Comments"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(premiumSchema)

        // Verify defaults are correctly loaded
        bridge.getSettingWithDefault("imageQuality") shouldBe "high"
        bridge.getSettingWithDefault("viewMode") shouldBe "vertical"
        bridge.getSettingWithDefault("showComments") shouldBe "true"

        // Test changing settings
        bridge.setSetting("imageQuality", "original")
        bridge.getSettingWithDefault("imageQuality") shouldBe "original"
    }

    @Test
    fun `hameln-auth-style complex settings with credentials`() {
        // Hameln authenticated plugin has credentials and preferences
        val hamelnSchema = """
            [
                {
                    "key": "username",
                    "type": "Text",
                    "default": "",
                    "title": "Username"
                },
                {
                    "key": "password",
                    "type": "Text",
                    "default": "",
                    "title": "Password"
                },
                {
                    "key": "autoLogin",
                    "type": "Switch",
                    "default": false,
                    "title": "Auto-login on startup"
                },
                {
                    "key": "contentFilter",
                    "type": "Picker",
                    "default": "safe",
                    "title": "Content Filter",
                    "options": ["safe", "warn", "all"]
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(hamelnSchema)

        // Simulate user entering credentials
        bridge.setSetting("username", "testuser")
        bridge.setSetting("password", "secret123")
        bridge.setSetting("autoLogin", "true")
        bridge.setSetting("contentFilter", "warn")

        bridge.getSettingWithDefault("username") shouldBe "testuser"
        bridge.getSettingWithDefault("password") shouldBe "secret123"
        bridge.getSettingWithDefault("autoLogin") shouldBe "true"
        bridge.getSettingWithDefault("contentFilter") shouldBe "warn"
    }

    @Test
    fun `settings persist across schema reloads`() {
        // Test that user settings persist when plugin updates its schema
        val initialSchema = """
            [
                {
                    "key": "apiKey",
                    "type": "Text",
                    "default": "",
                    "title": "API Key"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(initialSchema)
        bridge.setSetting("apiKey", "my-api-key-123")

        // Plugin updates with new setting added
        val updatedSchema = """
            [
                {
                    "key": "apiKey",
                    "type": "Text",
                    "default": "",
                    "title": "API Key"
                },
                {
                    "key": "baseUrl",
                    "type": "Text",
                    "default": "https://api.example.com",
                    "title": "Base URL"
                }
            ]
        """.trimIndent()

        bridge.loadSettingsSchema(updatedSchema)

        // Old setting should persist
        bridge.getSetting("apiKey") shouldBe "my-api-key-123"
        // New setting should have default
        bridge.getSettingWithDefault("baseUrl") shouldBe "https://api.example.com"
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
