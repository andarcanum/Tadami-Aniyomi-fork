package eu.kanade.tachiyomi.data.track.trakt

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class TraktMappingsTest {

    @Test
    fun `resolveTraktSeasonEpisode returns null season for whole episode numbers`() {
        val (season, episode) = resolveTraktSeasonEpisode(7.0)
        season shouldBe null
        episode shouldBe 7
    }

    @Test
    fun `resolveTraktSeasonEpisode floors zero values to a safe lower bound`() {
        val (season, episode) = resolveTraktSeasonEpisode(0.0)
        season shouldBe null
        episode shouldBe 1
    }

    @Test
    fun `resolveTraktSeasonEpisode parses dotted season-episode notation`() {
        val (season, episode) = resolveTraktSeasonEpisode(2.13)
        season shouldBe 2
        episode shouldBe 13
    }

    @Test
    fun `resolveTraktSeasonEpisode handles single-digit episodes within seasons`() {
        val (season, episode) = resolveTraktSeasonEpisode(3.4)
        season shouldBe 3
        episode shouldBe 4
    }

    @Test
    fun `resolveTraktSeasonEpisode falls back when season prefix is zero`() {
        val (season, episode) = resolveTraktSeasonEpisode(0.5)
        // Season 0 (specials) is rejected and we replace with season 1
        season shouldBe 1
        episode shouldBe 5
    }

    @Test
    fun `extractTraktPosterUrl prefers full size when present`() {
        val node = buildJsonObject {
            put("full", JsonPrimitive("walter.com/full.jpg"))
            put("medium", JsonPrimitive("walter.com/medium.jpg"))
        }
        extractTraktPosterUrl(node) shouldBe "https://walter.com/full.jpg"
    }

    @Test
    fun `extractTraktPosterUrl falls back to medium then thumb`() {
        val mediumOnly = buildJsonObject {
            put("medium", JsonPrimitive("walter.com/medium.jpg"))
        }
        extractTraktPosterUrl(mediumOnly) shouldBe "https://walter.com/medium.jpg"

        val thumbOnly = buildJsonObject {
            put("thumb", JsonPrimitive("walter.com/thumb.jpg"))
        }
        extractTraktPosterUrl(thumbOnly) shouldBe "https://walter.com/thumb.jpg"
    }

    @Test
    fun `extractTraktPosterUrl normalises protocol-relative urls`() {
        val node = buildJsonObject {
            put("full", JsonPrimitive("//walter.com/poster.jpg"))
        }
        extractTraktPosterUrl(node) shouldBe "https://walter.com/poster.jpg"
    }

    @Test
    fun `extractTraktPosterUrl preserves absolute https urls`() {
        val node = buildJsonObject {
            put("full", JsonPrimitive("https://walter.com/poster.jpg"))
        }
        extractTraktPosterUrl(node) shouldBe "https://walter.com/poster.jpg"
    }

    @Test
    fun `extractTraktPosterUrl supports plain string input`() {
        extractTraktPosterUrl(JsonPrimitive("walter.com/poster.jpg")) shouldBe "https://walter.com/poster.jpg"
    }

    @Test
    fun `extractTraktPosterUrl supports array of urls`() {
        val arr = buildJsonArray { add("walter.com/first.jpg") }
        extractTraktPosterUrl(arr) shouldBe "https://walter.com/first.jpg"
    }

    @Test
    fun `extractTraktPosterUrl returns empty string when no usable url is found`() {
        extractTraktPosterUrl(null) shouldBe ""
        extractTraktPosterUrl(JsonNull) shouldBe ""
        extractTraktPosterUrl(JsonPrimitive("")) shouldBe ""
    }

    @Test
    fun `Trakt OAuth payload is round-trip serialisable`() {
        val payload = """
            {
                "access_token": "abc",
                "refresh_token": "def",
                "expires_in": 7200,
                "created_at": 1700000000,
                "token_type": "bearer"
            }
        """.trimIndent()
        val json = Json { ignoreUnknownKeys = true }
        val oauth = json.decodeFromString<eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth>(payload)
        oauth.access_token shouldBe "abc"
        oauth.refresh_token shouldBe "def"
        oauth.expires_in shouldBe 7200
        oauth.created_at shouldBe 1700000000
        oauth.token_type shouldBe "bearer"
    }

    @Test
    fun `Trakt search response parses show entries with ids and images`() {
        val payload = """
            [
                {
                    "type": "show",
                    "score": 84.123,
                    "show": {
                        "title": "Demon Slayer",
                        "year": 2019,
                        "ids": { "trakt": 142064, "slug": "demon-slayer", "imdb": "tt9335498", "tmdb": 85937 },
                        "overview": "Tanjiro becomes a slayer.",
                        "images": { "poster": { "full": "//demonslayer.com/poster-full.jpg" } }
                    }
                }
            ]
        """.trimIndent()
        val json = Json { ignoreUnknownKeys = true }
        val results =
            json.decodeFromString<List<eu.kanade.tachiyomi.data.track.trakt.dto.TraktSearchResult>>(payload)
        results.size shouldBe 1
        val first = results.first()
        first.type shouldBe "show"
        first.show?.title shouldBe "Demon Slayer"
        first.show?.ids?.trakt shouldBe 142064L
        first.show?.ids?.slug shouldBe "demon-slayer"
        first.show?.ids?.imdb shouldBe "tt9335498"
        first.show?.ids?.tmdb shouldBe 85937L
        extractTraktPosterUrl(first.show?.images?.poster) shouldBe "https://demonslayer.com/poster-full.jpg"
    }

    @Test
    fun `Trakt search response parses movie entries`() {
        val payload = """
            [
                {
                    "type": "movie",
                    "score": 50.0,
                    "movie": {
                        "title": "Your Name",
                        "year": 2016,
                        "ids": { "trakt": 1234, "slug": "your-name-2016" }
                    }
                }
            ]
        """.trimIndent()
        val json = Json { ignoreUnknownKeys = true }
        val results =
            json.decodeFromString<List<eu.kanade.tachiyomi.data.track.trakt.dto.TraktSearchResult>>(payload)
        val movie = results.first().movie
        movie?.title shouldBe "Your Name"
        movie?.ids?.trakt shouldBe 1234L
        movie?.ids?.slug shouldBe "your-name-2016"
        movie?.ids?.imdb shouldBe null
    }
}
