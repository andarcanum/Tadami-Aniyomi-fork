package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginPackage
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoEntry
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import java.util.Date

class NovelReaderScreenModelTest {
    private val activeScreenModels = mutableListOf<NovelReaderScreenModel>()

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        activeScreenModels.clear()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupMainDispatcher() {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        @JvmStatic
        @AfterAll
        fun resetMainDispatcher() {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `loads chapter html from source`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("Hello") shouldBe true
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `loads chapter text off caller thread to avoid main-thread blocking`() {
        runBlocking {
            val callerThreadId = Thread.currentThread().id
            var sourceCallThreadId: Long? = null

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val sourceManager = FakeNovelSourceManager(
                sourceId = novel.source,
                chapterHtml = "<p>Hello</p>",
                onGetChapterText = { sourceCallThreadId = Thread.currentThread().id },
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = sourceManager,
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            (sourceCallThreadId == callerThreadId) shouldBe false
        }
    }

    @Test
    fun `injects chapter title heading when chapter html has no heading`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Том 1 Глава 0 - Система сил(?)",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("<h1 class=\"an-reader-chapter-title\">Том 1 Глава 0 - Система сил(?)</h1>") shouldBe true
            state.textBlocks.firstOrNull() shouldBe "Том 1 Глава 0 - Система сил(?)"
        }
    }

