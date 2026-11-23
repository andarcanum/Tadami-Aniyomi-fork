package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeCreatorDisplaySource
import eu.kanade.tachiyomi.animesource.AnimeFeedPlaybackHeadersSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.reels.player.reelsRequestHeaders
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReelsSourcePresentationTest {
    private open class Source : AnimeFeedSource {
        override val id: Long = 30L
        override val name: String = "Source"
        override val lang: String = "all"
        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)
    }

    @Test
    fun `creator labels use exact opaque ID and never replace the identity`() {
        val ids = mutableListOf<String>()
        val source = object : Source(), AnimeCreatorDisplaySource {
            override fun creatorDisplayName(creatorId: String): String? {
                ids += creatorId
                return if (creatorId == "/model/a.b") " Human Name " else null
            }
        }
        reelsCreatorLabel(source, "/model/a.b") shouldBe "Human Name"
        reelsCreatorLabel(source, "Unknown legacy", "@Unknown legacy") shouldBe "@Unknown legacy"
        ids shouldBe listOf("/model/a.b", "Unknown legacy")
    }

    @Test
    fun `legacy hidden names match both colliding canonical creators but canonical hides stay targeted`() {
        val source = object : Source(), AnimeCreatorDisplaySource {
            override fun creatorDisplayName(creatorId: String): String? = when (creatorId) {
                "/model/one", "/channels/two" -> "Shared Name"
                "/model/three" -> "/model/one"
                else -> null
            }
        }
        val legacy = setOf("Shared Name", "Unknown legacy")
        reelsCreatorIsHidden(source, "/model/one", legacy) shouldBe true
        reelsCreatorIsHidden(source, "/channels/two", legacy) shouldBe true
        reelsCreatorIsHidden(source, "Unknown legacy", legacy) shouldBe true
        reelsCreatorIsHidden(source, "/model/unknown", legacy) shouldBe false
        legacy shouldBe setOf("Shared Name", "Unknown legacy")

        val canonical = setOf("/model/one")
        reelsCreatorIsHidden(source, "/model/one", canonical) shouldBe true
        reelsCreatorIsHidden(source, "/channels/two", canonical) shouldBe false
        // A different creator's display label cannot turn a targeted route hide into a name hide.
        reelsCreatorIsHidden(source, "/model/three", canonical) shouldBe false
        reelsCreatorIsHidden(Source(), "/model/one", legacy) shouldBe false
    }

    @Test
    fun `hidden creator lookup propagates cancellation and does not guess unknown aliases`() {
        val source = object : Source(), AnimeCreatorDisplaySource {
            override fun creatorDisplayName(creatorId: String): String = throw CancellationException()
        }
        assertThrows(CancellationException::class.java) {
            reelsCreatorIsHidden(source, "/model/one", setOf("Shared Name"))
        }
        val broken = object : Source(), AnimeCreatorDisplaySource {
            override fun creatorDisplayName(creatorId: String): String = throw AbstractMethodError()
        }
        reelsCreatorIsHidden(broken, "/model/one", setOf("Shared Name")) shouldBe false
        reelsCreatorIsHidden(broken, "/model/one", setOf("/model/one")) shouldBe true
    }

    @Test
    fun `legacy labels and source supplied search labels are unchanged`() {
        val source = Source()
        reelsCreatorLabel(source, " @alice ") shouldBe "@alice"
        reelsCreatorLabel(source, "alice", "Alice search label") shouldBe "Alice search label"
        reelsCreatorLabel(null, "legacy") shouldBe "@legacy"
        reelsCreatorLabel(source, null) shouldBe null
    }

    @Test
    fun `blank or broken optional display capability falls back safely`() {
        val blank = object : Source(), AnimeCreatorDisplaySource {
            override fun creatorDisplayName(creatorId: String): String = "  "
        }
        val broken = object : Source(), AnimeCreatorDisplaySource {
            override fun creatorDisplayName(creatorId: String): String = throw AbstractMethodError()
        }
        reelsCreatorLabel(blank, "alice") shouldBe "@alice"
        reelsCreatorLabel(broken, "alice") shouldBe "@alice"
    }

    @Test
    fun `display cancellation is not swallowed`() {
        val source = object : Source(), AnimeCreatorDisplaySource {
            override fun creatorDisplayName(creatorId: String): String = throw CancellationException()
        }
        assertThrows(CancellationException::class.java) { reelsCreatorLabel(source, "alice") }
    }

    @Test
    fun `dynamic playback headers reflect current UA and drop credentials and injection`() {
        var ua = "before-login"
        val source = object : Source(), AnimeFeedPlaybackHeadersSource {
            override fun playbackHeaders(): Map<String, String> = mapOf(
                "user-agent" to ua,
                "Origin" to "https://example.com",
                "Referer" to "https://example.com/",
                "Cookie" to "must-not-export",
                "Authorization" to "must-not-export",
                "X-Session" to "must-not-export",
                "Referer\r\nCookie" to "injection",
            )
        }
        reelsPlaybackHeaders(source) shouldBe mapOf(
            "User-Agent" to "before-login",
            "Origin" to "https://example.com",
            "Referer" to "https://example.com/",
        )
        ua = "after-login"
        reelsPlaybackHeaders(source)["User-Agent"] shouldBe "after-login"
        ua = "agent\r\nCookie: injection"
        reelsPlaybackHeaders(source).containsKey("User-Agent") shouldBe false
    }

    @Test
    fun `empty provider is authoritative and legacy static headers remain accepted`() {
        val empty = object : Source(), AnimeFeedPlaybackHeadersSource {
            override fun playbackHeaders(): Map<String, String> = emptyMap()
        }
        reelsPlaybackHeaders(empty) shouldBe emptyMap()
        val legacy = mockk<AnimeHttpSource>()
        every { legacy.headers } returns Headers.Builder().add("User-Agent", "legacy").add("Accept", "*/*").build()
        reelsPlaybackHeaders(legacy) shouldBe mapOf("User-Agent" to "legacy", "Accept" to "*/*")
    }

    @Test
    fun `failed opted in playback provider never falls back to private HTTP headers`() {
        val hybrid = mockk<AnimeHttpSource>(moreInterfaces = arrayOf(AnimeFeedPlaybackHeadersSource::class))
        every { hybrid.headers } returns Headers.Builder()
            .add("User-Agent", "legacy")
            .add("Cookie", "private-session")
            .add("Authorization", "private-token")
            .build()
        every { (hybrid as AnimeFeedPlaybackHeadersSource).playbackHeaders() } throws
            IllegalStateException("provider failed")
        reelsPlaybackHeaders(hybrid) shouldBe emptyMap()
    }

    @Test
    fun `media source construction reads provider after the composition header snapshot`() {
        var ua = "old"
        val provider = { mapOf("User-Agent" to ua, "Referer" to "https://example.com/") }
        val compositionSnapshot = provider()
        ua = "new"
        reelsRequestHeaders(compositionSnapshot, null, provider)["User-Agent"] shouldBe "new"
        reelsRequestHeaders(compositionSnapshot, null)["User-Agent"] shouldBe "old"
    }

    @Test
    fun `playback header cancellation is not swallowed`() {
        val source = object : Source(), AnimeFeedPlaybackHeadersSource {
            override fun playbackHeaders(): Map<String, String> = throw CancellationException()
        }
        assertThrows(CancellationException::class.java) { reelsPlaybackHeaders(source) }
    }
}
