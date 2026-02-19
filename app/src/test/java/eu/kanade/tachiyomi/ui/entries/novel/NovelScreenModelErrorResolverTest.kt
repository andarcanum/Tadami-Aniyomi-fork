package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NoChaptersException

class NovelScreenModelErrorResolverTest {

    @Test
    fun `suppresses no chapters message when webview login is likely`() {
        resolveNovelRefreshErrorMessage(
            error = NoChaptersException(),
            likelyWebViewLoginRequired = true,
        ) shouldBe null
    }

    @Test
    fun `suppresses could not reach message when webview login is likely`() {
        resolveNovelRefreshErrorMessage(
            error = RuntimeException("Could not reach https://hexnovels.me/content/the-binding-rings (404)"),
            likelyWebViewLoginRequired = true,
        ) shouldBe null
    }

    @Test
    fun `keeps no chapters message when webview login is not likely`() {
        resolveNovelRefreshErrorMessage(
            error = NoChaptersException(),
            likelyWebViewLoginRequired = false,
        ) shouldBe "No chapters found"
    }

    @Test
    fun `keeps original message for regular errors`() {
        resolveNovelRefreshErrorMessage(
            error = RuntimeException("Rate limit"),
            likelyWebViewLoginRequired = false,
        ) shouldBe "Rate limit"
    }

    @Test
    fun `uses fallback for regular errors without message`() {
        resolveNovelRefreshErrorMessage(
            error = RuntimeException(),
            likelyWebViewLoginRequired = false,
        ) shouldBe "Failed to refresh"
    }
}
