package eu.kanade.tachiyomi.extension.novel.api

internal class NovelExtensionUpdateRunner(
    private val api: NovelExtensionApi = NovelExtensionApi(),
) {
    suspend fun run() {
        api.checkForUpdates()
    }
}
