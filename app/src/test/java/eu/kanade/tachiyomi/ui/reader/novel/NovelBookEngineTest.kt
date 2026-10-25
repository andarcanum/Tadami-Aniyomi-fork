package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookEngineTest {

    @Test
    fun `opening a book loads the requested section as its own renderer document`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 100),
        )
        val renderer = RecordingNovelBookEngineRenderer()
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )

        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 50),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        renderer.openedDocument shouldBe NovelBookDocument(
            sectionIndex = 1,
            chapterId = 2L,
            html = "<p>Chapter 2 text</p>",
        )
        renderer.openedLocation shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 50)
        renderer.openedFlow shouldBe NovelBookEngineFlow.PAGINATED
    }

    @Test
    fun `next at the end of a renderer document opens the next spine section`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 100),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            nextResult = NovelBookPageTurnResult.EndOfDocument
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation.START,
            flow = NovelBookEngineFlow.PAGINATED,
        )

        engine.next()

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(1L, 2L)
        renderer.openedLocation shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 0)
        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 0)
    }

    @Test
    fun `previous at the start of a renderer document opens the end of the previous spine section`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 200),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            previousResult = NovelBookPageTurnResult.StartOfDocument
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 0),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        engine.previous()

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(2L, 1L)
        renderer.openedLocation shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 99)
        engine.location shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 99)
    }

    @Test
    fun `switching flow reopens the same section at the exact book location`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 200),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            nextResult = NovelBookPageTurnResult.Moved(charOffset = 73)
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 25),
            flow = NovelBookEngineFlow.PAGINATED,
        )
        engine.next()

        engine.setFlow(NovelBookEngineFlow.SCROLLED)

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(2L, 2L)
        renderer.openedLocations shouldBe listOf(
            NovelBookLocation(sectionIndex = 1, charOffset = 25),
            NovelBookLocation(sectionIndex = 1, charOffset = 73),
        )
        renderer.openedFlow shouldBe NovelBookEngineFlow.SCROLLED
        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 73)
    }

    @Test
    fun `goTo adopts the renderer-acknowledged location instead of the requested one`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(NovelChapter.create().copy(id = 1L, name = "Chapter 1")),
            charCounts = mapOf(1L to 100),
        )
        val relocatedLocations = mutableListOf<NovelBookLocation>()
        val renderer = RecordingNovelBookEngineRenderer().apply {
            relocateResult = NovelBookPageTurnResult.Moved(charOffset = 83)
        }
        val engine = NovelBookEngine(
            loadDocument = { section -> documentFor(section) },
            renderer = renderer,
            onLocationChanged = relocatedLocations::add,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 0, charOffset = 10),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        engine.goTo(NovelBookLocation(sectionIndex = 0, charOffset = 25))

        engine.location shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 83)
        relocatedLocations.last() shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 83)
    }

    @Test
    fun `reloading reopens the same section at the exact book location`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 200),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            relocateResult = NovelBookPageTurnResult.Moved(charOffset = 83)
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 25),
            flow = NovelBookEngineFlow.PAGINATED,
        )
        engine.flushLocation()

        engine.reload()

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(2L, 2L)
        renderer.openedLocations shouldBe listOf(
            NovelBookLocation(sectionIndex = 1, charOffset = 25),
            NovelBookLocation(sectionIndex = 1, charOffset = 83),
        )
        renderer.openedFlow shouldBe NovelBookEngineFlow.PAGINATED
        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 83)
    }

    @Test
    fun `renderer relocation updates and publishes the exact book location`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 200),
        )
        val relocatedLocations = mutableListOf<NovelBookLocation>()
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = RecordingNovelBookEngineRenderer(),
            onLocationChanged = relocatedLocations::add,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 25),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        engine.onRendererRelocated(charOffset = 88)

        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 88)
        relocatedLocations shouldBe listOf(NovelBookLocation(sectionIndex = 1, charOffset = 88))
    }

    @Test
    fun `flushing queries the renderer and publishes its exact book location`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
            ),
            charCounts = mapOf(1L to 100),
        )
        val relocatedLocations = mutableListOf<NovelBookLocation>()
        val renderer = RecordingNovelBookEngineRenderer().apply {
            relocateResult = NovelBookPageTurnResult.Moved(charOffset = 77)
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
            onLocationChanged = relocatedLocations::add,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 0, charOffset = 10),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        engine.flushLocation()

        engine.location shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 77)
        relocatedLocations shouldBe listOf(NovelBookLocation(sectionIndex = 0, charOffset = 77))
    }

    @Test
    fun `reaching the end of a scrolled document stitches the next chapter into the same document`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 100),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            stitchingSupported = true
            nextResult = NovelBookPageTurnResult.EndOfDocument
        }
        val engine = NovelBookEngine(
            loadDocument = { section -> documentFor(section) },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation.START,
            flow = NovelBookEngineFlow.SCROLLED,
        )

        engine.next()

        // The document was never replaced, which is what removes the jump between chapters.
        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(1L)
        renderer.appendedDocuments.map { it.chapterId } shouldBe listOf(2L)
    }

    @Test
    fun `scrolling back into the previous chapter stitches it above the current one`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 100),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply { stitchingSupported = true }
        val engine = NovelBookEngine(
            loadDocument = { section -> documentFor(section) },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 0),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        engine.stitch(forward = false) shouldBe true
        engine.stitch(forward = false) shouldBe false

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(2L)
        renderer.prependedDocuments.map { it.chapterId } shouldBe listOf(1L)
    }

    @Test
    fun `a stitched document reports which chapter the viewport is in`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            charCounts = mapOf(1L to 100, 2L to 100),
        )
        val relocatedLocations = mutableListOf<NovelBookLocation>()
        val renderer = RecordingNovelBookEngineRenderer().apply { stitchingSupported = true }
        val engine = NovelBookEngine(
            loadDocument = { section -> documentFor(section) },
            renderer = renderer,
            onLocationChanged = relocatedLocations::add,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation.START,
            flow = NovelBookEngineFlow.SCROLLED,
        )
        engine.stitch(forward = true) shouldBe true

        engine.onRendererRelocated(charOffset = 12, sectionIndex = 1)

        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 12)
        relocatedLocations shouldBe listOf(NovelBookLocation(sectionIndex = 1, charOffset = 12))
    }

    @Test
    fun `stitching drops chapters the reader left far behind`() = runTest {
        val spine = testSpineOf(
            chapters = (1L..7L).map { id ->
                NovelChapter.create().copy(id = id, name = "Chapter ${'$'}id")
            },
            charCounts = (1L..7L).associateWith { 100 },
        )
        val renderer = RecordingNovelBookEngineRenderer().apply { stitchingSupported = true }
        val engine = NovelBookEngine(
            loadDocument = { section -> documentFor(section) },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation.START,
            flow = NovelBookEngineFlow.SCROLLED,
        )
        repeat(5) { engine.stitch(forward = true) }

        // The reader has scrolled into the fourth chapter, so the first ones can be dropped.
        engine.onRendererRelocated(charOffset = 10, sectionIndex = 3)
        engine.stitch(forward = true) shouldBe true

        renderer.appendedDocuments.map { it.chapterId } shouldBe listOf(2L, 3L, 4L, 5L, 6L, 7L)
        renderer.removedSections shouldBe listOf(0, 1)
    }

    @Test
    fun `the chapter the reader is in is never dropped from the document`() = runTest {
        val spine = testSpineOf(
            chapters = (1L..7L).map { id ->
                NovelChapter.create().copy(id = id, name = "Chapter ${'$'}id")
            },
            charCounts = (1L..7L).associateWith { 100 },
        )
        val renderer = RecordingNovelBookEngineRenderer().apply { stitchingSupported = true }
        val engine = NovelBookEngine(
            loadDocument = { section -> documentFor(section) },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation.START,
            flow = NovelBookEngineFlow.SCROLLED,
        )

        repeat(6) { engine.stitch(forward = true) }

        renderer.removedSections shouldBe emptyList()
    }

    @Test
    fun `a measured section is restored from its exact offset`() = runTest {
        val spine = testSpineOf(
            chapters = listOf(NovelChapter.create().copy(id = 1L, name = "Chapter 1")),
            charCounts = mapOf(1L to 5_000),
        )
        val renderer = RecordingNovelBookEngineRenderer()
        val engine = NovelBookEngine(
            loadDocument = { section -> documentFor(section) },
            renderer = renderer,
        )

        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 0, charOffset = 2_000),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        renderer.openedRestoreFraction shouldBe null
        renderer.openedLocation shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 2_000)
    }

    private fun documentFor(section: NovelBookSection): NovelBookDocument = NovelBookDocument(
        sectionIndex = section.index,
        chapterId = section.chapterId,
        html = "<p>${'$'}{section.name} text</p>",
    )

    private class RecordingNovelBookEngineRenderer : NovelBookEngineRenderer {
        val openedDocuments = mutableListOf<NovelBookDocument>()
        val openedLocations = mutableListOf<NovelBookLocation>()
        val appendedDocuments = mutableListOf<NovelBookDocument>()
        val prependedDocuments = mutableListOf<NovelBookDocument>()
        val removedSections = mutableListOf<Int>()
        var openedDocument: NovelBookDocument? = null
        var openedLocation: NovelBookLocation? = null
        var openedFlow: NovelBookEngineFlow? = null
        var openedRestoreFraction: Float? = null

        /** Mirrors the renderer contract: only a live scrolled document can be stitched. */
        var stitchingSupported = false
        var nextResult: NovelBookPageTurnResult = NovelBookPageTurnResult.Moved(charOffset = 0)
        var previousResult: NovelBookPageTurnResult = NovelBookPageTurnResult.Moved(charOffset = 0)
        var relocateResult: NovelBookPageTurnResult = NovelBookPageTurnResult.Moved(charOffset = 0)

        override suspend fun open(
            document: NovelBookDocument,
            location: NovelBookLocation,
            flow: NovelBookEngineFlow,
            restoreFraction: Float?,
        ) {
            openedDocuments += document
            openedLocations += location
            openedDocument = document
            openedLocation = location
            openedFlow = flow
            openedRestoreFraction = restoreFraction
        }

        override suspend fun next(transitionStyleName: String): NovelBookPageTurnResult = nextResult

        override suspend fun previous(transitionStyleName: String): NovelBookPageTurnResult = previousResult

        override suspend fun relocate(): NovelBookPageTurnResult = relocateResult

        override suspend fun appendSection(document: NovelBookDocument): Boolean {
            if (!stitchingSupported) return false
            appendedDocuments += document
            return true
        }

        override suspend fun prependSection(document: NovelBookDocument): Boolean {
            if (!stitchingSupported) return false
            prependedDocuments += document
            return true
        }

        override suspend fun removeSection(sectionIndex: Int): Boolean {
            if (!stitchingSupported) return false
            removedSections += sectionIndex
            return true
        }
    }
}
