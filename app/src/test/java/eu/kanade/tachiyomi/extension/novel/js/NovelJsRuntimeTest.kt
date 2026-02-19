package eu.kanade.tachiyomi.extension.novel.js

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelJsRuntimeTest {

    @Test
    fun `contexts are isolated by plugin id`() {
        val runtime = NovelJsRuntime()
        val first = runtime.getOrCreateContext("a")
        val second = runtime.getOrCreateContext("b")

        first.moduleCache["key"] = "value"

        (second.moduleCache.containsKey("key")) shouldBe false
    }

    @Test
    fun `registry returns registered module`() {
        val registry = JsModuleRegistry()
        val module = FakeModule(name = "filters")

        registry.register(module)

        registry.get("filters") shouldBe module
    }

    private class FakeModule(override val name: String) : JsModule
}
