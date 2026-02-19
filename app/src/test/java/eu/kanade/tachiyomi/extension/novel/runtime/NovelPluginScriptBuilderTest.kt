package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelPluginScriptBuilderTest {

    @Test
    fun `wraps script in module factory`() {
        val builder = NovelPluginScriptBuilder()
        val result = builder.wrap("module.exports = { foo: 1 };", "plugin:one")
        result.shouldContain("__defineModule(\"plugin:one\"")
        result.shouldContain("module.exports = { foo: 1 };")
    }
}
