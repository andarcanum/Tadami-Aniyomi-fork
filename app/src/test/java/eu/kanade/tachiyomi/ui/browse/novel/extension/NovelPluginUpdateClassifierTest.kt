package eu.kanade.tachiyomi.ui.browse.novel.extension

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelPluginUpdateClassifierTest {

    @Test
    fun `same repo newer version is a regular update`() {
        val installed = installed(repoUrl = "https://main.example/index.json", versionCode = 1)
        val result = NovelPluginUpdateClassifier.classify(
            installed = installed,
            variants = listOf(
                available(repoUrl = "https://main.example/index.json", versionCode = 2),
                available(repoUrl = "https://mirror.example/index.json", versionCode = 1),
            ),
        )

        assertTrue(result.hasSameRepoUpdate)
        assertFalse(result.hasOtherRepoUpdate)
        assertEquals("https://main.example/index.json", result.sameRepoUpdate?.repoUrl)
    }

    @Test
    fun `other repo newer version requires reinstall and is not a regular update`() {
        val installed = installed(repoUrl = "https://main.example/index.json", versionCode = 1)
        val result = NovelPluginUpdateClassifier.classify(
            installed = installed,
            variants = listOf(
                available(repoUrl = "https://main.example/index.json", versionCode = 1),
                available(repoUrl = "https://mirror.example/index.json", versionCode = 2),
            ),
        )

        assertFalse(result.hasSameRepoUpdate)
        assertTrue(result.hasOtherRepoUpdate)
        assertNull(result.sameRepoUpdate)
        assertEquals("https://mirror.example/index.json", result.otherRepoUpdates.single().repoUrl)
    }

    @Test
    fun `same repo and other repo updates are exposed independently`() {
        val installed = installed(repoUrl = "https://main.example/index.json", versionCode = 1)
        val result = NovelPluginUpdateClassifier.classify(
            installed = installed,
            variants = listOf(
                available(repoUrl = "https://main.example/index.json", versionCode = 2),
                available(repoUrl = "https://mirror.example/index.json", versionCode = 3),
            ),
        )

        assertTrue(result.hasSameRepoUpdate)
        assertTrue(result.hasOtherRepoUpdate)
        assertEquals(2, result.sameRepoUpdate?.versionCode)
        assertEquals(3, result.otherRepoUpdates.single().versionCode)
    }

    @Test
    fun `older variants are ignored`() {
        val installed = installed(repoUrl = "https://main.example/index.json", versionCode = 5)
        val result = NovelPluginUpdateClassifier.classify(
            installed = installed,
            variants = listOf(
                available(repoUrl = "https://main.example/index.json", versionCode = 4),
                available(repoUrl = "https://mirror.example/index.json", versionCode = 5),
            ),
        )

        assertFalse(result.hasAnyUpdate)
    }

    @Test
    fun `blank installed repo uses single available repo as regular update`() {
        val result = NovelPluginUpdateClassifier.classify(
            installed = installed(repoUrl = "", versionCode = 1),
            variants = listOf(available(repoUrl = "https://main.example/index.json", versionCode = 2)),
        )

        assertTrue(result.hasSameRepoUpdate)
        assertFalse(result.hasOtherRepoUpdate)
        assertEquals("https://main.example/index.json", result.sameRepoUpdate?.repoUrl)
    }

    @Test
    fun `blank installed repo uses exact current version to identify other repo update`() {
        val result = NovelPluginUpdateClassifier.classify(
            installed = installed(repoUrl = "", versionCode = 1),
            variants = listOf(
                available(repoUrl = "https://main.example/index.json", versionCode = 1),
                available(repoUrl = "https://mirror.example/index.json", versionCode = 2),
            ),
        )

        assertFalse(result.hasSameRepoUpdate)
        assertTrue(result.hasOtherRepoUpdate)
        assertEquals("https://mirror.example/index.json", result.otherRepoUpdates.single().repoUrl)
    }

    private fun installed(
        repoUrl: String,
        versionCode: Int,
    ) = NovelPlugin.Installed(
        id = "plugin.id",
        name = "Plugin",
        site = "site",
        lang = "en",
        versionCode = versionCode,
        versionName = versionCode.toString(),
        url = "https://example.org",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "installed-sha",
        repoUrl = repoUrl,
    )

    private fun available(
        repoUrl: String,
        versionCode: Int,
    ) = NovelPlugin.Available(
        id = "plugin.id",
        name = "Plugin",
        site = "site",
        lang = "en",
        versionCode = versionCode,
        versionName = versionCode.toString(),
        url = "https://example.org",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "available-sha-$versionCode",
        repoUrl = repoUrl,
        repoName = repoUrl.substringAfter("//").substringBefore('/'),
    )
}
