package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.ui.reader.novel.replace.ReplaceRule
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings

/** Use the admission snapshot for display/cache/TTS identity too; never resolve a secret for UI. */
internal suspend fun TranslationQueueManager.readerProfileSettings(
    base: NovelReaderSettings,
    novelId: Long,
    rules: List<ReplaceRule>,
): NovelReaderSettings {
    val public = base.copy(
        geminiApiKey = "",
        openRouterApiKey = "",
        deepSeekApiKey = "",
        mistralApiKey = "",
        nvidiaApiKey = "",
        ollamaCloudApiKey = "",
    )
    val snapshot = freezeProfileSnapshot(public, novelId, rules)
    return if (snapshot.snapshotVersion == 2) snapshot.toReaderSettings(public) else public
}
