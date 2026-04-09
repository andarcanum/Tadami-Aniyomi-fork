package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsEngineRegistryTest {

    @Test
    fun `listEngines marks system default and preserves labels`() {
        val registry = NovelTtsEngineRegistry(
            infoSource = FakeEngineInfoSource(
                defaultEnginePackage = "engine.default",
                engines = listOf(
                    NovelTtsInstalledEngine(packageName = "engine.default", label = "Default Engine"),
                    NovelTtsInstalledEngine(packageName = "engine.alt", label = "Alt Engine"),
                ),
            ),
        )

        val engines = registry.listEngines()

        engines.shouldHaveSize(2)
        engines[0].packageName shouldBe "engine.default"
        engines[0].isSystemDefault shouldBe true
        engines[1].isSystemDefault shouldBe false
    }

    @Test
    fun `resolvePreferredEngine falls back to system default when selected engine is missing`() {
        val registry = NovelTtsEngineRegistry(
            infoSource = FakeEngineInfoSource(
                defaultEnginePackage = "engine.default",
                engines = listOf(
                    NovelTtsInstalledEngine(packageName = "engine.default", label = "Default Engine"),
                ),
            ),
        )

        val resolved = registry.resolvePreferredEngine("engine.missing")

        resolved?.packageName shouldBe "engine.default"
    }

    private class FakeEngineInfoSource(
        private val defaultEnginePackage: String?,
        private val engines: List<NovelTtsInstalledEngine>,
    ) : NovelTtsEngineInfoSource {
        override fun listInstalledEngines(): List<NovelTtsInstalledEngine> = engines

        override fun defaultEnginePackage(): String? = defaultEnginePackage
    }
}
