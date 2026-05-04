package eu.kanade.presentation.webview

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WebViewCloudflarePolicyTest {

    @Test
    fun `shouldReloadMainFrameNavigation ignores subframe requests`() {
        shouldReloadMainFrameNavigation(
            requestUrl = "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/g/turnstile",
            currentUrl = "https://source.example/chapter/1",
            isForMainFrame = false,
            method = "GET",
        ) shouldBe false
    }

    @Test
    fun `shouldReloadMainFrameNavigation ignores post submissions`() {
        shouldReloadMainFrameNavigation(
            requestUrl = "https://source.example/cdn-cgi/challenge-platform/h/g/flow/ov1",
            currentUrl = "https://source.example/chapter/1",
            isForMainFrame = true,
            method = "POST",
        ) shouldBe false
    }

    @Test
    fun `shouldReloadMainFrameNavigation reloads changed main-frame get navigations`() {
        shouldReloadMainFrameNavigation(
            requestUrl = "https://source.example/login",
            currentUrl = "https://source.example/chapter/1",
            isForMainFrame = true,
            method = "GET",
        ) shouldBe true
    }

    @Test
    fun `isCloudflareChallengePage detects challenge urls`() {
        isCloudflareChallengePage(
            url = "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/g/turnstile/f/ov2",
            title = "Any",
        ) shouldBe true
    }

    @Test
    fun `isCloudflareChallengePage detects challenge titles`() {
        isCloudflareChallengePage(
            url = "https://source.example/chapter/1",
            title = "Just a moment...",
        ) shouldBe true
    }
}
