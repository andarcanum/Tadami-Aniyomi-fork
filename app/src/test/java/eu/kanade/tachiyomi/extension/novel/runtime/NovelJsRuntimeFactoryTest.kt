package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoType
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

class NovelJsRuntimeFactoryTest {

    @Test
    fun `decodeProtoResponse handles sfixed nanos in wuxia decimal`() {
        val payload = ProtoBuf.encodeToByteArray(
            TestWuxiaGetNovelResponse.serializer(),
            TestWuxiaGetNovelResponse(
                item = TestWuxiaNovelItem(
                    karmaInfo = TestWuxiaNovelKarmaInfo(
                        maxFreeChapter = TestWuxiaDecimalValue(
                            units = 50,
                            nanos = 500_000_000,
                        ),
                    ),
                ),
            ),
        )

        val nativeApiClass = Class.forName(
            "eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory\$NativeApiImpl",
        )
        val constructor = nativeApiClass.getDeclaredConstructor(
            String::class.java,
            NetworkHelper::class.java,
            NovelPluginKeyValueStore::class.java,
            Json::class.java,
            NovelDomainAliasResolver::class.java,
        ).apply {
            isAccessible = true
        }
        val nativeApi = constructor.newInstance(
            "wuxiaworld",
            mockk<NetworkHelper>(relaxed = true),
            InMemoryStore(),
            Json { ignoreUnknownKeys = true },
            NovelDomainAliasResolver(NovelPluginRuntimeOverrides()),
        )

        val decodeMethod = nativeApiClass.getDeclaredMethod(
            "decodeProtoResponse",
            String::class.java,
            ByteArray::class.java,
        ).apply {
            isAccessible = true
        }

        val decoded = decodeMethod.invoke(nativeApi, "GetNovelResponse", payload) as String
        decoded.shouldContain("\"units\":50")
        decoded.shouldContain("\"nanos\":500000000")
    }

    @Test
    fun `decodeProtoResponse tolerates unexpected wuxia maxFreeChapter payload`() {
        val payload = ProtoBuf.encodeToByteArray(
            DriftedWuxiaGetNovelResponse.serializer(),
            DriftedWuxiaGetNovelResponse(
                item = DriftedWuxiaNovelItem(
                    id = 123,
                    name = "Drifted Novel",
                    slug = "drifted-novel",
                    karmaInfo = DriftedWuxiaNovelKarmaInfo(
                        maxFreeChapter = DriftedWuxiaStringValue("50.5"),
                    ),
                ),
            ),
        )

        val nativeApiClass = Class.forName(
            "eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory\$NativeApiImpl",
        )
        val constructor = nativeApiClass.getDeclaredConstructor(
            String::class.java,
            NetworkHelper::class.java,
            NovelPluginKeyValueStore::class.java,
            Json::class.java,
            NovelDomainAliasResolver::class.java,
        ).apply {
            isAccessible = true
        }
        val nativeApi = constructor.newInstance(
            "wuxiaworld",
            mockk<NetworkHelper>(relaxed = true),
            InMemoryStore(),
            Json { ignoreUnknownKeys = true },
            NovelDomainAliasResolver(NovelPluginRuntimeOverrides()),
        )

        val decodeMethod = nativeApiClass.getDeclaredMethod(
            "decodeProtoResponse",
            String::class.java,
            ByteArray::class.java,
        ).apply {
            isAccessible = true
        }

        val decoded = decodeMethod.invoke(nativeApi, "GetNovelResponse", payload) as String
        decoded.shouldContain("\"id\":123")
        decoded.shouldContain("\"name\":\"Drifted Novel\"")
        decoded.shouldContain("\"slug\":\"drifted-novel\"")
        decoded.shouldNotContain("maxFreeChapter")
    }

