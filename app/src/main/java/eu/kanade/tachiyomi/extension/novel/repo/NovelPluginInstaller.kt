package eu.kanade.tachiyomi.extension.novel.repo

interface NovelPluginDownloaderContract {
    suspend fun download(entry: NovelPluginRepoEntry): Result<NovelPluginPackage>
}

class NovelPluginInstaller(
    private val downloader: NovelPluginDownloaderContract,
    private val storage: NovelPluginStorage,
) {
    suspend fun install(entry: NovelPluginRepoEntry): Result<NovelPluginPackage> {
        val result = downloader.download(entry)
        return result.onSuccess { storage.save(it) }
    }
}
