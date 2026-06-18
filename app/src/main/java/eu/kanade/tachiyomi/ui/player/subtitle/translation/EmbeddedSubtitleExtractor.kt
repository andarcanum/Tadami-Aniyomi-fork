package eu.kanade.tachiyomi.ui.player.subtitle.translation

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface EmbeddedSubtitleExtractor {
    suspend fun extract(track: PlayerSubtitleTranslationTrack): ExtractedEmbeddedSubtitle
}

data class ExtractedEmbeddedSubtitle(
    val rawText: String,
    val formatHint: String,
    val sourceIdentity: String,
)

class UnsupportedEmbeddedSubtitleExtractor : EmbeddedSubtitleExtractor {
    override suspend fun extract(track: PlayerSubtitleTranslationTrack): ExtractedEmbeddedSubtitle {
        throw UnsupportedOperationException(
            "Embedded subtitle extraction is not available yet. External subtitle tracks are supported.",
        )
    }
}

data class LocalEmbeddedSubtitleExtractionRequest(
    val videoUri: Uri,
    val streamSpecifier: String,
    val outputFormat: SubtitleFormat = SubtitleFormat.Ass,
    val sourceIdentity: String = videoUri.toString(),
)

data class EmbeddedSubtitleStream(
    val order: Int,
    val streamIndex: Int,
    val language: String? = null,
    val title: String? = null,
) {
    val ffmpegSpecifier: String = "0:$streamIndex"
}

class FFprobeEmbeddedSubtitleStreamResolver(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun resolve(videoUri: Uri): List<EmbeddedSubtitleStream> {
        require(videoUri.scheme != "http" && videoUri.scheme != "https") {
            "Embedded subtitle stream probing supports local or downloaded files only"
        }
        val input = videoUri.toFFmpegString(context)
        val args = arrayOf(
            "-v",
            "error",
            "-select_streams",
            "s",
            "-show_entries",
            "stream=index:stream_tags=language,title",
            "-of",
            "json",
            input,
        )
        val session = suspendCancellableCoroutine { continuation ->
            val running = FFprobeKit.executeWithArgumentsAsync(args) { session ->
                if (session.returnCode.isValueSuccess) {
                    continuation.resume(session)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(session.output ?: "Failed to probe subtitle streams"),
                    )
                }
            }
            continuation.invokeOnCancellation { running.cancel() }
        }
        return parseProbeOutput(session.output.orEmpty())
    }

    fun parseProbeOutput(raw: String): List<EmbeddedSubtitleStream> = parseEmbeddedSubtitleProbeOutput(raw, json)
}

fun parseEmbeddedSubtitleProbeOutput(
    raw: String,
    json: Json = Json { ignoreUnknownKeys = true },
): List<EmbeddedSubtitleStream> {
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
    val streams = root["streams"]?.jsonArray ?: return emptyList()
    return streams.mapIndexedNotNull { order, element ->
        val obj = element as? JsonObject ?: element.jsonObject
        val streamIndex = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapIndexedNotNull null
        val tags = obj["tags"]?.jsonObject
        EmbeddedSubtitleStream(
            order = order,
            streamIndex = streamIndex,
            language = tags?.get("language")?.jsonPrimitive?.content,
            title = tags?.get("title")?.jsonPrimitive?.content,
        )
    }
}

class FFmpegEmbeddedSubtitleExtractor(
    private val context: Context,
    private val outputDir: File,
) {
    suspend fun extract(request: LocalEmbeddedSubtitleExtractionRequest): ExtractedEmbeddedSubtitle {
        require(request.videoUri.scheme != "http" && request.videoUri.scheme != "https") {
            "Embedded subtitle extraction supports local or downloaded files only"
        }
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFormat = when (request.outputFormat) {
            SubtitleFormat.Srt -> SubtitleFormat.Srt
            SubtitleFormat.Vtt -> SubtitleFormat.Vtt
            SubtitleFormat.Ass, SubtitleFormat.Unknown -> SubtitleFormat.Ass
        }
        val output = File(outputDir, "embedded-${System.nanoTime()}.${outputFormat.extension}")
        val input = request.videoUri.toFFmpegString(context)
        val args = arrayOf(
            "-y",
            "-i",
            input,
            "-map",
            request.streamSpecifier,
            "-c:s",
            when (outputFormat) {
                SubtitleFormat.Srt -> "srt"
                SubtitleFormat.Vtt -> "webvtt"
                SubtitleFormat.Ass, SubtitleFormat.Unknown -> "ass"
            },
            output.absolutePath,
        )

        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(args) { session ->
                if (session.returnCode.isValueSuccess && output.isFile) {
                    continuation.resume(Unit)
                } else {
                    output.delete()
                    continuation.resumeWithException(
                        IllegalStateException("Failed to extract embedded subtitle stream ${request.streamSpecifier}"),
                    )
                }
            }
            continuation.invokeOnCancellation {
                session.cancel()
                output.delete()
            }
        }

        return ExtractedEmbeddedSubtitle(
            rawText = output.readText(Charsets.UTF_8),
            formatHint = output.extension,
            sourceIdentity = "${request.sourceIdentity}#${request.streamSpecifier}",
        )
    }
}
