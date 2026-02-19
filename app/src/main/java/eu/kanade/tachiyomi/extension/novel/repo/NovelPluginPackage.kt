package eu.kanade.tachiyomi.extension.novel.repo

data class NovelPluginPackage(
    val entry: NovelPluginRepoEntry,
    val script: ByteArray,
    val customJs: ByteArray?,
    val customCss: ByteArray?,
)

class NovelPluginChecksumMismatch(expected: String, actual: String) :
    IllegalStateException("Plugin checksum mismatch (expected=$expected actual=$actual)")

class NovelPluginPackageFactory {
    fun create(
        entry: NovelPluginRepoEntry,
        script: ByteArray,
        customJs: ByteArray?,
        customCss: ByteArray?,
    ): Result<NovelPluginPackage> {
        val expected = entry.sha256.lowercase()
        val actual = eu.kanade.tachiyomi.util.lang.Hash.sha256(script)
        if (expected != actual) {
            return Result.failure(NovelPluginChecksumMismatch(expected, actual))
        }
        return Result.success(
            NovelPluginPackage(
                entry = entry,
                script = script,
                customJs = customJs,
                customCss = customCss,
            ),
        )
    }
}
