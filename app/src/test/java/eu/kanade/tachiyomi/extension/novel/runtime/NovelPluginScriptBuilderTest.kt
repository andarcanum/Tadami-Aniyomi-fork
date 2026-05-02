package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelPluginScriptBuilderTest {

    @Test
    fun `wraps script for global scope evaluation`() {
        val builder = NovelPluginScriptBuilder()
        val result = builder.wrap("module.exports = { foo: 1 };", "plugin:one")
        result.shouldContain("var module = { exports: {} }")
        result.shouldContain("module.exports = { foo: 1 };")
        result.shouldContain("__plugin =")
        result.shouldContain("exports.default || module.exports.default || module.exports")
    }
}
