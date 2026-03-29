package tachiyomi.data.release

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReleaseServiceImplTest {

    @Test
    fun `maps published_at into release date`() {
        val githubRelease = GithubRelease(
            version = "v0.34",
            info = "Thanks @alice",
            publishedAt = "2026-03-29T12:34:56Z",
            releaseLink = "https://github.com/example/release/v0.34",
            assets = emptyList(),
        )

        githubRelease.toRelease(downloadLink = "https://example.com/app-release.apk") shouldBe
            tachiyomi.domain.release.model.Release(
                version = "v0.34",
                info = "Thanks [@alice](https://github.com/alice)",
                releaseLink = "https://github.com/example/release/v0.34",
                downloadLink = "https://example.com/app-release.apk",
                releaseDate = "2026-03-29",
            )
    }
}
