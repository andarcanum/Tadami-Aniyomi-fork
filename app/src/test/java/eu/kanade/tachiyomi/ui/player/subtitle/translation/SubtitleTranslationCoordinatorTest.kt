package eu.kanade.tachiyomi.ui.player.subtitle.translation

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SubtitleTranslationCoordinatorTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `translates cues and reuses disk cache`() = runTest {
        var calls = 0
        val provider = object : SubtitleTranslationProvider {
            override val id = SubtitleTranslationProviderId.Google
            override val fingerprint = "fake-v1"
            override val supportsBatch = true

            override suspend fun translate(
                cues: List<SubtitleCue>,
                sourceLanguage: String,
                targetLanguage: String,
                onProgress: (SubtitleTranslationProgress) -> Unit,
            ): SubtitleTranslationProviderResult {
                calls += 1
                return SubtitleTranslationProviderResult.Success(
                    cues.associate { it.index to "${it.text} [$targetLanguage]" },
                )
            }
        }
        val coordinator = SubtitleTranslationCoordinator(
            providers = mapOf(SubtitleTranslationProviderId.Google to provider),
            cache = SubtitleTranslationDiskCache(tempDir),
        )
        val document = SubtitleDocument(
            format = SubtitleFormat.Srt,
            cues = listOf(SubtitleCue(1, 0, 1000, "Hello")),
        )
        val request = SubtitleTranslationRequest(
            document = document,
            sourceLanguage = "en",
            targetLanguage = "ru",
            providerId = SubtitleTranslationProviderId.Google,
            sourceIdentity = "episode-1",
        )

        val first = coordinator.translate(request)
        val second = coordinator.translate(request)

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(1, calls)
        assertEquals("Hello [ru]", first.document.cues.first().text)
    }
}