    @Test
    fun `buildRequest creates empty body for post without explicit body`() {
        val nativeApiClass = Class.forName(
            "eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory\$NativeApiImpl",
        )
        val constructor = nativeApiClass.getDeclaredConstructor(
            String::class.java,
            NetworkHelper::class.java,
            NovelPluginKeyValueStore::class.java,
            Json::class.java,
            NovelDomainAliasResolver::class.java,
        ).apply {
            isAccessible = true
        }
        val nativeApi = constructor.newInstance(
            "scribblehub",
            mockk<NetworkHelper>(relaxed = true),
            InMemoryStore(),
            Json { ignoreUnknownKeys = true },
            NovelDomainAliasResolver(NovelPluginRuntimeOverrides()),
        )

        val buildMethod = nativeApiClass.getDeclaredMethod(
            "buildRequest",
            String::class.java,
            String::class.java,
        ).apply {
            isAccessible = true
        }

        val request = buildMethod.invoke(
            nativeApi,
            "https://www.scribblehub.com/",
            """{"method":"POST"}""",
        ) as okhttp3.Request

        assertEquals("POST", request.method)
        assertNotNull(request.body)
    }

    @Test
    fun `buildRequest adds browser headers when absent`() {
        val nativeApiClass = Class.forName(
            "eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory\$NativeApiImpl",
        )
        val constructor = nativeApiClass.getDeclaredConstructor(
            String::class.java,
            NetworkHelper::class.java,
            NovelPluginKeyValueStore::class.java,
            Json::class.java,
            NovelDomainAliasResolver::class.java,
        ).apply {
            isAccessible = true
        }
        val nativeApi = constructor.newInstance(
            "scribblehub",
            mockk<NetworkHelper>(relaxed = true),
            InMemoryStore(),
            Json { ignoreUnknownKeys = true },
            NovelDomainAliasResolver(NovelPluginRuntimeOverrides()),
        )

        val buildMethod = nativeApiClass.getDeclaredMethod(
            "buildRequest",
            String::class.java,
            String::class.java,
        ).apply {
            isAccessible = true
        }

        val request = buildMethod.invoke(
            nativeApi,
            "https://www.scribblehub.com/wp-admin/admin-ajax.php",
            """{"method":"POST"}""",
        ) as okhttp3.Request

        assertTrue((request.header("User-Agent") ?: "").contains("Mozilla/5.0"))
        assertEquals("https://www.scribblehub.com/", request.header("Referer"))
        assertEquals("https://www.scribblehub.com", request.header("Origin"))
        assertNotNull(request.header("Accept-Language"))
    }

    @Test
    fun `buildRequest honors explicit referrer and origin from options`() {
        val nativeApiClass = Class.forName(
            "eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory\$NativeApiImpl",
        )
        val constructor = nativeApiClass.getDeclaredConstructor(
            String::class.java,
            NetworkHelper::class.java,
            NovelPluginKeyValueStore::class.java,
            Json::class.java,
            NovelDomainAliasResolver::class.java,
        ).apply {
            isAccessible = true
        }
        val nativeApi = constructor.newInstance(
            "TL",
            mockk<NetworkHelper>(relaxed = true),
            InMemoryStore(),
            Json { ignoreUnknownKeys = true },
            NovelDomainAliasResolver(NovelPluginRuntimeOverrides()),
        )

        val buildMethod = nativeApiClass.getDeclaredMethod(
            "buildRequest",
            String::class.java,
            String::class.java,
        ).apply {
            isAccessible = true
        }

        val request = buildMethod.invoke(
            nativeApi,
            "https://novel.tl/api/site/v2/graphql",
            """{"method":"POST","referrer":"https://novel.tl/book/123","origin":"https://novel.tl"}""",
        ) as okhttp3.Request

        assertEquals("https://novel.tl/book/123", request.header("Referer"))
        assertEquals("https://novel.tl", request.header("Origin"))
    }

    @Test
    fun `buildRequest parses lowercase bodyType text and preserves request body`() {
        val nativeApiClass = Class.forName(
            "eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory\$NativeApiImpl",
        )
        val constructor = nativeApiClass.getDeclaredConstructor(
            String::class.java,
            NetworkHelper::class.java,
            NovelPluginKeyValueStore::class.java,
            Json::class.java,
            NovelDomainAliasResolver::class.java,
        ).apply {
            isAccessible = true
        }
        val nativeApi = constructor.newInstance(
            "TL",
            mockk<NetworkHelper>(relaxed = true),
            InMemoryStore(),
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
            },
            NovelDomainAliasResolver(NovelPluginRuntimeOverrides()),
        )

