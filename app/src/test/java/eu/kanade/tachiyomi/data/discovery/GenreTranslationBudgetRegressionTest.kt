package eu.kanade.tachiyomi.data.discovery

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GenreTranslationBudgetRegressionTest {
    @Test
    fun `direct required match does not translate unrelated filter options`() = runTest {
        var calls = 0
        val matcher = GenreTranslationFallback(translate = {
            calls++
            null
        })
        matcher.selectSourceGenres(listOf("Fantasy"), listOf("Fantasy") + (1..30).map { "opaque$it" }) shouldBe
            listOf("Fantasy")
        calls shouldBe 0
    }

    @Test
    fun `unresolved options respect the actual network budget`() = runTest {
        var calls = 0
        val matcher = GenreTranslationFallback(translate = {
            calls++
            null
        }, maxTranslationsPerCall = 2)
        matcher.selectSourceGenres(listOf("Fantasy"), (1..30).map { "opaque$it" }) shouldBe emptyList()
        calls shouldBe 2
    }

    @Test
    fun `failed translation is retried instead of cached as a permanent negative`() = runTest {
        var calls = 0
        val matcher = GenreTranslationFallback(translate = { if (++calls == 1) null else "Fantasy" })
        matcher.selectSourceGenres(listOf("Fantasy"), listOf("opaque")) shouldBe emptyList()
        matcher.selectSourceGenres(listOf("Fantasy"), listOf("opaque")) shouldBe listOf("opaque")
        calls shouldBe 2
    }

    @Test
    fun `translation cancellation propagates`() = runTest {
        val matcher = GenreTranslationFallback(translate = { throw CancellationException("cancel") })
        shouldThrow<CancellationException> { matcher.selectSourceGenres(listOf("Fantasy"), listOf("opaque")) }
    }
}
