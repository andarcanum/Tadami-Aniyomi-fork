package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelDomainAliasResolverTest {

    @Test
    fun `applies configured alias for plugin domain`() {
        val overrides = NovelPluginRuntimeOverrides(
            entries = listOf(
                NovelPluginRuntimeOverride(
                    pluginId = "plugin.alias",
                    domainAliases = mapOf(
                        "https://legacy.example.org" to "https://mirror.example.org",
                    ),
                ),
            ),
        )
        val resolver = NovelDomainAliasResolver(overrides)

        val resolved = resolver.resolve(
            pluginId = "plugin.alias",
            url = "https://legacy.example.org/book/1?chapter=2",
        )

        resolved shouldBe "https://mirror.example.org/book/1?chapter=2"
    }

    @Test
    fun `leaves url unchanged when plugin has no alias`() {
        val resolver = NovelDomainAliasResolver(
            NovelPluginRuntimeOverrides(entries = emptyList()),
        )

        val resolved = resolver.resolve(
            pluginId = "plugin.none",
            url = "https://example.org/book/1",
        )

        resolved shouldBe "https://example.org/book/1"
    }
}