        val buildMethod = nativeApiClass.getDeclaredMethod(
            "buildRequest",
            String::class.java,
            String::class.java,
        ).apply {
            isAccessible = true
        }

        val optionsJson = """
            {
              "method": "POST",
              "headers": { "Content-Type": "application/json" },
              "bodyType": "text",
              "body": "{\"query\":\"query Test { ok }\"}"
            }
        """.trimIndent()

        val request = buildMethod.invoke(
            nativeApi,
            "https://novel.tl/api/site/v2/graphql",
            optionsJson,
        ) as okhttp3.Request

        assertEquals("POST", request.method)
        assertNotNull(request.body)
        val body = request.body ?: error("Request body should not be null")
        assertTrue(body.contentLength() > 0)
        val buffer = Buffer()
        body.writeTo(buffer)
        assertTrue(buffer.readUtf8().contains("\"query\""))
    }

    @Test
    fun `storage methods preserve legacy raw keys and ignore saved settings keys`() {
        val nativeApiClass = Class.forName(
            "eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory\$NativeApiImpl",
        )
        val constructor = nativeApiClass.getDeclaredConstructor(
            String::class.java,
            NetworkHelper::class.java,
            NovelPluginKeyValueStore::class.java,
            Json::class.java,
            NovelDomainAliasResolver::class.java,
        ).apply {
            isAccessible = true
        }
        val store = InMemoryStore()
        store.set("plugin-a", "cache_key", "legacy-cache-value")
        store.set("plugin-a", "setting:apiKey", "persisted-settings")
        val nativeApi = constructor.newInstance(
            "plugin-a",
            mockk<NetworkHelper>(relaxed = true),
            store,
            Json { ignoreUnknownKeys = true },
            NovelDomainAliasResolver(NovelPluginRuntimeOverrides()),
        )

        val storageSet = nativeApiClass.getDeclaredMethod(
            "storageSet",
            String::class.java,
            String::class.java,
        ).apply {
            isAccessible = true
        }
        val storageGet = nativeApiClass.getDeclaredMethod(
            "storageGet",
            String::class.java,
        ).apply {
            isAccessible = true
        }
        val storageKeys = nativeApiClass.getDeclaredMethod("storageKeys").apply {
            isAccessible = true
        }
        val storageClear = nativeApiClass.getDeclaredMethod("storageClear").apply {
            isAccessible = true
        }

        storageGet.invoke(nativeApi, "cache_key") shouldBe "legacy-cache-value"
        storageSet.invoke(nativeApi, "cache_key", "cache_value")

        store.get("plugin-a", "storage:cache_key") shouldBe "cache_value"
        store.get("plugin-a", "cache_key") shouldBe null
        store.get("plugin-a", "setting:apiKey") shouldBe "persisted-settings"

        val keysBeforeClear = storageKeys.invoke(nativeApi) as String
        keysBeforeClear shouldContain "\"cache_key\""
        keysBeforeClear shouldNotContain "setting:apiKey"

        storageClear.invoke(nativeApi)

        store.get("plugin-a", "storage:cache_key") shouldBe null
        store.get("plugin-a", "setting:apiKey") shouldBe "persisted-settings"
    }

    private class InMemoryStore : NovelPluginKeyValueStore {
        private val values = mutableMapOf<String, MutableMap<String, String>>()

        override fun get(pluginId: String, key: String): String? {
            return values[pluginId]?.get(key)
        }

        override fun set(pluginId: String, key: String, value: String) {
            val pluginValues = values.getOrPut(pluginId) { mutableMapOf() }
            pluginValues[key] = value
        }

        override fun remove(pluginId: String, key: String) {
            values[pluginId]?.remove(key)
        }

        override fun clear(pluginId: String) {
            values[pluginId]?.clear()
        }

        override fun clearAll() {
            values.values.forEach { it.clear() }
        }

        override fun keys(pluginId: String): Set<String> {
            return values[pluginId]?.keys.orEmpty()
        }
    }

    @Serializable
    private data class TestWuxiaGetNovelResponse(
        @ProtoNumber(1) val item: TestWuxiaNovelItem? = null,
    )

    @Serializable
    private data class TestWuxiaNovelItem(
        @ProtoNumber(14) val karmaInfo: TestWuxiaNovelKarmaInfo? = null,
    )

    @Serializable
    private data class TestWuxiaNovelKarmaInfo(
        @ProtoNumber(3) val maxFreeChapter: TestWuxiaDecimalValue? = null,
    )

    @Serializable
    private data class TestWuxiaDecimalValue(
        @ProtoNumber(1) val units: Long? = null,
        @ProtoType(ProtoIntegerType.FIXED)
        @ProtoNumber(2) val nanos: Int? = null,
    )

    @Serializable
    private data class DriftedWuxiaGetNovelResponse(
        @ProtoNumber(1) val item: DriftedWuxiaNovelItem? = null,
    )

    @Serializable
    private data class DriftedWuxiaNovelItem(
        @ProtoNumber(1) val id: Int? = null,
        @ProtoNumber(2) val name: String? = null,
        @ProtoNumber(3) val slug: String? = null,
        @ProtoNumber(14) val karmaInfo: DriftedWuxiaNovelKarmaInfo? = null,
    )

    @Serializable
    private data class DriftedWuxiaNovelKarmaInfo(
        @ProtoNumber(3) val maxFreeChapter: DriftedWuxiaStringValue? = null,
    )

    @Serializable
    private data class DriftedWuxiaStringValue(
        @ProtoNumber(1) val value: String? = null,
    )

    // Golden-plugin regression tests for webStorageUtilized behavior

    @Test
    fun `webStorage stores and retrieves values per plugin`() {
        val store = InMemoryStore()

        // Simulate kakuyomu-auth plugin storing auth token
        store.set("kakuyomu-auth", "auth_token", "token_123")
        store.set("kakuyomu-auth", "user_id", "user_456")

        // Simulate pixiv-novel plugin storing different values
        store.set("pixiv-novel", "auth_token", "pixiv_token_789")
        store.set("pixiv-novel", "refresh_token", "refresh_abc")

        // Verify isolation between plugins
        store.get("kakuyomu-auth", "auth_token") shouldBe "token_123"
        store.get("pixiv-novel", "auth_token") shouldBe "pixiv_token_789"
        store.get("kakuyomu-auth", "refresh_token") shouldBe null
    }

    @Test
    fun `webStorage removes and clears values correctly`() {
        val store = InMemoryStore()

        // Setup: store values for hameln-auth plugin
        store.set("hameln-auth", "session", "session_1")
        store.set("hameln-auth", "preferences", "pref_data")

        // Test remove
        store.remove("hameln-auth", "session")
        store.get("hameln-auth", "session") shouldBe null
        store.get("hameln-auth", "preferences") shouldBe "pref_data"

        // Test clear
        store.set("hameln-auth", "session", "session_2")
        store.clear("hameln-auth")
        store.get("hameln-auth", "session") shouldBe null
        store.get("hameln-auth", "preferences") shouldBe null
    }

    @Test
    fun `webStorage returns correct keys for plugin`() {
        val store = InMemoryStore()

        store.set("plugin-a", "key1", "value1")
        store.set("plugin-a", "key2", "value2")
        store.set("plugin-b", "key1", "value3")

        val keysA = store.keys("plugin-a")
        val keysB = store.keys("plugin-b")

        ("key1" in keysA) shouldBe true
        ("key2" in keysA) shouldBe true
        ("key3" in keysA) shouldBe false
        ("key1" in keysB) shouldBe true
        ("key2" in keysB) shouldBe false
    }

    @Test
    fun `webStorage clearAll removes all data`() {
        val store = InMemoryStore()

        store.set("plugin1", "key", "value")
        store.set("plugin2", "key", "value")

        store.clearAll()

        store.get("plugin1", "key") shouldBe null
        store.get("plugin2", "key") shouldBe null
        store.keys("plugin1").shouldBeEmpty()
        store.keys("plugin2").shouldBeEmpty()
    }
}
