package eu.kanade.tachiyomi.extension.novel.js

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelJsModulesTest {

    @Test
    fun `default registry includes base modules`() {
        val registry = defaultNovelJsModuleRegistry()
        val names = listOf(
            "@libs/fetch",
            "@libs/storage",
            "@libs/filterInputs",
            "@libs/novelStatus",
            "@libs/defaultCover",
        )

        names.forEach { name ->
            (registry.get(name) != null) shouldBe true
        }
    }
}
