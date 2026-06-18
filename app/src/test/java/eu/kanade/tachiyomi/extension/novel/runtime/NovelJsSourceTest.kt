package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.novelsource.model.SNovel
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.domain.extension.novel.model.NovelPlugin
import java.util.concurrent.atomic.AtomicInteger

class NovelJsSourceTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val keyValueStore = InMemoryKeyValueStore()

    @Test
    fun `hasPluginSettings reflects plugin metadata before runtime initialization`() {
        val source = createSource(hasSettings = true)

        source.hasPluginSettings() shouldBe true
    }

    @Test
    fun `hasPluginSettings is false for plugins without settings before runtime initialization`() {
        val source = createSource(hasSettings = false)

        source.hasPluginSettings() shouldBe false
    }

    @Test
    fun `hasPluginSettings discoverRuntime can rediscover settings after cache clear`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime1 = mockk<NovelJsRuntime>(relaxed = true)
        val runtime2 = mockk<NovelJsRuntime>(relaxed = true)
        every { runtimeFactory.create(any()) } returns runtime1 andThen runtime2
        every { runtime1.evaluate(any(), any(), any()) } answers { evaluateSettingsScript(firstArg()) }
        every { runtime2.evaluate(any(), any(), any()) } answers { evaluateSettingsScript(firstArg()) }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )

        source.hasPluginSettings(discoverRuntime = true) shouldBe true

        source.clearInMemoryCaches()

        source.hasPluginSettings(discoverRuntime = true) shouldBe true

        verify(exactly = 2) { runtimeFactory.create("test-plugin") }
    }

    @Test
    fun `hasPluginSettings discoverRuntime waits for in-flight discovery result`() = kotlinx.coroutines.runBlocking {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>(relaxed = true)
        val firstDiscoveryStarted = CompletableDeferred<Unit>()
        val allowFirstDiscovery = CompletableDeferred<Unit>()
        var shouldBlockDiscovery = true

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            val script = firstArg<String>()
            if (shouldBlockDiscovery && script.contains("Array.isArray(__plugin && __plugin.settings)")) {
                shouldBlockDiscovery = false
                firstDiscoveryStarted.complete(Unit)
                kotlinx.coroutines.runBlocking {
                    allowFirstDiscovery.await()
                }
            }
            evaluateSettingsScript(script)
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )

        val firstCall = async(Dispatchers.Default) {
            source.hasPluginSettings(discoverRuntime = true)
        }
        firstDiscoveryStarted.await()

        val secondCall = async(Dispatchers.Default) {
            source.hasPluginSettings(discoverRuntime = true)
        }

        withTimeoutOrNull(200) {
            secondCall.await()
        } shouldBe null

        allowFirstDiscovery.complete(Unit)
        firstCall.await() shouldBe true
        secondCall.await() shouldBe true

        verify(exactly = 1) { runtimeFactory.create("test-plugin") }
    }

    @Test
    fun `clearInMemoryCaches waits for runtime close before rediscovery`() = kotlinx.coroutines.runBlocking {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime1 = mockk<NovelJsRuntime>(relaxed = true)
        val runtime2 = mockk<NovelJsRuntime>(relaxed = true)
        val closeStarted = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        val secondRuntimeCreated = CompletableDeferred<Unit>()
        val createCount = AtomicInteger(0)

        every { runtimeFactory.create(any()) } answers {
            when (createCount.getAndIncrement()) {
                0 -> runtime1
                else -> {
                    secondRuntimeCreated.complete(Unit)
                    runtime2
                }
            }
        }
        every { runtime1.close() } answers {
            closeStarted.complete(Unit)
            kotlinx.coroutines.runBlocking {
                allowClose.await()
            }
        }
        every { runtime1.evaluate(any(), any(), any()) } answers { evaluateSettingsScript(firstArg()) }
        every { runtime2.evaluate(any(), any(), any()) } answers { evaluateSettingsScript(firstArg()) }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )

        source.getFilterList()

        val clearJob = async(Dispatchers.Default) {
            source.clearInMemoryCaches()
        }
        closeStarted.await()

        val filterJob = async(Dispatchers.Default) {
            source.getFilterList()
        }

        kotlinx.coroutines.withTimeoutOrNull(200) {
            secondRuntimeCreated.await()
        } shouldBe null

        allowClose.complete(Unit)
        clearJob.await()
        filterJob.await()

        verify(exactly = 2) { runtimeFactory.create("test-plugin") }
    }

    @Test
    fun `siteUrl caches value until clearInMemoryCaches`() {
        val store = CountingKeyValueStore()
        val source = createSource(
            hasSettings = false,
            keyValueStore = store,
        )

        repeat(5) {
            source.siteUrl shouldBe "https://example.com"
        }
        store.urlGetCount shouldBe 1

        source.clearInMemoryCaches()
        source.siteUrl shouldBe "https://example.com"
        store.urlGetCount shouldBe 2
    }

    @Test
    fun `hasPluginSettings discoverRuntime supports lnreader pluginSettings`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>(relaxed = true)
        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers { evaluateLnReaderSettingsScript(firstArg()) }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )

        source.hasPluginSettings(discoverRuntime = true) shouldBe true
    }

    @Test
    fun `hasPluginSettings discoverRuntime keeps legacy settings array support`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>(relaxed = true)
        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers { evaluateSettingsScript(firstArg()) }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )

        source.hasPluginSettings(discoverRuntime = true) shouldBe true
    }

    @Test
    fun `getChapterList falls back to parsePage when parseNovel fails`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>()
        val parsePageCalls = AtomicInteger(0)

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            evaluateChapterFallbackScript(firstArg(), parsePageCalls)
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
            runtimeOverride = NovelPluginRuntimeOverride(
                pluginId = "test-plugin",
                disableFallbacks = false,
            ),
        )
        val novel = eu.kanade.tachiyomi.novelsource.model.SNovel.create().apply {
            url = "261335--pyeonjibjaui-saengjonsuchig"
            title = "Novel"
        }

        val chapters = kotlinx.coroutines.runBlocking {
            source.getChapterList(novel)
        }

        chapters.size shouldBe 2
        chapters[0].name shouldBe "Ch 1"
        chapters[1].name shouldBe "Ch 2"
    }

    @Test
    fun `getChapterList auto-loads all jaomix pages`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>()
        val parsePageCalls = AtomicInteger(0)
        val delays = mutableListOf<Long>()

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            evaluateJaomixAutoLoadScript(firstArg(), parsePageCalls)
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
            runtimeOverride = NovelPluginRuntimeOverride(pluginId = "jaomix"),
            chapterRequestDelay = { delayMs ->
                delays.add(delayMs)
                Unit
            },
        )
        val novel = SNovel.create().apply {
            url = "/novel"
            title = "Novel"
        }

        val chapters = kotlinx.coroutines.runBlocking {
            source.getChapterList(novel)
        }

        chapters.size shouldBe 3
        chapters.map { it.name } shouldBe listOf("Ch 3", "Ch 2", "Ch 1")
        parsePageCalls.get() shouldBe 2
        delays.size shouldBe 2
        delays.all { it > 0L } shouldBe true
    }

    @Test
    fun `getChapterList retries jaomix parsePage after a transient empty response`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>()
        val parsePageCalls = AtomicInteger(0)
        val delays = mutableListOf<Long>()

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            evaluateJaomixRetryScript(firstArg(), parsePageCalls)
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
            runtimeOverride = NovelPluginRuntimeOverride(pluginId = "jaomix"),
            chapterRequestDelay = { delayMs ->
                delays.add(delayMs)
                Unit
            },
        )
        val novel = SNovel.create().apply {
            url = "/novel"
            title = "Novel"
        }

        val chapters = kotlinx.coroutines.runBlocking {
            source.getChapterList(novel)
        }

        chapters.size shouldBe 3
        chapters.map { it.name } shouldBe listOf("Ch 3", "Ch 2", "Ch 1")
        parsePageCalls.get() shouldBe 3
        delays.size shouldBe 3
        delays[1] shouldBeGreaterThan delays[0]
    }

    @Test
    fun `getChapterListPage falls back to parsePage when parseNovel fails`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>()
        val parsePageCalls = AtomicInteger(0)

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            evaluateChapterFallbackScript(firstArg(), parsePageCalls)
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
            runtimeOverride = NovelPluginRuntimeOverride(
                pluginId = "test-plugin",
                disableFallbacks = false,
            ),
        )
        val novel = eu.kanade.tachiyomi.novelsource.model.SNovel.create().apply {
            url = "261335--pyeonjibjaui-saengjonsuchig"
            title = "Novel"
        }

        val page = kotlinx.coroutines.runBlocking {
            source.getChapterListPage(novel, page = 2)
        }

        val chapterPage = requireNotNull(page)
        chapterPage.page shouldBe 2
        chapterPage.totalPages shouldBe 2
        chapterPage.chapters.size shouldBe 1
        chapterPage.chapters[0].name shouldBe "Ch 2"
    }

    @Test
    fun `getChapterList skips fallbacks when disableFallbacks is true`() {
        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>()
        val parsePageCalls = AtomicInteger(0)

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            evaluateChapterFallbackScript(firstArg(), parsePageCalls)
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
            runtimeOverride = NovelPluginRuntimeOverride(
                pluginId = "test-plugin",
                disableFallbacks = true,
            ),
        )
        val novel = SNovel.create().apply {
            url = "/test-novel"
            title = "Test Novel"
        }

        val chapters = kotlinx.coroutines.runBlocking { source.getChapterList(novel) }

        chapters.size shouldBe 0
    }

    @Test
    fun `getLatestUpdates prefers plugin latest listing method when available`() {
        mockkStatic(android.util.Log::class)
        val loggedErrors = mutableListOf<Throwable?>()
        every { android.util.Log.e(any(), any(), any()) } answers {
            loggedErrors += (invocation.args[2] as Throwable?)
            0
        }

        val runtimeFactory = mockk<NovelJsRuntimeFactory>()
        val runtime = mockk<NovelJsRuntime>(relaxed = true)

        every { runtimeFactory.create(any()) } returns runtime
        every { runtime.evaluate(any(), any(), any()) } answers {
            evaluateLatestListingScript(firstArg())
        }

        val source = createSource(
            hasSettings = false,
            runtimeFactory = runtimeFactory,
        )

        val result = try {
            kotlinx.coroutines.runBlocking {
                source.getLatestUpdates(1)
            }
        } finally {
            unmockkStatic(android.util.Log::class)
        }

        loggedErrors.filterNotNull().map { it.message } shouldBe emptyList()
        result.novels.map { it.title } shouldBe listOf("Latest 1")
    }

    private fun createSource(
        hasSettings: Boolean,
        runtimeFactory: NovelJsRuntimeFactory = mockk(relaxed = true),
        runtimeOverride: NovelPluginRuntimeOverride = NovelPluginRuntimeOverride(pluginId = "test-plugin"),
        keyValueStore: NovelPluginKeyValueStore = this.keyValueStore,
        chapterRequestDelay: suspend (Long) -> Unit = {},
    ): NovelJsSource {
        val plugin = NovelPlugin.Installed(
            id = runtimeOverride.pluginId,
            name = "Test Plugin",
            site = "https://example.com",
            lang = "en",
            versionCode = 1,
            versionName = "1.0.0",
            url = "https://example.com/plugin.js",
            iconUrl = null,
            customJs = null,
            customCss = null,
            hasSettings = hasSettings,
            sha256 = "",
            repoUrl = "https://repo.example/",
        )

        return NovelJsSource(
            plugin = plugin,
            script = "module.exports = {};",
            runtimeFactory = runtimeFactory,
            json = json,
            scriptBuilder = NovelPluginScriptBuilder(),
            filterMapper = NovelPluginFilterMapper(json),
            resultNormalizer = NovelPluginResultNormalizer(),
            runtimeOverride = runtimeOverride,
            settingsBridge = NovelPluginSettingsBridge(
                pluginId = plugin.id,
                keyValueStore = keyValueStore,
                json = json,
            ),
            chapterRequestDelay = chapterRequestDelay,
        )
    }

    private fun evaluateSettingsScript(script: String): Any? {
        return when {
            script.contains("typeof __plugin.pluginSettings === \"object\"") -> false
            script.contains("JSON.stringify(__plugin.pluginSettings || {})") -> "{}"
            script.contains("Array.isArray(__plugin && __plugin.settings)") -> true
            script.contains("JSON.stringify(__plugin.settings || [])") -> """
                [
                    {
                        "key": "apiKey",
                        "type": "Text",
                        "title": "API Key",
                        "default": ""
                    }
                ]
            """.trimIndent()
            script.contains("JSON.stringify(__plugin && __plugin.filters ? __plugin.filters : {})") -> "{}"
            script.contains("typeof __plugin.parsePage") -> false
            script.contains("typeof __plugin.resolveUrl") -> false
            script.contains("typeof __plugin.fetchImage") -> false
            else -> null
        }
    }

    private fun evaluateLnReaderSettingsScript(script: String): Any? {
        return when {
            script.contains("typeof __plugin.pluginSettings === \"object\"") -> true
            script.contains("JSON.stringify(__plugin.pluginSettings || {})") -> """
                {
                    "email": {
                        "value": "",
                        "label": "Email",
                        "type": "Text"
                    }
                }
            """.trimIndent()
            script.contains("Array.isArray(__plugin && __plugin.settings)") -> false
            script.contains("JSON.stringify(__plugin.settings || [])") -> "[]"
            script.contains("JSON.stringify(__plugin && __plugin.filters ? __plugin.filters : {})") -> "{}"
            script.contains("typeof __plugin.parsePage") -> false
            script.contains("typeof __plugin.resolveUrl") -> false
            script.contains("typeof __plugin.fetchImage") -> false
            else -> null
        }
    }

    private fun evaluateChapterFallbackScript(
        script: String,
        parsePageCalls: AtomicInteger,
    ): Any? {
        return when {
            script.contains("Array.isArray(__plugin && __plugin.settings)") -> false
            script.contains("JSON.stringify(__plugin.settings || [])") -> "[]"
            script.contains("typeof __plugin.parsePage") -> true
            script.contains("typeof __plugin.resolveUrl") -> false
            script.contains("typeof __plugin.fetchImage") -> false

            // Polling: drain job queue
            script.contains("__drainJobs") -> null

            // Polling: cleanup
            script.contains("delete globalThis") -> null

            // Polling: done check
            script.startsWith("globalThis.__d_") -> true

            // Polling: error read — simulate parseNovel failure
            script.startsWith("globalThis.__e_") -> null

            // Polling: result read
            script.startsWith("globalThis.__r_") -> {
                val page = parsePageCalls.get()
                if (page == 0) {
                    """{}"""
                } else if (page == 1) {
                    """
                        {
                            "totalPages": 2,
                            "chapters": [
                                {
                                    "name": "Ch 1",
                                    "path": "/c1",
                                    "chapterNumber": 1
                                }
                            ]
                        }
                    """.trimIndent()
                } else {
                    """
                        {
                            "chapters": [
                                {
                                    "name": "Ch $page",
                                    "path": "/c$page",
                                    "chapterNumber": $page
                                }
                            ]
                        }
                    """.trimIndent()
                }
            }

            // Polling: setup script with wrapped plugin method call
            script.contains("(function()") && script.contains("__plugin.parseNovel") -> {
                // parseNovel call: return null (success) but empty result
                null
            }
            script.contains("(function()") && script.contains("__plugin.parsePage") -> {
                parsePageCalls.incrementAndGet()
                null
            }
            script.contains("(function()") -> null

            else -> null
        }
    }

    private fun evaluateJaomixAutoLoadScript(
        script: String,
        parsePageCalls: AtomicInteger,
    ): Any? {
        return when {
            script.contains("Array.isArray(__plugin && __plugin.settings)") -> false
            script.contains("JSON.stringify(__plugin.settings || [])") -> "[]"
            script.contains("typeof __plugin.parsePage") -> true
            script.contains("typeof __plugin.resolveUrl") -> false
            script.contains("typeof __plugin.fetchImage") -> false

            // Polling: drain job queue
            script.contains("__drainJobs") -> null

            // Polling: cleanup
            script.contains("delete globalThis") -> null

            // Polling: done check
            script.startsWith("globalThis.__d_") -> true

            // Polling: error read - simulate success
            script.startsWith("globalThis.__e_") -> null

            // Polling: result read
            script.startsWith("globalThis.__r_") -> {
                when (parsePageCalls.get()) {
                    0 -> """
                        {
                            "totalPages": 3,
                            "chapters": [
                                {
                                    "name": "Ch 1",
                                    "path": "/c1",
                                    "chapterNumber": 1
                                }
                            ]
                        }
                    """.trimIndent()
                    1 -> """
                        {
                            "chapters": [
                                {
                                    "name": "Ch 2",
                                    "path": "/c2",
                                    "chapterNumber": 2
                                }
                            ]
                        }
                    """.trimIndent()
                    else -> """
                        {
                            "chapters": [
                                {
                                    "name": "Ch 3",
                                    "path": "/c3",
                                    "chapterNumber": 3
                                }
                            ]
                        }
                    """.trimIndent()
                }
            }

            // Polling: setup script with wrapped plugin method call
            script.contains("(function()") && script.contains("__plugin.parseNovel") -> {
                null
            }
            script.contains("(function()") && script.contains("__plugin.parsePage") -> {
                parsePageCalls.incrementAndGet()
                null
            }
            script.contains("(function()") -> null

            else -> null
        }
    }

    private fun evaluateJaomixRetryScript(
        script: String,
        parsePageCalls: AtomicInteger,
    ): Any? {
        return when {
            script.contains("Array.isArray(__plugin && __plugin.settings)") -> false
            script.contains("JSON.stringify(__plugin.settings || [])") -> "[]"
            script.contains("typeof __plugin.parsePage") -> true
            script.contains("typeof __plugin.resolveUrl") -> false
            script.contains("typeof __plugin.fetchImage") -> false

            // Polling: drain job queue
            script.contains("__drainJobs") -> null

            // Polling: cleanup
            script.contains("delete globalThis") -> null

            // Polling: done check
            script.startsWith("globalThis.__d_") -> true

            // Polling: error read - keep this path clean so the test exercises an empty
            // page response rather than an exception-only retry.
            script.startsWith("globalThis.__e_") -> null

            // Polling: result read
            script.startsWith("globalThis.__r_") -> {
                when (parsePageCalls.get()) {
                    0 -> """
                        {
                            "totalPages": 3,
                            "chapters": [
                                {
                                    "name": "Ch 1",
                                    "path": "/c1",
                                    "chapterNumber": 1
                                }
                            ]
                        }
                    """.trimIndent()
                    1 -> "[]"
                    2 -> """
                        {
                            "chapters": [
                                {
                                    "name": "Ch 2",
                                    "path": "/c2",
                                    "chapterNumber": 2
                                }
                            ]
                        }
                    """.trimIndent()
                    else -> """
                        {
                            "chapters": [
                                {
                                    "name": "Ch 3",
                                    "path": "/c3",
                                    "chapterNumber": 3
                                }
                            ]
                        }
                    """.trimIndent()
                }
            }

            // Polling: setup script with wrapped plugin method call
            script.contains("(function()") && script.contains("__plugin.parseNovel") -> {
                null
            }
            script.contains("(function()") && script.contains("__plugin.parsePage") -> {
                parsePageCalls.incrementAndGet()
                null
            }
            script.contains("(function()") -> null

            else -> null
        }
    }

    private fun evaluateLatestListingScript(script: String): Any? {
        return when {
            script.contains("typeof __plugin.latestNovels === \"function\"") -> true
            script.contains("typeof __plugin.latestUpdates === \"function\"") -> false
            script.contains("typeof __plugin.pluginSettings === \"object\"") -> false
            script.contains("JSON.stringify(__plugin.pluginSettings || {})") -> "{}"
            script.contains("Array.isArray(__plugin && __plugin.settings)") -> false
            script.contains("JSON.stringify(__plugin.settings || [])") -> "[]"
            script.contains("JSON.stringify(__plugin && __plugin.filters ? __plugin.filters : {})") -> "{}"
            script.contains("typeof __plugin.parsePage") -> false
            script.contains("typeof __plugin.resolveUrl") -> false
            script.contains("typeof __plugin.fetchImage") -> false
            script.contains("(function()") && script.contains("__plugin.latestNovels") -> null
            script.contains("globalThis.__d_") -> true
            script.contains("globalThis.__e_") -> null
            script.contains("globalThis.__r_") -> """
                [
                    {
                        "name": "Latest 1",
                        "path": "/latest-1",
                        "cover": "https://example.com/latest-1.jpg"
                    }
                ]
            """.trimIndent()
            script.contains("(function()") -> null
            script.contains("typeof __plugin") -> false
            script.contains("JSON.stringify") -> "{}"
            else -> null
        }
    }

    private open class InMemoryKeyValueStore : NovelPluginKeyValueStore {
        private val store = mutableMapOf<String, MutableMap<String, String>>()

        open override fun get(pluginId: String, key: String): String? {
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

    private class CountingKeyValueStore : InMemoryKeyValueStore() {
        var urlGetCount = 0
            private set

        override fun get(pluginId: String, key: String): String? {
            if (key == "setting:url") {
                urlGetCount++
            }
            return super.get(pluginId, key)
        }
    }
}
