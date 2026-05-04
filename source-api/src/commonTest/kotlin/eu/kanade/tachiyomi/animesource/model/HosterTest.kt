package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class HosterTest {

    @Test
    fun `toHosterList groups videos by playback metadata from internalData`() {
        val videos = listOf(
            Video(
                videoUrl = "https://example.com/cdn-1080.m3u8",
                videoTitle = "1080p",
                internalData = """
                    {"playerId":"cdn","playerLabel":"CDN","dubbingId":"anistar","dubbingLabel":"AniStar","sortOrder":0}
                """.trimIndent(),
            ),
            Video(
                videoUrl = "https://example.com/cdn-720.m3u8",
                videoTitle = "720p",
                internalData = """
                    {"playerId":"cdn","playerLabel":"CDN","dubbingId":"anistar","dubbingLabel":"AniStar","sortOrder":0}
                """.trimIndent(),
            ),
            Video(
                videoUrl = "https://example.com/kodik-1080.m3u8",
                videoTitle = "1080p",
                internalData = """
                    {"playerId":"kodik","playerLabel":"Kodik","dubbingId":"animevost","dubbingLabel":"AnimeVost","sortOrder":10}
                """.trimIndent(),
            ),
        )

        val hosters = videos.toHosterList()

        hosters.shouldHaveSize(2)
        hosters[0].hosterName shouldBe "AniStar"
        hosters[0].playerId shouldBe "cdn"
        hosters[0].playerLabel shouldBe "CDN"
        hosters[0].dubbingId shouldBe "anistar"
        hosters[0].dubbingLabel shouldBe "AniStar"
        hosters[0].videoList?.map { it.videoTitle } shouldBe listOf("1080p", "720p")

        hosters[1].hosterName shouldBe "AnimeVost"
        hosters[1].playerId shouldBe "kodik"
        hosters[1].playerLabel shouldBe "Kodik"
        hosters[1].dubbingId shouldBe "animevost"
        hosters[1].dubbingLabel shouldBe "AnimeVost"
    }

    @Test
    fun `toHosterList falls back to translation suffix parsing when metadata is missing`() {
        val videos = listOf(
            Video(videoUrl = "https://example.com/1.m3u8", videoTitle = "1080p (AniDub)"),
            Video(videoUrl = "https://example.com/2.m3u8", videoTitle = "720p (AniDub)"),
        )

        val hosters = videos.toHosterList()

        hosters.shouldHaveSize(1)
        hosters[0].hosterName shouldBe "AniDub"
        hosters[0].playerId shouldBe null
        hosters[0].dubbingId shouldBe null
        hosters[0].videoList?.map { it.videoTitle } shouldBe listOf("1080p", "720p")
    }

    @Test
    fun `toHosterList groups structured playback titles when metadata is missing`() {
        val videos = listOf(
            Video(videoUrl = "https://example.com/1.m3u8", videoTitle = "CDN • AniStar • 1080p"),
            Video(videoUrl = "https://example.com/2.m3u8", videoTitle = "CDN • AniStar • 720p"),
            Video(videoUrl = "https://example.com/3.m3u8", videoTitle = "Kodik • AnimeVost • 1080p"),
        )

        val hosters = videos.toHosterList()

        hosters.shouldHaveSize(2)
        hosters[0].hosterName shouldBe "AniStar"
        hosters[0].playerId shouldBe "cdn"
        hosters[0].dubbingLabel shouldBe "AniStar"
        hosters[0].videoList?.map { it.videoTitle } shouldBe listOf("1080p", "720p")

        hosters[1].hosterName shouldBe "AnimeVost"
        hosters[1].playerId shouldBe "kodik"
        hosters[1].dubbingLabel shouldBe "AnimeVost"
        hosters[1].videoList?.map { it.videoTitle } shouldBe listOf("1080p")
    }

    @Test
    fun `legacy default constructor signature stays available`() {
        val expectedParameterTypes = listOf(
            "java.lang.String",
            "java.lang.String",
            "java.util.List",
            "java.lang.String",
            "boolean",
            "int",
            "kotlin.jvm.internal.DefaultConstructorMarker",
        )

        Hoster::class.java.declaredConstructors.any { constructor ->
            constructor.parameterTypes.map { it.name } == expectedParameterTypes
        } shouldBe true
    }
}
