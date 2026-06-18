package eu.kanade.tachiyomi.ui.browse.manga.extension

import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.toInstalledMangaExtensionPkgName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MangaExtensionRepoUpdateSelectionTest {

    @Test
    fun `unknown installed repo keeps reinstall candidates instead of filtering newest repo out`() {
        val installed = installed(repoUrl = null, versionCode = 1)
        val variants = listOf(
            available(repoUrl = "https://main.example/repo", versionCode = 1),
            available(repoUrl = "https://other.example/repo", versionCode = 2),
        )

        assertNull(selectMangaRegularUpdate(installed, variants))
        assertEquals(
            listOf("https://other.example/repo"),
            selectMangaReinstallCandidates(installed, variants).map { it.repoUrl },
        )
    }

    @Test
    fun `same repo update is selected without opening old repo picker`() {
        val installed = installed(repoUrl = "https://main.example/repo", versionCode = 1)
        val update = selectMangaRegularUpdate(
            extension = installed,
            variants = listOf(
                available(repoUrl = "https://main.example/repo", versionCode = 2),
                available(repoUrl = "https://other.example/repo", versionCode = 3),
            ),
        )

        assertEquals("https://main.example/repo", update?.repoUrl)
        assertEquals(2, update?.versionCode)
    }

    @Test
    fun `repo only update does not become a regular update`() {
        val installed = installed(repoUrl = "https://main.example/repo", versionCode = 1)
        val variants = listOf(
            available(repoUrl = "https://main.example/repo", versionCode = 1),
            available(repoUrl = "https://other.example/repo", versionCode = 2),
        )

        assertNull(selectMangaSameRepoUpdate(installed, variants))
        assertEquals(
            listOf("https://other.example/repo"),
            selectMangaReinstallCandidates(installed, variants).map { it.repoUrl },
        )
    }

    @Test
    fun `same new version from several repositories is a regular update without reinstall warning`() {
        val installed = installed(repoUrl = null, versionCode = 1)
        val variants = listOf(
            available(repoUrl = "https://main.example/repo", versionCode = 2),
            available(repoUrl = "https://other.example/repo", versionCode = 2),
        )

        assertEquals(2, selectMangaRegularUpdate(installed, variants)?.versionCode)
        assertEquals(emptyList<MangaExtension.Available>(), selectMangaReinstallCandidates(installed, variants))
    }

    @Test
    fun `reinstall candidates contain only the latest newer version`() {
        val installed = installed(repoUrl = "https://main.example/repo", versionCode = 1)
        val candidates = selectMangaReinstallCandidates(
            extension = installed,
            variants = listOf(
                available(repoUrl = "https://main.example/repo", versionCode = 1),
                available(repoUrl = "https://other.example/repo", versionCode = 2),
                available(repoUrl = "https://latest.example/repo", versionCode = 3),
            ),
        )

        assertEquals(listOf("https://latest.example/repo"), candidates.map { it.repoUrl })
    }

    @Test
    fun `display name uses installed repo url even when variants are empty`() {
        val installed = installed(repoUrl = "https://main.example/repo", versionCode = 1)

        assertEquals(
            "https://main.example/repo",
            selectMangaInstalledRepoDisplayName(
                extension = installed,
                variants = emptyList(),
            ),
        )
    }

    @Test
    fun `display name for regular update with saved installed repo does not switch to newer repo`() {
        val installed = installed(repoUrl = "https://main.example/repo", versionCode = 1)

        assertEquals(
            "https://main.example/repo",
            selectMangaInstalledRepoDisplayName(
                extension = installed,
                variants = listOf(
                    available(repoUrl = "https://main.example/repo", versionCode = 2),
                    available(repoUrl = "https://other.example/repo", versionCode = 3),
                ),
            ),
        )
    }

    @Test
    fun `display name uses installed repo name when it is known`() {
        val installed = installed(
            repoUrl = "https://main.example/repo",
            versionCode = 1,
            repoName = "Main Repo",
        )

        assertEquals(
            "Main Repo",
            selectMangaInstalledRepoDisplayName(
                extension = installed,
                variants = listOf(available(repoUrl = "https://other.example/repo", versionCode = 2)),
            ),
        )
    }

    @Test
    fun `display name for reinstall update stays on installed current version repo`() {
        val installed = installed(repoUrl = null, versionCode = 1)

        assertEquals(
            "main.example",
            selectMangaInstalledRepoDisplayName(
                extension = installed,
                variants = listOf(
                    available(repoUrl = "https://main.example/repo", versionCode = 1),
                    available(repoUrl = "https://other.example/repo", versionCode = 2),
                ),
            ),
        )
    }

    @Test
    fun `display name for regular update without exact current version is null when ambiguous`() {
        val installed = installed(repoUrl = null, versionCode = 1)

        assertNull(
            selectMangaInstalledRepoDisplayName(
                extension = installed,
                variants = listOf(
                    available(repoUrl = "https://main.example/repo", versionCode = 2),
                    available(repoUrl = "https://mirror.example/repo", versionCode = 2),
                ),
            ),
        )
    }

    @Test
    fun `display name is unknown instead of repo count when current version repo is ambiguous`() {
        val installed = installed(repoUrl = null, versionCode = 1)

        assertNull(
            selectMangaInstalledRepoDisplayName(
                extension = installed,
                variants = listOf(
                    available(repoUrl = "https://main.example/repo", versionCode = 1),
                    available(repoUrl = "https://mirror.example/repo", versionCode = 1),
                ),
            ),
        )
    }

    @Test
    fun `same version variants are not reinstall candidates`() {
        val installed = installed(repoUrl = "https://main.example/repo", versionCode = 2)
        val candidates = selectMangaReinstallCandidates(
            extension = installed,
            variants = listOf(
                available(repoUrl = "https://main.example/repo", versionCode = 2),
                available(repoUrl = "https://other.example/repo", versionCode = 2),
            ),
        )

        assertEquals(emptyList<MangaExtension.Available>(), candidates)
    }

    @Test
    fun `unknown installed repo uses single available repo as regular update`() {
        val installed = installed(repoUrl = null, versionCode = 1)
        val update = selectMangaRegularUpdate(
            extension = installed,
            variants = listOf(available(repoUrl = "https://main.example/repo", versionCode = 2)),
        )

        assertEquals("https://main.example/repo", update?.repoUrl)
        assertEquals(emptyList<MangaExtension.Available>(), selectMangaReinstallCandidates(installed, listOf(update!!)))
    }

    @Test
    fun `same repo regular update selects latest version from that repo`() {
        val installed = installed(repoUrl = "https://main.example/repo", versionCode = 1)
        val update = selectMangaRegularUpdate(
            extension = installed,
            variants = listOf(
                available(repoUrl = "https://main.example/repo", versionCode = 2),
                available(repoUrl = "https://main.example/repo", versionCode = 3),
                available(repoUrl = "https://other.example/repo", versionCode = 4),
            ),
        )

        assertEquals("https://main.example/repo", update?.repoUrl)
        assertEquals(3, update?.versionCode)
    }

    @Test
    fun `split UI package name is normalized to installed package name`() {
        assertEquals(
            "eu.kanade.tachiyomi.extension.en.example",
            "eu.kanade.tachiyomi.extension.en.example-123456789".toInstalledMangaExtensionPkgName(),
        )
    }

    @Test
    fun `non split package name is kept as installed package name`() {
        assertEquals(
            "eu.kanade.tachiyomi.extension.en.example-beta",
            "eu.kanade.tachiyomi.extension.en.example-beta".toInstalledMangaExtensionPkgName(),
        )
    }

    @Test
    fun `stale repo name without repo url does not block inferred display name`() {
        val displayName = selectMangaInstalledRepoDisplayName(
            extension = installed(repoUrl = null, repoName = "Stale Repo", versionCode = 1),
            variants = listOf(
                available(
                    repoUrl = "https://fresh.example/repo",
                    repoName = "Fresh Repo",
                    versionCode = 1,
                ),
            ),
        )

        assertEquals("Fresh Repo", displayName)
    }

    private fun installed(
        repoUrl: String?,
        versionCode: Long,
        repoName: String? = null,
    ) = MangaExtension.Installed(
        name = "Plugin",
        pkgName = "ext.plugin",
        versionName = versionCode.toString(),
        versionCode = versionCode,
        libVersion = 1.0,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        isShared = false,
        repoUrl = repoUrl,
        repoName = repoName,
    )

    private fun available(
        repoUrl: String,
        versionCode: Long,
        repoName: String = repoUrl.substringAfter("//").substringBefore('/'),
    ) = MangaExtension.Available(
        name = "Plugin",
        pkgName = "ext.plugin",
        versionName = versionCode.toString(),
        versionCode = versionCode,
        libVersion = 1.0,
        lang = "en",
        isNsfw = false,
        sources = emptyList(),
        apkName = "plugin.apk",
        iconUrl = "https://example/icon.png",
        repoUrl = repoUrl,
        repoName = repoName,
    )
}
