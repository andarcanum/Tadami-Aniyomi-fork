package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelJsPromiseShimTest {

    @Test
    fun `promise shim defines immediate promise helpers`() {
        val script = NovelJsPromiseShim.script

        script shouldContain "ImmediatePromise"
        script shouldContain "then"
        script shouldContain "resolve"
        script shouldContain "reject"
    }
}
