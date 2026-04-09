package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.Locale

class NovelTtsEngineTest {

    @Test
    fun `initialize uses selected engine package and exposes platform voices`() {
        runBlocking {
            val platformEngine = FakePlatformEngine(
                voices = listOf(
                    NovelTtsVoiceDescriptor(
                        id = "voice-1",
                        name = "English",
                        localeTag = "en-US",
                    ),
                ),
                locales = listOf(Locale.US),
            )
            val engine = NovelTtsEngine(
                platformFactory = FakePlatformFactory(platformEngine),
            )

            engine.initialize("engine.rhvoice")

            platformEngine.initializedWithPackage shouldBe "engine.rhvoice"
            engine.availableVoices().shouldHaveSize(1)
        }
    }

    @Test
    fun `availableVoices falls back to locale derived descriptors when platform voices are empty`() = runBlocking {
        val engine = NovelTtsEngine(
            platformFactory = FakePlatformFactory(
                FakePlatformEngine(
                    voices = emptyList(),
                    locales = listOf(Locale.US, Locale("ru", "RU")),
                ),
            ),
        )

        engine.initialize("engine.default")

        engine.availableVoices().map { it.localeTag } shouldContainExactly listOf("en-US", "ru-RU")
    }

    @Test
    fun `availableLocales falls back to voice locales when locale enumeration is empty`() = runBlocking {
        val engine = NovelTtsEngine(
            platformFactory = FakePlatformFactory(
                FakePlatformEngine(
                    voices = listOf(
                        NovelTtsVoiceDescriptor(id = "a", name = "A", localeTag = "en-US"),
                        NovelTtsVoiceDescriptor(id = "b", name = "B", localeTag = "ja-JP"),
                    ),
                    locales = emptyList(),
                ),
            ),
        )

        engine.initialize("engine.default")

        engine.availableLocales() shouldContainExactly listOf("en-US", "ja-JP")
    }

    @Test
    fun `capabilities reflect platform feature support`() {
        runBlocking {
            val engine = NovelTtsEngine(
                platformFactory = FakePlatformFactory(
                    FakePlatformEngine(
                        voices = emptyList(),
                        locales = emptyList(),
                        supportsExactWordOffsets = true,
                        supportsReliablePauseResume = false,
                        supportsVoiceEnumeration = false,
                        supportsLocaleEnumeration = false,
                    ),
                ),
            )

            engine.initialize("engine.default")

            engine.capabilities() shouldBe NovelTtsEngineCapabilities(
                supportsExactWordOffsets = true,
                supportsReliablePauseResume = false,
                supportsVoiceEnumeration = false,
                supportsLocaleEnumeration = false,
            )
        }
    }

    @Test
    fun `initialize switches platform engine when package changes and refreshes voices`() = runBlocking {
        val platformEngine = FakePlatformEngine(
            voicesByPackage = mapOf(
                "engine.default" to listOf(
                    NovelTtsVoiceDescriptor(id = "en", name = "English", localeTag = "en-US"),
                ),
                "engine.rhvoice" to listOf(
                    NovelTtsVoiceDescriptor(id = "zh", name = "Chinese", localeTag = "zh-CN"),
                ),
            ),
            localesByPackage = mapOf(
                "engine.default" to listOf(Locale.US),
                "engine.rhvoice" to listOf(Locale.SIMPLIFIED_CHINESE),
            ),
        )
        val engine = NovelTtsEngine(
            platformFactory = FakePlatformFactory(platformEngine),
        )

        engine.initialize("engine.default")
        engine.initialize("engine.rhvoice")

        platformEngine.initializedPackages shouldContainExactly listOf("engine.default", "engine.rhvoice")
        platformEngine.shutdownCount shouldBe 1
        engine.availableVoices().map { it.localeTag } shouldContainExactly listOf("zh-CN")
    }

    private class FakePlatformFactory(
        private val engine: FakePlatformEngine,
    ) : NovelTtsPlatformFactory {
        override fun create(): NovelTtsPlatformEngine = engine
    }

    private class FakePlatformEngine(
        private val voices: List<NovelTtsVoiceDescriptor> = emptyList(),
        private val locales: List<Locale> = emptyList(),
        private val supportsExactWordOffsets: Boolean = false,
        private val supportsReliablePauseResume: Boolean = true,
        private val supportsVoiceEnumeration: Boolean = voices.isNotEmpty(),
        private val supportsLocaleEnumeration: Boolean = locales.isNotEmpty(),
        private val voicesByPackage: Map<String?, List<NovelTtsVoiceDescriptor>> = emptyMap(),
        private val localesByPackage: Map<String?, List<Locale>> = emptyMap(),
    ) : NovelTtsPlatformEngine {
        var initializedWithPackage: String? = null
        val initializedPackages = mutableListOf<String?>()
        var shutdownCount: Int = 0
        private var currentPackage: String? = null

        override suspend fun initialize(enginePackageName: String?) {
            initializedWithPackage = enginePackageName
            initializedPackages += enginePackageName
            currentPackage = enginePackageName
        }

        override fun setProgressListener(listener: NovelTtsPlaybackProgressListener?) = Unit

        override fun availableVoices(): List<NovelTtsVoiceDescriptor> {
            return voicesByPackage[currentPackage] ?: voices
        }

        override fun availableLocales(): List<Locale> {
            return localesByPackage[currentPackage] ?: locales
        }

        override fun capabilities(): NovelTtsEngineCapabilities {
            return NovelTtsEngineCapabilities(
                supportsExactWordOffsets = supportsExactWordOffsets,
                supportsReliablePauseResume = supportsReliablePauseResume,
                supportsVoiceEnumeration = supportsVoiceEnumeration,
                supportsLocaleEnumeration = supportsLocaleEnumeration,
            )
        }

        override suspend fun setVoice(voiceId: String?) = Unit
        override suspend fun setLocale(localeTag: String?) = Unit
        override suspend fun setSpeechRate(rate: Float) = Unit
        override suspend fun setPitch(pitch: Float) = Unit
        override suspend fun speak(utteranceId: String, text: String, flushQueue: Boolean) = Unit
        override fun stop() = Unit
        override fun shutdown() {
            shutdownCount += 1
            currentPackage = null
        }
    }
}
