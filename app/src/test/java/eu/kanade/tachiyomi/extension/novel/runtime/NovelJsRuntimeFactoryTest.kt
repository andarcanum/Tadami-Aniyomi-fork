package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.network.NetworkHelper
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
}
