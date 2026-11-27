package eu.kanade.tachiyomi.ui.reader.novel.translation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationSessionResults
import eu.kanade.tachiyomi.data.translation.profile.withTranslationProfile
import eu.kanade.tachiyomi.data.translation.toTranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsController
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsHost
import eu.kanade.tachiyomi.ui.reader.novel.extractContentBlocks
import eu.kanade.tachiyomi.ui.reader.novel.normalizeStructuredChapterPayload
import eu.kanade.tachiyomi.ui.reader.novel.prependChapterHeadingIfMissing
import eu.kanade.tachiyomi.ui.reader.novel.sanitizeChapterHtmlForReader
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterSnapshot
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import tachiyomi.domain.translation.service.TranslationFragmentPlanner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NovelTtsControllerProfileTranslationTest {
    private val chapterId = 4242L
    private val novelId = 77L
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        TranslationSessionResults.store.clearChapter(chapterId)
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun teardown() {
        TranslationSessionResults.store.clearChapter(chapterId)
        testScope.cancel()
    }

    @Test
    fun `resolveTtsChapter enriches settings with active profile so session translation resolves`() =
        runBlocking<Unit> {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val prefs = NovelReaderPreferences(PersistingPreferenceStore(), Json { encodeDefaults = true }).apply {
                theme().set(NovelReaderTheme.LIGHT)
                geminiEnabled().set(true)
                geminiDisableCache().set(true)
                ttsPreferTranslatedText().set(true)
                geminiModel().set("legacy-gemini-model")
            }

            val novel = Novel.create().copy(id = novelId, source = 1L, title = "Novel", url = "/novel")
            val chapter = NovelChapter.create().copy(
                id = chapterId,
                novelId = novelId,
                name = "Chapter 1",
                url = "/ch1",
            )
            val rawHtml = "<p>Alpha paragraph</p>"
            val snapshot = NovelTtsChapterSnapshot(
                novel = novel,
                chapter = chapter,
                chapterOrderList = listOf(chapter),
                rawHtml = rawHtml,
                customCss = null,
                customJs = null,
                pluginSite = null,
                chapterWebUrl = null,
                contentBlocks = emptyList(),
                richContentBlocks = emptyList(),
                richContentUnsupportedFeaturesDetected = false,
                lastSavedIndex = 0,
                lastSavedScrollOffsetPx = 0,
                lastSavedWebProgressPercent = 0,
                lastSavedPageReaderProgress = null,
                previousChapterId = null,
                previousChapterName = null,
                nextChapterId = null,
                nextChapterName = null,
            )

            val ttsRepo = mockk<NovelTtsChapterRepository> {
                coEvery { loadChapterSnapshot(chapterId) } returns snapshot
            }
            val sourceManager = mockk<NovelSourceManager> {
                every { getOrStub(novel.source) } returns mockk<NovelSource>(relaxed = true)
            }
            val host = mockk<NovelTtsHost>(relaxed = true) {
                every { ttsScope } returns testScope
                every { ttsIsGeminiTranslationVisible() } returns false
                every { ttsIsGoogleTranslationVisible() } returns false
                every { ttsTranslationHolderEmpty(any()) } returns true
            }

            val connection = ConnectionProfile(
                id = "00000000-0000-0000-0000-000000000501",
                name = "Profile Endpoint",
                protocol = TranslationProtocol.OPENAI_CHAT,
                baseUrl = "https://custom-tts.example.com",
                auth = ProfileAuth.NONE,
            )
            val profile = TranslationProfile(
                id = "00000000-0000-0000-0000-000000000601",
                name = "TTS Profile",
                connectionId = connection.id,
                model = ModelSelection(
                    exactModelId = "custom/profile-tts-model",
                    reasoningContract = ReasoningContract.Levels(listOf("low", "high")),
                    generation = GenerationSettings(maxOutputTokens = 2048, reasoning = ReasoningChoice.Level("high")),
                    outputTokenField = OutputTokenField.MAX_COMPLETION_TOKENS,
                ),
                languages = TranslationLanguages("English", "Russian"),
            )

            val baseSettings = prefs.resolveSettings(novel.source)
            val frozen = baseSettings
                .withTranslationProfile(profile, connection)
                .toTranslationQueueProfileSnapshot(replaceRulesSnapshot = emptyList())
            val profileSettings = frozen.toReaderSettings(baseSettings)

            val queueManager = mockk<TranslationQueueManager> {
                coEvery { freezeProfileSnapshot(any(), novelId, any()) } returns frozen
            }

            val withHeading = prependChapterHeadingIfMissing(
                rawHtml = rawHtml.normalizeStructuredChapterPayload(),
                chapterName = chapter.name,
            )
            val preparedHtml = sanitizeChapterHtmlForReader(withHeading).ifBlank { withHeading }
            val source = extractContentBlocks(
                rawHtml = preparedHtml,
                chapterWebUrl = null,
                novelUrl = novel.url,
                pluginSite = null,
            ).filterIsInstance<NovelReaderScreenModel.ContentBlock.Text>().map { it.text }
            source.size shouldBe 2

            val translatedMap = mapOf(0 to "Глава 1", 1 to "Альфа абзац")
            val entry = GeminiTranslationCacheEntry(
                chapterId = chapterId,
                translatedByIndex = translatedMap,
                provider = profileSettings.translationProvider,
                model = profileSettings.translationCacheModelId(),
                sourceLang = profileSettings.geminiSourceLang,
                targetLang = profileSettings.geminiTargetLang,
                promptMode = GeminiPromptMode.entries.first { it.name == profile.prompt.modeId },
                stylePreset = NovelTranslationStylePreset.entries.first { it.name == profile.prompt.styleId },
                extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
                promptModifiersFingerprint = profileSettings.translationPromptModifiersFingerprint(),
                replaceRulesFingerprint = "",
                sourceSegmentCount = source.size,
                sourceFingerprint = TranslationFragmentPlanner.sourceFingerprint(source),
                generationFingerprint = profileSettings.translationGenerationFingerprint(),
            )
            val run = TranslationRun(
                id = 1L,
                chapterId = chapterId,
                novelId = novelId,
                batchToken = "tts-profile-batch",
                batchOrder = 0,
                profileSnapshotJson = null,
                status = TranslationRunStatus.COMPLETE,
                progress = 100,
                errorMessage = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                retryCount = 0,
                sessionOnly = true,
            )
            TranslationSessionResults.store.publish(run, entry)

            val controllerWithProfile = NovelTtsController(
                host = host,
                application = app,
                novelReaderPreferences = prefs,
                ttsChapterRepository = ttsRepo,
                sourceManager = sourceManager,
                translationQueueManager = queueManager,
            )
            val resolvedWithProfile = controllerWithProfile.resolveTtsChapter(chapterId).shouldNotBeNull()
            val translatedModel = resolvedWithProfile.translatedModel.shouldNotBeNull()
            val spokenText = translatedModel.utterances.joinToString("\n") { it.text }
            spokenText shouldContain "Глава 1"
            spokenText shouldContain "Альфа абзац"

            val controllerWithoutProfile = NovelTtsController(
                host = host,
                application = app,
                novelReaderPreferences = prefs,
                ttsChapterRepository = ttsRepo,
                sourceManager = sourceManager,
                translationQueueManager = null,
            )
            val resolvedWithoutProfile = controllerWithoutProfile.resolveTtsChapter(chapterId).shouldNotBeNull()
            resolvedWithoutProfile.translatedModel.shouldBeNull()
        }
}
