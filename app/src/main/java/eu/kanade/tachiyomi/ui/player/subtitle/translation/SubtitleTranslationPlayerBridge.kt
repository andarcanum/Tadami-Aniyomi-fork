package eu.kanade.tachiyomi.ui.player.subtitle.translation

import android.content.ContentResolver
import android.net.Uri
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class SubtitleTranslationPlayerBridge(
    private val networkHelper: NetworkHelper,
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
    private val coordinator: SubtitleTranslationCoordinator,
) {
    suspend fun translateExternalTrack(
        track: Track,
        sourceLanguage: String,
        targetLanguage: String,
        providerId: SubtitleTranslationProviderId = SubtitleTranslationProviderId.Google,
        sourceIdentity: String = track.url,
        useCache: Boolean = true,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        translateRawSubtitle(
            raw = readTrackText(track.url),
            formatHint = track.url,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            providerId = providerId,
            sourceIdentity = sourceIdentity,
            useCache = useCache,
            onProgress = onProgress,
        )
    }

    suspend fun translateRawSubtitle(
        raw: String,
        formatHint: String,
        sourceLanguage: String,
        targetLanguage: String,
        providerId: SubtitleTranslationProviderId = SubtitleTranslationProviderId.Google,
        sourceIdentity: String,
        useCache: Boolean = true,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        onProgress(SubtitleTranslationProgress(0, 0, SubtitleTranslationStage.Parsing))
        val document = SubtitleParser.parse(raw, formatHint)
        require(document.cues.isNotEmpty()) { "No subtitle cues found" }

        val normalizedTarget = targetLanguage.ifBlank { Locale.getDefault().language }.ifBlank { "en" }
        val result = coordinator.translate(
            SubtitleTranslationRequest(
                document = document,
                sourceLanguage = sourceLanguage.ifBlank { "auto" },
                targetLanguage = normalizedTarget,
                providerId = providerId,
                sourceIdentity = sourceIdentity,
                useCache = useCache,
            ),
            onProgress = onProgress,
        )

        val outputDir = File(cacheDir, "subtitle_translations/player").also { it.mkdirs() }
        val extension = result.document.format.takeIf { it != SubtitleFormat.Unknown }?.extension ?: "vtt"
        val output = File(outputDir, "${result.cacheKey}.$extension")
        output.writeText(SubtitleWriter.write(result.document), Charsets.UTF_8)
        output
    }

    private fun readTrackText(url: String): String {
        return when {
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> {
                networkHelper.client.newCall(GET(url)).execute().use { response ->
                    require(response.isSuccessful) { "Subtitle download failed: HTTP ${response.code}" }
                    response.body.string()
                }
            }
            url.startsWith("content://", ignoreCase = true) -> {
                contentResolver.openInputStream(Uri.parse(url))?.use { input ->
                    input.reader(Charsets.UTF_8).readText()
                } ?: error("Unable to open subtitle content URI")
            }
            url.startsWith("file://", ignoreCase = true) -> {
                File(Uri.parse(url).path.orEmpty()).readText(Charsets.UTF_8)
            }
            else -> File(url).readText(Charsets.UTF_8)
        }
    }
}