    @Test
    fun `does not prepend chapter title when html already contains heading`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<h1>Сайтовый заголовок</h1><p>Hello</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.textBlocks.firstOrNull() shouldBe "Сайтовый заголовок"
            state.textBlocks.contains("Chapter 1") shouldBe false
        }
    }

    @Test
    fun `builds mixed content blocks with text and resolved image urls`() {
        runBlocking {
            val novel = Novel.create().copy(
                id = 1L,
                source = 10L,
                title = "Novel",
                url = "https://example.org/book/slug",
            )
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/book/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Intro</p><img src=\"/images/pic.jpg\" /><p>Outro</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.contentBlocks.size shouldBe 4
            state.contentBlocks[0].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Text>().text shouldBe "Chapter 1"
            state.contentBlocks[1].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Text>().text shouldBe "Intro"
            state.contentBlocks[2].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Image>().url shouldBe "https://example.org/images/pic.jpg"
            state.contentBlocks[3].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Text>().text shouldBe "Outro"
        }
    }

    @Test
    fun `keeps custom novel plugin image scheme urls for on demand loading`() {
        runBlocking {
            val novel = Novel.create().copy(
                id = 1L,
                source = 10L,
                title = "Novel",
                url = "https://example.org/book/slug",
            )
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/book/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Intro</p><img src=\"novelimg://hexnovels?ref=chapter%2Fimg-1\" /><p>Outro</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.contentBlocks[2].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Image>().url shouldBe
                "novelimg://hexnovels?ref=chapter%2Fimg-1"
        }
    }

    @Test
    fun `structured json chapter payload is converted to html and bullet text blocks`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val structuredPayload = """
                {
                  "type": "doc",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [{ "type": "text", "text": "Intro" }]
                    },
                    {
                      "type": "bulletList",
                      "content": [
                        {
                          "type": "listItem",
                          "content": [
                            {
                              "type": "paragraph",
                              "content": [{ "type": "text", "text": "Bullet line" }]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = structuredPayload),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("<ul>") shouldBe true
            state.textBlocks shouldBe listOf("Chapter 1", "Intro", "\u2022 Bullet line")
        }
    }

    @Test
    fun `escaped structured json payload string is converted to html and bullet text blocks`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val structuredPayload = """
                {
                  "type": "doc",
                  "content": [
                    {
                      "type": "paragraph",
                      "content": [{ "type": "text", "text": "Intro" }]
                    },
                    {
                      "type": "bulletList",
                      "content": [
                        {
                          "type": "listItem",
                          "content": [
                            {
                              "type": "paragraph",
                              "content": [{ "type": "text", "text": "Bullet line" }]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
            val escapedPayload = Json.encodeToString(structuredPayload)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = escapedPayload),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("<ul>") shouldBe true
            state.textBlocks shouldBe listOf("Chapter 1", "Intro", "\u2022 Bullet line")
        }
    }

    @Test
    fun `json like structured payload with unquoted keys is normalized and rendered`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val jsonLikePayload =
                "{type:'doc',content:[{type:'paragraph',content:[{type:'text',text:'Intro'}]},{type:'bulletList',content:[{type:'listItem',content:[{type:'paragraph',content:[{type:'text',text:'Bullet line'}]}]}]}],}"

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = jsonLikePayload),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("<ul>") shouldBe true
            state.textBlocks shouldBe listOf("Chapter 1", "Intro", "\u2022 Bullet line")
        }
    }

    @Test
    fun `malformed structured payload falls back to extracted text blocks instead of raw json`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val malformedPayload =
                "{\"type: \"bulletList\", \"content: [{\"type\": \"listItem\", \"content\": [{\"type\": \"paragraph\", \"content\": [{\"type\": \"text\", \"text\": \"Магия в этом мире основана на математике и формулах\"}]}]}, {\"type\": \"listItem\", \"content\": [{\"type\": \"paragraph\", \"content\": [{\"type\": \"text\", \"text\": \"Второй пункт\"}]}]}]}"

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = malformedPayload),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.textBlocks shouldBe listOf(
                "Chapter 1",
                "\u2022 Магия в этом мире основана на математике и формулах",
                "\u2022 Второй пункт",
            )
            state.html.contains("type:") shouldBe false
        }
    }

    @Test
    fun `html payload keeps normal paragraphs and recovers malformed list fragment`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val htmlWithMalformedFragment =
                "<div><p>Эта информация не обязательна для понимания.</p><p>{\"type: \"bulletList\", \"content: [{\"type\": \"listItem\", \"content\": [{\"type\": \"paragraph\", \"content\": [{\"type\": \"text\", \"text\": \"Магия в этом мире основана на математике и формулах\"}]}]}]}</p><p>Финальная строка.</p></div>"

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = htmlWithMalformedFragment),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.textBlocks shouldBe listOf(
                "Chapter 1",
                "Эта информация не обязательна для понимания.",
                "\u2022 Магия в этом мире основана на математике и формулах",
                "Финальная строка.",
            )
        }
    }

    @Test
    fun `loads custom js and css for plugin source`() {
        runBlocking {
            val pluginId = "plugin.test"
            val entry = NovelPluginRepoEntry(
                id = pluginId,
                name = "Plugin",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "ignored",
            )
            val customJs = "console.log('custom');"
            val customCss = "body { color: red; }"
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "console.log('main');".toByteArray(),
                customJs = customJs.toByteArray(),
                customCss = customCss.toByteArray(),
            )

            val novel = Novel.create().copy(id = 1L, source = pluginId.hashCode().toLong(), title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(listOf(pkg)),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.enableJs shouldBe true
            state.html.contains(customJs) shouldBe true
            state.html.contains(customCss) shouldBe true
            state.lastSavedIndex shouldBe 0
            state.chapterWebUrl shouldBe "https://example.org/ch1"
            Unit
        }
    }

    @Test
    fun `resolves chapter web url from plugin site when chapter path is relative`() {
        runBlocking {
            val pluginId = "plugin.relative"
            val entry = NovelPluginRepoEntry(
                id = pluginId,
                name = "Plugin",
                site = "example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "ignored",
            )
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "console.log('main');".toByteArray(),
                customJs = null,
                customCss = null,
            )

            val novel = Novel.create().copy(id = 1L, source = pluginId.hashCode().toLong(), title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "/book/chapter-1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(listOf(pkg)),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.chapterWebUrl shouldBe "https://example.org/book/chapter-1"
            Unit
        }
    }

    @Test
    fun `uses source chapter web url resolver when available`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "book-slug/1/1/0",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello</p>",
                    chapterWebUrlResolver = { chapterPath, _ ->
                        if (chapterPath == "book-slug/1/1/0") {
                            "https://ranobelib.me/ru/book-slug/read/v1/c1?bid=0"
                        } else {
                            null
                        }
                    },
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.chapterWebUrl shouldBe "https://ranobelib.me/ru/book-slug/read/v1/c1?bid=0"
            Unit
        }
    }

    @Test
    fun `applies reader settings to html`() {
        runBlocking {
            val store = InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_font_size",
                        20,
                        NovelReaderPreferences.DEFAULT_FONT_SIZE,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_line_height",
                        1.8f,
                        NovelReaderPreferences.DEFAULT_LINE_HEIGHT,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_margins",
                        24,
                        NovelReaderPreferences.DEFAULT_MARGIN,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_theme",
                        NovelReaderTheme.LIGHT,
                        NovelReaderTheme.SYSTEM,
                    ),
                ),
            )
            val prefs = NovelReaderPreferences(
                preferenceStore = store,
                json = Json { encodeDefaults = true },
            )

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("font-size: 20px") shouldBe true
            state.html.contains("line-height: 1.8") shouldBe true
            state.html.contains("padding: 24px") shouldBe true
            state.readerSettings.fontSize shouldBe 20
            state.readerSettings.lineHeight shouldBe 1.8f
            state.readerSettings.margin shouldBe 24
            state.readerSettings.theme shouldBe NovelReaderTheme.LIGHT
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `reuses parsed content blocks when settings change`() {
        runBlocking {
            val store = ReactivePreferenceStore()
            val prefs = NovelReaderPreferences(
                preferenceStore = store,
                json = Json { encodeDefaults = true },
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Intro</p><p>Outro</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            try {
                withTimeout(1_000) {
                    while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                        yield()
                    }
                }

                val initialState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                val initialBlocks = initialState.contentBlocks

                prefs.fontSize().set(22)

                withTimeout(1_000) {
                    while (true) {
                        val state = screenModel.state.value
                        if (state is NovelReaderScreenModel.State.Success && state.readerSettings.fontSize == 22) {
                            break
                        }
                        yield()
                    }
                }

                val updatedState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                (updatedState.contentBlocks === initialBlocks) shouldBe true
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `missing chapter shows error state`() {
        runBlocking {
            val screenModel = trackedNovelReaderScreenModel(
                chapterId = 99L,
                novelChapterRepository = FakeNovelChapterRepository(null),
                getNovel = GetNovel(FakeNovelRepository(Novel.create())),
                sourceManager = FakeNovelSourceManager(sourceId = 10L, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Error>()
            Unit
        }
    }

    @Test
    fun `update reading progress marks read near end`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 9, totalItems = 10)
            yield()

            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 0L
        }
    }

    @Test
    fun `percent based tracking does not mark read too early`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 95, totalItems = 100, persistedProgress = 95L)
            yield()
            chapterRepo.lastUpdate?.read shouldBe false
            chapterRepo.lastUpdate?.lastPageRead shouldBe 95L

            screenModel.updateReadingProgress(currentIndex = 99, totalItems = 100, persistedProgress = 99L)
            yield()
            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 0L
        }
    }

    @Test
    fun `encoded webview progress is restored as percent without affecting native index`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = encodeWebScrollProgressPercent(42),
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.lastSavedWebProgressPercent shouldBe 42
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `progress callback after read completion does not reset chapter to unread`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            // WebView/native can emit low/stale progress on disposal; this must not unread chapter.
            screenModel.updateReadingProgress(currentIndex = 99, totalItems = 100)
            yield()
            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 0L

            screenModel.updateReadingProgress(currentIndex = 0, totalItems = 100)
            yield()

            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 0L
        }
    }

    @Test
    fun `records history when chapter is opened and progressed`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)
            val historyRepository = FakeNovelHistoryRepository()

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                historyRepository = historyRepository,
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            historyRepository.lastUpdate?.chapterId shouldBe chapter.id

            screenModel.updateReadingProgress(currentIndex = 1, totalItems = 10)
            yield()

            historyRepository.lastUpdate?.chapterId shouldBe chapter.id
            (historyRepository.lastUpdate?.readAt != null) shouldBe true
        }
    }

    @Test
    fun `initial progress callback at saved index does not update chapter`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = 3L,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 3, totalItems = 10)
            yield()

            chapterRepo.lastUpdate shouldBe null
        }
    }

    @Test
    fun `repeated callback at saved index does not mark chapter read`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = 8L,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            // Initial callback and dispose callback with unchanged index should not update.
            screenModel.updateReadingProgress(currentIndex = 8, totalItems = 10)
            screenModel.updateReadingProgress(currentIndex = 8, totalItems = 10)
            yield()
            chapterRepo.lastUpdate shouldBe null

            // Actual progress change can mark chapter as read near the end.
            screenModel.updateReadingProgress(currentIndex = 9, totalItems = 10)
            yield()
            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 0L
        }
    }

    @Test
    fun `native progress update at same index persists changed scroll offset`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = encodeNativeScrollProgress(index = 0, offsetPx = 0),
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val updatedProgress = encodeNativeScrollProgress(index = 0, offsetPx = 420)
            screenModel.updateReadingProgress(
                currentIndex = 0,
                totalItems = 10,
                persistedProgress = updatedProgress,
            )
            yield()

            chapterRepo.lastUpdate?.read shouldBe false
            chapterRepo.lastUpdate?.lastPageRead shouldBe updatedProgress
        }
    }

    @Test
    fun `computes previous and next chapter ids from source order`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter1 = NovelChapter.create().copy(
                id = 1L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                sourceOrder = 0L,
            )
            val chapter2 = NovelChapter.create().copy(
                id = 2L,
                novelId = 1L,
                name = "Chapter 2",
                url = "https://example.org/ch2",
                sourceOrder = 1L,
            )
            val chapter3 = NovelChapter.create().copy(
                id = 3L,
                novelId = 1L,
                name = "Chapter 3",
                url = "https://example.org/ch3",
                sourceOrder = 2L,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter2, listOf(chapter1, chapter2, chapter3))

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter2.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.previousChapterId shouldBe chapter1.id
            state.nextChapterId shouldBe chapter3.id
            chapterRepo.lastApplyScanlatorFilter shouldBe true
        }
    }

    @Test
    fun `toggle chapter bookmark updates repository and state`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                bookmark = false,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.toggleChapterBookmark()
            yield()

            chapterRepo.lastUpdate?.bookmark shouldBe true
            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.chapter.bookmark shouldBe true
        }
    }

    private class FakeNovelChapterRepository(
        private val chapter: NovelChapter?,
        private val chaptersByNovel: List<NovelChapter> = emptyList(),
    ) : NovelChapterRepository {
        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        var lastUpdate: NovelChapterUpdate? = null
        var lastApplyScanlatorFilter: Boolean? = null
        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) {
            lastUpdate = chapterUpdate
        }
        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(novelId: Long, applyScanlatorFilter: Boolean): List<NovelChapter> {
            lastApplyScanlatorFilter = applyScanlatorFilter
            return chaptersByNovel
        }
        override suspend fun getScanlatorsByNovelId(novelId: Long) = chaptersByNovel.mapNotNull { it.scanlator }
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long) = emptyList<NovelChapter>()
        override suspend fun getChapterById(id: Long): NovelChapter? = chapter?.takeIf { it.id == id }
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
    }

    private class FakeNovelPluginStorage(
        private val packages: List<NovelPluginPackage>,
    ) : NovelPluginStorage {
        override suspend fun save(pkg: NovelPluginPackage) = Unit
        override suspend fun get(id: String): NovelPluginPackage? =
            packages.firstOrNull { it.entry.id == id }
        override suspend fun getAll(): List<NovelPluginPackage> = packages
    }

    private class FakeNovelHistoryRepository : NovelHistoryRepository {
        var lastUpdate: NovelHistoryUpdate? = null

        override fun getNovelHistory(query: String): Flow<List<NovelHistoryWithRelations>> =
            MutableStateFlow(emptyList())

        override suspend fun getLastNovelHistory(): NovelHistoryWithRelations? = null

        override suspend fun getTotalReadDuration(): Long = 0L

        override suspend fun getHistoryByNovelId(novelId: Long): List<NovelHistory> = emptyList()

        override suspend fun resetNovelHistory(historyId: Long) = Unit

        override suspend fun resetHistoryByNovelId(novelId: Long) = Unit

        override suspend fun deleteAllNovelHistory(): Boolean = true

        override suspend fun upsertNovelHistory(historyUpdate: NovelHistoryUpdate) {
            lastUpdate = historyUpdate
        }
    }

    private fun createNovelReaderPreferences(): NovelReaderPreferences {
        return NovelReaderPreferences(
            preferenceStore = InMemoryPreferenceStore(),
            json = Json { encodeDefaults = true },
        )
    }

    private fun trackedNovelReaderScreenModel(
        chapterId: Long,
        novelChapterRepository: NovelChapterRepository,
        getNovel: GetNovel,
        sourceManager: NovelSourceManager,
        pluginStorage: NovelPluginStorage,
        novelReaderPreferences: NovelReaderPreferences,
        isSystemDark: () -> Boolean,
        historyRepository: NovelHistoryRepository = FakeNovelHistoryRepository(),
    ): NovelReaderScreenModel {
        return NovelReaderScreenModel(
            chapterId = chapterId,
            novelChapterRepository = novelChapterRepository,
            getNovel = getNovel,
            sourceManager = sourceManager,
            pluginStorage = pluginStorage,
            historyRepository = historyRepository,
            novelReaderPreferences = novelReaderPreferences,
            isSystemDark = isSystemDark,
        ).also(activeScreenModels::add)
    }

    private class ReactivePreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, Any?>()
        private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

        override fun getString(key: String, defaultValue: String): Preference<String> = createPreference(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> = createPreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> = createPreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = createPreference(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = createPreference(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            createPreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = createPreference(key, defaultValue)

        override fun getAll(): Map<String, *> = values.toMap()

        @Suppress("UNCHECKED_CAST")
        private fun <T> createPreference(key: String, defaultValue: T): Preference<T> {
            val stateFlow = flows.getOrPut(key) {
                MutableStateFlow(values[key] ?: defaultValue)
            }
            return object : Preference<T> {
                override fun key(): String = key

                override fun get(): T = (values[key] as T?) ?: (stateFlow.value as T?) ?: defaultValue

                override fun set(value: T) {
                    values[key] = value
                    stateFlow.value = value
                }

                override fun isSet(): Boolean = values.containsKey(key)

                override fun delete() {
                    values.remove(key)
                    stateFlow.value = defaultValue
                }

                override fun defaultValue(): T = defaultValue

                override fun changes(): Flow<T> = stateFlow.map { (it as T?) ?: defaultValue }

                override fun stateIn(scope: CoroutineScope) =
                    changes().stateIn(scope, SharingStarted.Eagerly, get())
            }
        }
    }

    private class FakeNovelRepository(
        private val novel: Novel,
    ) : NovelRepository {
        override suspend fun getNovelById(id: Long): Novel = novel
        override suspend fun getNovelByIdAsFlow(id: Long) = MutableStateFlow(novel)
        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) = MutableStateFlow<Novel?>(null)
        override suspend fun getNovelFavorites(): List<Novel> = emptyList()
        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
        override suspend fun getLibraryNovel(): List<LibraryNovel> = emptyList()
        override fun getLibraryNovelAsFlow() = MutableStateFlow(emptyList<LibraryNovel>())
        override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())
        override suspend fun insertNovel(novel: Novel): Long? = null
        override suspend fun updateNovel(update: NovelUpdate): Boolean = true
        override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean = true
        override suspend fun resetNovelViewerFlags(): Boolean = true
    }

    private class FakeNovelSourceManager(
        private val sourceId: Long,
        private val chapterHtml: String,
        private val chapterWebUrlResolver: ((String, String?) -> String?)? = null,
        private val onGetChapterText: (() -> Unit)? = null,
    ) : NovelSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources =
            MutableStateFlow(emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>())
        override fun get(sourceKey: Long): NovelSource? =
            if (sourceKey == sourceId) {
                FakeNovelSource(
                    id = sourceId,
                    chapterHtml = chapterHtml,
                    chapterWebUrlResolver = chapterWebUrlResolver,
                    onGetChapterText = onGetChapterText,
                )
            } else {
                null
            }
        override fun getOrStub(sourceKey: Long): NovelSource =
            get(sourceKey) ?: object : NovelSource {
                override val id: Long = sourceKey
                override val name: String = "Stub"
            }
        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource>()
        override fun getCatalogueSources() = emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>()
        override fun getStubSources() = emptyList<StubNovelSource>()
    }

    private class FakeNovelSource(
        override val id: Long,
        private val chapterHtml: String,
        private val chapterWebUrlResolver: ((String, String?) -> String?)? = null,
        private val onGetChapterText: (() -> Unit)? = null,
    ) : NovelSource, NovelWebUrlSource {
        override val name: String = "NovelSource"

        override suspend fun getChapterText(chapter: SNovelChapter): String {
            onGetChapterText?.invoke()
            return chapterHtml
        }

        override suspend fun getNovelWebUrl(novelPath: String): String? = null

        override suspend fun getChapterWebUrl(chapterPath: String, novelPath: String?): String? {
            return chapterWebUrlResolver?.invoke(chapterPath, novelPath)
        }
    }
}


