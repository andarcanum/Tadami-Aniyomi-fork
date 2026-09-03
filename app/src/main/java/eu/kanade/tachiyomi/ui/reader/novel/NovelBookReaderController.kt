package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.replace.applyReplaceRulesToHtml
import eu.kanade.tachiyomi.ui.reader.novel.replace.applyReplaceRulesToNativeBlocks
import eu.kanade.tachiyomi.ui.reader.novel.replace.replaceRulesFingerprint
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.book.novel.interactor.SetNovelBookProgress
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Sections around the reading position rebuilt when the visible translation changes.
 *
 * Wide enough to cover the resident window on both sides, so the reader never scrolls from a
 * translated section straight into an untranslated one, and small enough that toggling the button
 * does not re-run the pipeline over a whole book.
 */
private const val BOOK_TRANSLATION_REFRESH_RADIUS = 2

/**
 * Host the book controller uses to reach the shared reader state owned by
 * [NovelReaderScreenModel].
 *
 * The screen model keeps the per-chapter state and the shared progress/history pipeline; the book
 * controller only talks to it through this narrow surface so the two stay decoupled.
 */
internal interface NovelBookReaderHost {
    val bookScope: CoroutineScope

    fun bookCurrentNovel(): tachiyomi.domain.entries.novel.model.Novel?
    fun bookCurrentChapter(): NovelChapter?
    fun bookChapterOrderList(): List<NovelChapter>
    fun bookFullChapterOrderList(): List<NovelChapter>
    fun bookMarkChapterReadInMemory(chapterId: Long)
    fun bookTtsChapterRepository(): NovelTtsChapterRepository

    fun bookUpdateSuccessState(
        transform: (NovelReaderScreenModel.State.Success) -> NovelReaderScreenModel.State.Success,
    )
    fun bookAdoptBookModeChapter(chapterId: Long)
    fun bookEnqueueProgressPersistence(update: PendingProgressPersistence)
    fun onNovelCompletedWitnessed(chapter: NovelChapter)
    fun bookApplyBookSectionTranslation(chapterId: Long, bodyHtml: String): String
    fun bookGeminiTranslationVisible(): Boolean
    fun bookGoogleTranslationVisible(): Boolean
}

/**
 * Book-mode subsystem of the novel reader.
 *
 * Owns the compiled-book artifact source, the book runtime, the section command queue and the
 * book-specific progress/read-marking state. [NovelReaderScreenModel] delegates every
 * `onBookMode*` / `bookEngine*` call here, which keeps the screen model focused on the
 * chapter-by-chapter reader.
 */
internal class NovelBookReaderController(
    private val host: NovelBookReaderHost,
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val getNovelBookState: tachiyomi.domain.book.novel.interactor.GetNovelBookState = Injekt.get(),
    private val setNovelBookProgress: SetNovelBookProgress = Injekt.get(),
    internal val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Single content pipeline of book mode.
     *
     * Both the chapter-fed sections and the compiled artifact go through this one chain, so a
     * section can no longer be built with a different set of transformations depending on which
     * path produced it.
     */
    private val sectionRepository: BookSectionRepository = DefaultBookSectionRepository(
        loadRawSection = { sectionChapterId ->
            val snapshot = host.bookTtsChapterRepository().loadChapterSnapshot(sectionChapterId)
            NovelBookRawSection(
                chapterId = sectionChapterId,
                chapterName = snapshot.chapter.name,
                rawHtml = snapshot.rawHtml,
                chapterWebUrl = snapshot.chapterWebUrl,
            )
        },
        translateChapterHtml = { sectionChapterId, bodyHtml ->
            withContext(Dispatchers.Default) {
                host.bookApplyBookSectionTranslation(chapterId = sectionChapterId, bodyHtml = bodyHtml)
            }
        },
        showChapterHeadings = { novelReaderPreferences.bookModeShowChapterHeadings().get() },
        translationVariant = { bookTranslationVariant() },
        replaceTextHtml = { html ->
            applyReplaceRulesToHtml(html, novelReaderPreferences.enabledReplaceRules())
        },
        replaceRulesFingerprint = {
            replaceRulesFingerprint(novelReaderPreferences.enabledReplaceRules())
        },
    )

    private val bookModeRuntime = NovelBookModeRuntime(
        sectionRepository = sectionRepository,
        showChapterHeadings = { novelReaderPreferences.bookModeShowChapterHeadings().get() },
        prepareAhead = { novelReaderPreferences.bookModePrepareAhead().get() },
        sectionCacheScope = { bookSectionCacheScope() },
        readCachedSection = { key -> NovelBookSectionDiskCacheStore.read(key) },
        writeCachedSection = { key, section -> NovelBookSectionDiskCacheStore.write(key, section) },
        deleteCachedSection = { key -> NovelBookSectionDiskCacheStore.remove(key) },
        pruneCachedSections = { scope, keepPrefix ->
            NovelBookSectionDiskCacheStore.removeScopeExcept(scopePrefix = scope, keepPrefix = keepPrefix)
        },
    )

    /** Translation variant the section markup is built with, part of the cache scope and signature. */
    private fun bookTranslationVariant(): String = when {
        host.bookGeminiTranslationVisible() -> "gemini"
        host.bookGoogleTranslationVisible() -> "google"
        else -> "raw"
    }

    /**
     * Scope prepared book sections are cached under.
     *
     * Section HTML is normalized, heading-wrapped and possibly translated, so it may only be reused
     * for the same novel and the same visible translation. Mixing those would show a translated
     * section after the translation was hidden (or the other way round), which is why the translation
     * state is part of the scope instead of being invalidated on every toggle. Null before a novel is
     * known, which keeps sections in memory only.
     */
    private fun bookSectionCacheScope(): String? {
        val novelId = host.bookCurrentNovel()?.id ?: return null
        val bookVersion = bookStateVersion?.takeIf { it > 0 }?.let { "v$it-" } ?: ""
        return "novel$novelId-$bookVersion"
    }

    /**
     * Version of the compiled book whose sections are being cached.
     *
     * A rebuilt artifact can hold different text for the same chapter ids, so the prepared-section
     * cache has to be scoped to the book version; without it a rebuild would keep serving sections
     * compiled from the old chapters.
     */
    private var bookStateVersion: Long? = null

    /** Opened compiled-book artifact, non-null while this novel is read as one continuous book. */
    var artifactSource: NovelBookArtifactSource? = null
        private set

    /** Pre-compiled native blocks of a book section, or null when this book has none. */
    fun nativeBookBlocksForSection(sectionIndex: Int): List<NovelRichContentBlock>? =
        artifactSource
            ?.anchoredNativeBlocksFor(sectionIndex)
            ?.let { blocks ->
                applyReplaceRulesToNativeBlocks(blocks, novelReaderPreferences.enabledReplaceRules())
            }
            ?.toAnchoredRichContentBlocks()
            ?.takeIf { it.isNotEmpty() }

    /** Content revision per section, bumped when a mounted section's markup changed. */
    private val bookSectionRevisions = mutableMapOf<Int, Long>()

    private val bookWindowState = MutableStateFlow(NovelBookWindowState.EMPTY)

    /**
     * The window a mounted renderer holds, and the only channel from the core to a renderer.
     *
     * The core no longer pushes append/prune/scroll work into a queue that only one renderer read:
     * it publishes where the reader is, and each renderer pulls the sections it is missing through
     * [bookSectionHtml]. A renderer that was rebuilt underneath the core just reads this state
     * again, which is what the document-ready hook used to be needed for.
     */
    internal val bookWindow: StateFlow<NovelBookWindowState> = bookWindowState.asStateFlow()

    /** Publishes the window for the current reading position. */
    private fun refreshBookWindow() {
        val next = bookModeRuntime.windowState(bookSectionRevisions.toMap())
        // A scroll report re-creates the window instance even when nothing about the resident
        // window changed; assigning it anyway restarted the renderer's window effect on every
        // scroll. Equal windows are dropped here so the effect only restarts on real changes.
        if (bookWindowState.value == next) return
        bookWindowState.value = next
    }

    /**
     * Marks one section's markup as changed, so every mounted renderer re-pulls exactly it.
     *
     * Replaces the old "replace section" command: the section is dropped from the prepared cache and
     * its revision moves on, which both renderers notice on their own.
     */
    private fun bumpBookSectionRevision(sectionIndex: Int) {
        bookModeRuntime.invalidatePreparedSection(sectionIndex)
        bookSectionRevisions[sectionIndex] = (bookSectionRevisions[sectionIndex] ?: 0L) + 1L
        refreshBookWindow()
        logBookModeTrace { "section=$sectionIndex revision=${bookSectionRevisions[sectionIndex]}" }
    }

    /**
     * Markup of one section, prepared on demand for the renderer that is missing it.
     *
     * Compiled-book sections come straight out of the artifact body, so the translation overlay is
     * the only pipeline step left to run here.
     */
    internal suspend fun bookSectionHtml(sectionIndex: Int): String? {
        if (!bookModeRuntime.isActive) return null
        val html = runCatching { bookModeRuntime.sectionHtml(sectionIndex) }
            .onFailure { error -> logBookModeFailure("load section $sectionIndex", error) }
            .getOrNull()
            ?: return null
        val artifact = artifactSource ?: return html
        return runCatching {
            sectionRepository.applyArtifactTranslations(
                html = html,
                chapterIds = artifact.chaptersOfSection(sectionIndex),
            )
        }.getOrDefault(html)
    }

    internal val bookEngineSpine: NovelBookSpine
        get() = bookModeRuntime.engineSpine

    internal val bookEngineLocation: NovelBookLocation
        get() = bookModeRuntime.location

    /**
     * Explicit renderer moves, the only way the core changes the reading position.
     *
     * The current position is never pushed back into the renderer: it flows upwards only. Resume,
     * the seek bar, the chapter picker, TTS and search publish a request here, the renderer applies
     * it once and acknowledges it through [onBookSeekApplied].
     */
    private val bookSeekRequestState = MutableStateFlow<BookSeekRequest?>(null)

    internal val bookSeekRequests: StateFlow<BookSeekRequest?> = bookSeekRequestState.asStateFlow()

    private var lastBookSeekRequestId = 0L

    /** Publishes a move for the renderer and keeps the runtime position in step with it. */
    private fun requestBookSeek(location: NovelBookLocation, reason: BookSeekReason) {
        val artifact = artifactSource
        val locator = artifact?.locatorOf(artifact.charOffsetOf(location)) ?: BookLocator.UNKNOWN
        // An explicit move (resume, seekbar, chapter picker, TTS, search) never marks the chapters
        // it jumps over as read: read marking restarts from the chapter the seek lands in, and is
        // paused until the renderer applies the move.
        readMarkAnchorCharOffset = artifact?.let { it.chapterStartAt(it.charOffsetOf(location)) }
        bookSeekInFlight = true
        bookSeekRequestedAtMs = clock()
        lastBookSeekRequestId += 1
        bookSeekRequestState.value = BookSeekRequest(
            id = lastBookSeekRequestId,
            locator = locator,
            location = location,
            reason = reason,
        )
        logBookModeTrace {
            "seek request id=$lastBookSeekRequestId reason=$reason " +
                "section=${location.sectionIndex} offset=${location.charOffset}"
        }
    }

    /**
     * Acknowledgement from the renderer that a seek landed.
     *
     * The "restoring position" cover is dropped here, on the fact of the resume being applied,
     * instead of by a timer that could uncover the reader too early or too late.
     */
    fun onBookSeekApplied(seekRequestId: Long) {
        val request = bookSeekRequestState.value ?: return
        if (request.id != seekRequestId) return
        bookSeekInFlight = false
        if (request.reason == BookSeekReason.Resume && bookModeRestoringPosition) {
            bookModeRestoringPosition = false
            refreshBookModeState()
        }
    }

    /** Snapshot of the book-mode UI state, merged into the reader state by the screen model. */
    fun bookModeUiState(): NovelReaderScreenModel.State.ReaderBookModeState =
        bookModeRuntime.uiState().copy(isRestoringPosition = bookModeRestoringPosition)

    internal suspend fun loadBookEngineDocument(section: NovelBookSection): NovelBookDocument {
        val document = bookModeRuntime.loadEngineDocument(section)
        scheduleBookEnginePrefetch(section.index)
        // Compiled-book sections come straight out of the artifact body, so the translation overlay
        // of the shared pipeline is the only step that still has to run here. A section straddling a
        // chapter boundary is split per chapter first, which is what makes translations continue
        // across the boundary instead of stopping at it.
        val translated = artifactSource?.let { artifact ->
            sectionRepository.applyArtifactTranslations(
                html = document.html,
                chapterIds = artifact.chaptersOfSection(section.index),
            )
        } ?: document.html
        return if (translated == document.html) {
            document
        } else {
            document.copy(html = translated)
        }
    }

    private var bookEnginePrefetchJob: kotlinx.coroutines.Job? = null

    private var lastBookEnginePrefetchSectionIndex = -1

    private var bookModeProgressJob: kotlinx.coroutines.Job? = null

    private var lastPersistedBookModeSectionIndex = -1

    /** Whole-book offset this session started from; earlier chapters are not this session's doing. */
    private var bookModeSessionStartCharOffset = 0

    /** Chapter under the reading caret, tracked to notice chapter changes inside one block. */
    private var lastBookModeChapterId: Long? = null

    /** Mirrors [NovelReaderScreenModel.State.ReaderBookModeState.isRestoringPosition] for the UI snapshot. */
    private var bookModeRestoringPosition = false

    /** Chapters this book-mode session already reported as read, to avoid duplicate write-through. */
    private val bookModeMarkedReadChapterIds = mutableSetOf<Long>()

    /**
     * Chapter-boundary anchor for [markBookModeCrossedChaptersRead]: the furthest chapter start
     * organic reading has reached. Explicit seeks reset it to their target, so chapters a jump
     * skipped over are never marked read just because the position passed them.
     */
    private var readMarkAnchorCharOffset: Int? = null

    /** True while a seek is requested but not yet applied by the renderer; read marking pauses. */
    private var bookSeekInFlight = false

    /** When the in-flight seek was requested; a never-acked seek goes stale and stops blocking. */
    private var bookSeekRequestedAtMs = 0L

    private var lastBookModeFailureLogAtMs = 0L

    /** Idle delay before a book-mode position is written through the progress pipeline. */
    private val bookModeProgressDebounceMs = 1_500L

    /** Minimum gap between book-mode failure logs, so a failing source cannot flood logcat. */
    private val bookModeFailureLogThrottleMs = 5_000L

    /**
     * Gate for book-mode trace logging.
     *
     * The trace used to be pinned on, so every append/prune round was written to logcat even in
     * release builds. It is now driven by a hidden debug preference and read on each call, which
     * makes it possible to turn tracing on for one reproduction without rebuilding the app.
     */
    private val bookModeTraceLoggingEnabled: Boolean
        get() = novelReaderPreferences.bookModeTraceLogging().get()

    /** True when the reader should present the whole novel as one continuous document. */
    fun isBookModeEnabled(): Boolean = artifactSource != null

    /** True while the book runtime is active (a spine is loaded and being read). */
    fun isBookRuntimeActive(): Boolean = bookModeRuntime.isActive

    /**
     * Starts book mode for the currently loaded chapter. Suspending: called from the chapter load
     * path, which already runs on the screen model scope.
     */
    suspend fun startForChapter(chapter: NovelChapter) {
        maybeStartBookMode(chapter)
    }

    /**
     * Re-renders a book section whose translation changed.
     *
     * Sections are cached once they are rendered, so a translation that finishes after the section
     * was mounted would otherwise only appear after leaving and re-entering the book.
     */
    fun refreshSectionAfterTranslation(chapterId: Long) {
        if (!bookModeRuntime.isActive) return
        // A chapter can cover several sections of a compiled book, so every section holding this
        // chapter's text has to be rebuilt, not just the first one.
        val sections = bookModeRuntime.engineSpine.sections.filter { it.covers(chapterId) }
        if (sections.isEmpty()) return
        // Only the affected sections change revision and are pulled again in place. Rebuilding the
        // whole document (the old behaviour) reset the renderer, which is what made a finished
        // translation flash the book and throw the reader back to the section start.
        sections.forEach { section -> bumpBookSectionRevision(section.index) }
    }

    /**
     * Rebuilds the resident sections after the visible translation was switched on or off.
     *
     * The toggle only re-rendered the chapter reader, which book mode does not show: the book kept
     * the document it was already displaying, so "show original" flipped the button and changed
     * nothing on screen. Every section around the reading position is rebuilt with the variant that
     * is visible now and swapped in place, and the prepared-section cache is dropped so sections
     * mounted later do not serve the previous variant from memory.
     */
    fun refreshSectionsAfterTranslationVisibilityChange() {
        if (!bookModeRuntime.isActive) return
        bookModeRuntime.invalidatePreparedSections()
        val spine = bookModeRuntime.engineSpine
        val indices = spine.windowAround(
            sectionIndex = bookModeRuntime.location.sectionIndex,
            radius = BOOK_TRANSLATION_REFRESH_RADIUS,
        )
        if (indices.isEmpty()) return
        indices.forEach { index ->
            val section = spine.sectionAt(index) ?: return@forEach
            bumpBookSectionRevision(section.index)
        }
    }

    /** Watches the reading-mode preference so book mode can be switched on without a reload. */
    private var readingModeJob: kotlinx.coroutines.Job? = null

    fun observeReadingModeChanges(loadedChapter: NovelChapter) {
        readingModeJob?.cancel()
        readingModeJob = host.bookScope.launch {
            novelReaderPreferences.readingMode().changes()
                .distinctUntilChanged()
                .collect {
                    // The global book reading mode is gone: only the per-title artifact decides.
                    if (isBookModeEnabled() == bookModeRuntime.isActive) return@collect
                    if (isBookModeEnabled()) {
                        maybeStartBookMode(host.bookCurrentChapter() ?: loadedChapter)
                    } else {
                        bookEnginePrefetchJob?.cancel()
                        lastBookEnginePrefetchSectionIndex = -1
                        bookModeRuntime.stop()
                        bookSectionRevisions.clear()
                        refreshBookWindow()
                    }
                    refreshBookModeState()
                }
        }
    }

    /**
     * Rebuilds the book spine when the full chapter list arrives after the reader was already
     * opened with a narrower window. The spine is rebuilt only when its section count no longer
     * matches the full list.
     */
    fun rebuildSpineIfNeeded(chapter: NovelChapter, fullList: List<NovelChapter>) {
        if (!bookModeRuntime.isActive || fullList.isEmpty()) return
        if (bookModeRuntime.uiState().sectionCount == fullList.size) return
        host.bookScope.launch {
            maybeStartBookMode(chapter)
            refreshBookModeState()
        }
    }

    /**
     * Builds the native block stream of an older book in the background.
     *
     * The book stays fully readable while this runs: sections keep being parsed on the fly until the
     * stream is ready. Only then is the artifact re-opened, so the sections rendered after that point
     * come from pre-compiled blocks. Nothing is re-downloaded and no offset changes, which is why
     * swapping the source mid-read is safe.
     */
    private fun scheduleNativeBookStreamUpgrade() {
        val novel = host.bookCurrentNovel() ?: return
        val source = artifactSource ?: return
        if (source.hasNativeBlocks) return
        host.bookScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val migrated = runCatching {
                eu.kanade.tachiyomi.data.book.novel.NovelBookBuilder().ensureNativeStream(novel)
            }.getOrDefault(false)
            if (!migrated || host.bookCurrentNovel()?.id != novel.id) return@launch
            val upgraded = runCatching {
                NovelBookArtifactSource.open(
                    directory = eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact.directoryFor(
                        root = eu.kanade.tachiyomi.data.book.novel.NovelBookBuilder
                            .defaultRootDirectory(),
                        sourceId = novel.source,
                        novelId = novel.id,
                    ),
                    replaceTextHtml = { html ->
                        applyReplaceRulesToHtml(html, novelReaderPreferences.enabledReplaceRules())
                    },
                )
            }.getOrNull() ?: return@launch
            if (host.bookCurrentNovel()?.id == novel.id) {
                artifactSource = upgraded
                logcat(LogPriority.INFO) { "Native book stream ready: novel=${novel.id}" }
            }
        }
    }

    /** Starts book mode for the currently loaded chapter, or keeps the runtime inert otherwise. */
    private suspend fun maybeStartBookMode(chapter: NovelChapter) {
        // Phase-0 baseline metric: wall-clock cost of opening a book, from the artifact lookup to
        // the queued resume scroll. Compared against the recorded baseline after each phase.
        val startedAtMs = System.currentTimeMillis()
        val novel = host.bookCurrentNovel()
        val bookState = novel?.let { getNovelBookState.await(it.id) }
        bookStateVersion = bookState?.bookVersion
        artifactSource = if (bookState?.enabled == true) {
            novel.let {
                NovelBookArtifactSource.open(
                    directory = eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact.directoryFor(
                        root = eu.kanade.tachiyomi.data.book.novel.NovelBookBuilder
                            .defaultRootDirectory(),
                        sourceId = novel.source,
                        novelId = novel.id,
                    ),
                    replaceTextHtml = { html ->
                        applyReplaceRulesToHtml(html, novelReaderPreferences.enabledReplaceRules())
                    },
                )
            }
        } else {
            null
        }
        scheduleNativeBookStreamUpgrade()
        if (!isBookModeEnabled()) {
            if (bookModeRuntime.isActive) {
                bookEnginePrefetchJob?.cancel()
                lastBookEnginePrefetchSectionIndex = -1
                bookModeRuntime.stop()
            }
            return
        }
        val chapters = host.bookFullChapterOrderList().ifEmpty { host.bookChapterOrderList() }
        if (chapters.isEmpty()) return
        // Book mode only ever runs over a compiled artifact: the entry point is hidden until the
        // book is built, and the early return above already left book mode when there is none.
        val artifact = artifactSource ?: return
        // Opening the chapter the stored position belongs to means "continue reading", so the
        // exact offset is restored. Any other chapter comes from a table-of-contents tap and
        // must land on that chapter's first character instead.
        // Stored position as a locator: the chapter plus the offset inside it survives a rebuild of
        // the artifact, while the whole-book offset alone would silently shift by every character
        // added or removed before it.
        // Rows written before the locator existed are converted here, once, before anything reads
        // them: their whole-book offset (or the position the classic reader packed into the chapter
        // row) is turned into a locator and written back, so the upgrade does not lose the position.
        val migratedLocator = bookState
            ?.let { state -> migrateBookProgressIfNeeded(state, chapter, chapters, artifact) }
        val storedLocator = migratedLocator ?: bookState?.let { state ->
            state.lastChapterId?.let { lastChapterId ->
                BookLocator(
                    chapterId = lastChapterId,
                    blockIndex = state.blockIndex,
                    charOffset = state.chapterCharOffset,
                )
            }
        }
        val storedLocation = storedLocator
            ?.let { artifact.locationOf(it) }
            ?: artifact.locationOf((bookState?.charOffset ?: 0L).toInt())
        val resumeChapterId = storedLocator?.chapterId ?: bookState?.lastChapterId
        val resumeLocation = if (resumeChapterId == chapter.id) {
            storedLocation
        } else {
            artifact.locationOfChapter(chapter.id) ?: storedLocation
        }
        bookModeRuntime.startWithSpine(
            spine = artifact.spine,
            resumeLocation = resumeLocation,
            windowPolicy = BookWindowPolicy.forBlockChars(
                eu.kanade.tachiyomi.data.book.novel.NovelBookBlockPlanner.DEFAULT_TARGET_CHARS,
            ),
            fetchSectionHtml = { sectionKey ->
                artifact.documentFor(sectionKey.toInt())?.html.orEmpty()
            },
        )
        bookEnginePrefetchJob?.cancel()
        lastBookEnginePrefetchSectionIndex = -1
        bookSectionRevisions.clear()
        val resumeState = bookModeRuntime.uiState()
        val resumedLocation = bookModeRuntime.location
        bookModeRestoringPosition = resumedLocation.sectionIndex > 0 || resumedLocation.charOffset > 0
        // Emitted before the resume seek so the UI can cover the book until the position lands;
        // onBookSeekApplied clears the flag (and re-emits this state) the moment the renderer
        // acknowledges the move. Without this emission the cover was dead code: the UI never saw
        // isRestoringPosition=true and the reader flashed the document start before the jump.
        refreshBookModeState()
        // The renderer is told to move exactly once, and the cover above it stays until that move
        // is acknowledged. No timer decides when the resume is "probably" done anymore.
        requestBookSeek(resumedLocation, BookSeekReason.Resume)
        bookModeSessionStartCharOffset = artifactSource
            ?.let { it.chapterStartAt(it.charOffsetOf(resumedLocation)) }
            ?: 0
        lastPersistedBookModeSectionIndex = resumedLocation.sectionIndex
        lastBookModeChapterId = bookModeChapterAtReadingPosition()?.id
        bookModeMarkedReadChapterIds.clear()
        // Both renderers take their moves from the seek request published above and pull the window
        // themselves, so the book opens where the reader left off on either renderer without a
        // renderer-specific scroll command.
        refreshBookWindow()
        scheduleBookEnginePrefetch(resumedLocation.sectionIndex)
        logcat(LogPriority.INFO) {
            "Book mode started: sections=${resumeState.sectionCount}, " +
                "resumeSection=${resumeState.currentSectionIndex}, " +
                "resumeFraction=${resumeState.currentSectionFraction}, chapterId=${chapter.id}"
        }
        logBookModeMetric(
            "open novelId=${novel?.id} sections=${resumeState.sectionCount} " +
                "source=artifact " +
                "v2=${novelReaderPreferences.novelBookModeV2().get()} " +
                "durationMs=${System.currentTimeMillis() - startedAtMs}",
        )
    }

    /** Throttled book-mode logging: repeated failures collapse into one entry per few seconds. */
    private fun logBookModeFailure(what: String, error: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastBookModeFailureLogAtMs < bookModeFailureLogThrottleMs) return
        lastBookModeFailureLogAtMs = now
        logcat(LogPriority.WARN, error) { "Book mode failed to $what" }
    }

    /**
     * Debug-only book-mode trace, emitted under the structured [BOOK_MODE_LOG_TAG] tag so a whole
     * reading session can be filtered with `adb logcat -s NovelBook`.
     */
    private inline fun logBookModeTrace(message: () -> String) {
        if (!bookModeTraceLoggingEnabled) return
        logcat.logcat(priority = LogPriority.DEBUG, tag = BOOK_MODE_LOG_TAG) { message() }
    }

    /**
     * Book-mode metric line: always emitted (they are rare and one line each) under the same tag,
     * so the phase-0 baseline numbers can be collected from a release build.
     */
    private fun logBookModeMetric(message: String) {
        logcat.logcat(priority = LogPriority.INFO, tag = BOOK_MODE_LOG_TAG) { "metric $message" }
    }

    private fun refreshBookModeState() {
        host.bookUpdateSuccessState { successState ->
            // The prepare-book action is owned by the controller, so its progress is merged into the
            // runtime snapshot instead of adding a second state holder for the UI to read.
            val chapterCount = bookPrepareTotalCount.takeIf { it > 0 }
                ?: host.bookFullChapterOrderList().ifEmpty { host.bookChapterOrderList() }.size
            successState.copy(
                bookMode = bookModeRuntime.uiState().copy(
                    isPreparingWholeBook = bookPrepareRunning,
                    preparedChapterCount = bookPreparedChapterCount,
                    totalChapterCount = chapterCount,
                    isRestoringPosition = bookModeRestoringPosition,
                ),
            )
        }
    }

    /** Reports the reader position inside the book after a scroll update from the WebView. */
    fun onBookModeScroll(sectionIndex: Int, sectionFraction: Float) {
        if (!bookModeRuntime.isActive) return
        bookModeRuntime.moveTo(sectionIndex = sectionIndex, sectionFraction = sectionFraction)
        refreshBookWindow()
        scheduleBookEnginePrefetch(bookModeRuntime.location.sectionIndex)
        refreshBookModeState()
        onBookModePositionChanged(sectionFraction)
    }

    /** Receives the dedicated renderer's exact section/text location. */
    fun onBookEngineLocationChanged(location: NovelBookLocation) {
        if (!bookModeRuntime.isActive) return
        bookModeRuntime.moveTo(location)
        refreshBookWindow()
        refreshBookModeState()
        val currentLocation = bookModeRuntime.location
        scheduleBookEnginePrefetch(currentLocation.sectionIndex)
        onBookModePositionChanged(bookModeRuntime.uiState().currentSectionFraction)
    }

    /**
     * Turns a new reading position into read marking and persistence.
     *
     * Both renderers funnel through here so the rules cannot drift apart.
     */
    private fun onBookModePositionChanged(sectionFraction: Float) {
        val location = bookModeRuntime.location
        val chapterId = bookModeChapterAtReadingPosition()?.id
        val chapterChanged = chapterId != null && chapterId != lastBookModeChapterId
        if (chapterChanged) {
            lastBookModeChapterId = chapterId
            host.bookAdoptBookModeChapter(chapterId)
            markBookModeCrossedChaptersRead()
        } else if (location.sectionIndex != lastPersistedBookModeSectionIndex) {
            markBookModeCrossedChaptersRead()
        }
        if (chapterChanged) {
            // Crossing into another chapter is a checkpoint worth surviving a kill, so the position
            // is written immediately instead of waiting out the debounce window.
            flushBookModeProgress()
            lastPersistedBookModeSectionIndex = location.sectionIndex
            return
        }
        scheduleBookModeProgressPersistence(
            sectionIndex = location.sectionIndex,
            sectionFraction = sectionFraction,
        )
    }

    /**
     * Writes the current position through immediately, cancelling the pending debounce.
     *
     * Called from the lifecycle's ON_STOP, when TTS takes over, when the reader is left and when the
     * reader crosses into another chapter. The 1.5s debounce is fine while reading, but any of these
     * moments can be the last one before the process goes away.
     */
    fun flushBookModeProgress() {
        if (!bookModeRuntime.isActive) return
        bookModeProgressJob?.cancel()
        persistBookModeProgress(bookModeRuntime.uiState().currentSectionFraction)
    }

    fun seekBookModeToProgress(fraction: Float) {
        if (!bookModeRuntime.isActive) return
        val artifact = artifactSource
        val location = if (artifact != null) {
            val charOffset = (fraction.coerceIn(0f, 1f) * artifact.totalChars).toInt()
            artifact.locationOf(charOffset)
        } else {
            val totalSections = bookModeRuntime.uiState().sectionCount
            val sectionIndex = (fraction.coerceIn(0f, 1f) * (totalSections - 1).coerceAtLeast(0)).toInt()
            NovelBookLocation(sectionIndex = sectionIndex, charOffset = 0)
        }
        // A seek is an explicit move: the runtime follows the request, and the renderer is asked to
        // go there once. It is not a reported position, so it never loops back.
        bookModeRuntime.moveTo(location)
        requestBookSeek(location, BookSeekReason.Seekbar)
        refreshBookWindow()
        refreshBookModeState()
        scheduleBookEnginePrefetch(location.sectionIndex)
        onBookModePositionChanged(bookModeRuntime.uiState().currentSectionFraction)
    }

    private fun scheduleBookEnginePrefetch(sectionIndex: Int) {
        if (!bookModeRuntime.isActive || sectionIndex == lastBookEnginePrefetchSectionIndex) return
        lastBookEnginePrefetchSectionIndex = sectionIndex
        bookEnginePrefetchJob?.cancel()
        bookEnginePrefetchJob = host.bookScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { bookModeRuntime.prefetchAround(sectionIndex) }
            }
            result.onFailure { error ->
                logBookModeFailure("prefetch around section $sectionIndex", error)
            }
            refreshBookModeState()
        }
    }

    /** Chapter that currently sits under the reading position over a book artifact. */
    fun bookModeChapterAtReadingPosition(): NovelChapter? = artifactSource
        ?.takeIf { bookModeRuntime.isActive }
        ?.let { artifact ->
            val chapterId = artifact.chapterAt(artifact.charOffsetOf(bookModeRuntime.location))?.chapterId
            chapterId?.let { id ->
                host.bookChapterOrderList().firstOrNull { it.id == id }
                    ?: host.bookFullChapterOrderList().firstOrNull { it.id == id }
            }
        }

    private fun markBookModeCrossedChaptersRead() {
        if (!bookModeRuntime.isActive) return
        // While an explicit seek is in flight the renderer may still report pre-seek positions;
        // marking chapters from those would count chapters the reader jumped over (or back) as read.
        if (bookSeekInFlight) {
            // A renderer that never acks (crashed section, WebView reload that dropped the request)
            // used to pause read marking for the rest of the session; treat an unacked seek older
            // than the ack timeout as stale, recover the flag and let marking resume.
            if (clock() - bookSeekRequestedAtMs < BOOK_SEEK_ACK_TIMEOUT_MS) return
            logBookModeTrace {
                "seek in flight for over ${BOOK_SEEK_ACK_TIMEOUT_MS}ms without ack; clearing stale flag"
            }
            bookSeekInFlight = false
        }
        val alreadyRead = buildSet {
            addAll(bookModeMarkedReadChapterIds)
            host.bookChapterOrderList().forEach { if (it.read) add(it.id) }
            host.bookFullChapterOrderList().forEach { if (it.read) add(it.id) }
        }
        // A section is a fixed-size block, not a chapter, so "the section was read" cannot mark a
        // chapter read. Crossed chapters are derived from the whole-book character offset instead,
        // which is the exact same signal the position is persisted from.
        val artifact = artifactSource ?: return
        val charOffset = artifact.charOffsetOf(bookModeRuntime.location)
        // Read marking is anchored to the chapter start organic reading last reached: after a seek
        // the anchor is the landing chapter, so skipped chapters are not marked read.
        val baseline = readMarkAnchorCharOffset ?: bookModeSessionStartCharOffset
        if (charOffset < baseline) return
        val crossedChapterIds = artifact
            .chaptersFullyReadBetween(
                fromCharOffset = baseline,
                toCharOffset = charOffset,
            )
            .filterNot { it in alreadyRead }
            .toMutableList()
        // The chapter under the caret is still being read, but it counts as read once the reader is
        // past the same threshold the per-chapter reader uses (90%). Without this, a chapter the
        // reader stopped in the middle of stayed unread until 99% of the WHOLE book, and "novel
        // completed" / the series interstitial never fired.
        artifact.chapterAt(charOffset)?.let { current ->
            val progressInside = if (current.charLength > 0) {
                ((charOffset - current.charStart).toFloat() / current.charLength.toFloat())
            } else {
                0f
            }
            if (progressInside >= BOOK_MODE_READ_THRESHOLD && current.chapterId !in alreadyRead) {
                crossedChapterIds += current.chapterId
            }
        }
        // Move the anchor to the chapter the reader is in now, so the next crossing resumes from a
        // clean chapter boundary instead of a mid-chapter offset.
        readMarkAnchorCharOffset = artifact.chapterStartAt(charOffset)
        if (crossedChapterIds.isEmpty()) return
        crossedChapterIds.forEach { chapterId ->
            val chapter = host.bookChapterOrderList().firstOrNull { it.id == chapterId }
                ?: host.bookFullChapterOrderList().firstOrNull { it.id == chapterId }
                ?: return@forEach
            bookModeMarkedReadChapterIds += chapterId
            val becameRead = !chapter.read
            host.bookMarkChapterReadInMemory(chapterId)
            // "Novel completed" has to check the whole novel: the resident window is anchored
            // to the entry chapter and does not slide like the chapter reader's window, so a
            // window-only check would fire long before a long book is actually finished.
            val emitNovelCompleted = becameRead &&
                host.bookFullChapterOrderList()
                    .ifEmpty { host.bookChapterOrderList() }
                    .all { it.read }
            if (emitNovelCompleted) {
                host.onNovelCompletedWitnessed(chapter)
            }
            host.bookEnqueueProgressPersistence(
                PendingProgressPersistence(
                    chapterId = chapter.id,
                    novelId = chapter.novelId,
                    chapterNumber = chapter.chapterNumber.toInt(),
                    read = true,
                    // The book position never leaks into the chapter row: `lastPageRead` belongs to
                    // the chapter-by-chapter reader, and book mode resumes from its own locator.
                    lastPageRead = chapter.lastPageRead,
                    emitReadEvent = becameRead,
                    emitNovelCompleted = emitNovelCompleted,
                    sessionReadDurationMs = 0L,
                ),
            )
        }
    }

    /**
     * Writes the position through the progress pipeline at most once per idle window, and right away
     * when the reader crosses into another section.
     */
    private fun scheduleBookModeProgressPersistence(sectionIndex: Int, sectionFraction: Float) {
        bookModeProgressJob?.cancel()
        if (sectionIndex != lastPersistedBookModeSectionIndex) {
            lastPersistedBookModeSectionIndex = sectionIndex
            persistBookModeProgress(sectionFraction)
            return
        }
        bookModeProgressJob = host.bookScope.launch {
            kotlinx.coroutines.delay(bookModeProgressDebounceMs)
            persistBookModeProgress(sectionFraction)
        }
    }

    /**
     * Persists the reading position as a book locator so the reader can resume anywhere in the
     * novel.
     *
     * There is exactly one writer of the book position now: the artifact state row. The chapter row
     * is deliberately not touched here, because a percentage of a whole book cannot be expressed as
     * a page index of a single chapter, and that round trip is what made progress jump. The "read"
     * flag stays with [markBookModeCrossedChaptersRead] and its per-chapter threshold.
     */
    private fun persistBookModeProgress(@Suppress("UNUSED_PARAMETER") sectionFraction: Float) {
        persistBookArtifactProgress()
    }

    /**
     * Converts a position stored by an older version into a locator, once per book.
     *
     * Runs before the resume location is computed, so the very first open after the upgrade already
     * lands on the migrated position, and writes the result back immediately: the row is then in the
     * new format and the conversion never runs again, even if the reader is closed right away.
     * Returns the migrated locator, or null when the row is already migrated or nothing could be
     * recovered, in which case the stored values are left untouched.
     */
    private suspend fun migrateBookProgressIfNeeded(
        bookState: tachiyomi.domain.book.novel.model.NovelBookState,
        chapter: NovelChapter,
        chapters: List<NovelChapter>,
        artifact: NovelBookArtifactSource,
    ): BookLocator? {
        val plan = planBookProgressMigration(
            alreadyMigrated = bookState.progressMigrated,
            storedCharOffset = bookState.charOffset,
            storedChapterId = bookState.lastChapterId,
            // The classic reader packed a book position into the chapter row it was reading.
            legacyChapterProgress = chapter.lastPageRead,
            chapterIdsInReadingOrder = chapters.map { it.id },
            openedChapterId = chapter.id,
            locatorOfGlobalOffset = { offset -> artifact.locatorOf(offset) },
            locatorInChapter = { chapterId, fraction ->
                eu.kanade.tachiyomi.data.book.novel.NovelBookBlockPlanner
                    .chapterById(artifact.index, chapterId)
                    ?.let { entry ->
                        val inside = ((entry.charLength - 1).coerceAtLeast(0) * fraction).toInt()
                        artifact.locatorOf(entry.charStart + inside)
                    }
            },
        ) ?: return null

        val charOffset = artifact.globalCharOffsetOf(plan.locator)
        setNovelBookProgress.await(
            novelId = bookState.novelId,
            charOffset = charOffset.toLong(),
            lastChapterId = plan.locator.chapterId.takeIf { it != BookLocator.NO_CHAPTER_ID },
            blockIndex = plan.locator.blockIndex,
            chapterCharOffset = plan.locator.charOffset,
        )
        logBookModeMetric(
            "progress-migrated novel=${bookState.novelId} source=${plan.source} " +
                "chapter=${plan.locator.chapterId} offset=$charOffset",
        )
        return plan.locator
    }

    /**
     * Stores the whole-book position of a compiled book as a global character offset.
     *
     * Blocks are not chapters, so the per-chapter progress row cannot describe where the reader is
     * inside the book; the artifact state row keeps the exact offset used to resume.
     */
    private fun persistBookArtifactProgress() {
        val artifact = artifactSource ?: return
        val novelId = host.bookCurrentNovel()?.id ?: return
        val charOffset = artifact.charOffsetOf(bookModeRuntime.location)
        val locator = artifact.locatorOf(charOffset)
        // NonCancellable: the final flush runs from the screen model's disposal, and a write
        // launched on the about-to-be-cancelled scope died before reaching the database - losing
        // exactly the progress flushAndStop() exists to save. Detached writes are idempotent.
        host.bookScope.launch(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
            setNovelBookProgress.await(
                novelId = novelId,
                // Whole-book offset stays stored for the progress bar and the library; the locator
                // is what the reader actually resumes from.
                charOffset = charOffset.toLong(),
                lastChapterId = locator.chapterId.takeIf { it != BookLocator.NO_CHAPTER_ID },
                blockIndex = locator.blockIndex,
                chapterCharOffset = locator.charOffset,
            )
        }
    }

    /** Jumps to a chapter without leaving the continuous document. */
    fun onBookModeChapterSelected(chapterId: Long): Boolean {
        if (!bookModeRuntime.moveToChapter(chapterId)) return false
        requestBookSeek(bookModeRuntime.location, BookSeekReason.TableOfContents)
        // The native renderer only seeks into sections its resident window already holds, so the
        // window has to move first: a far chapter would otherwise never mount and the seek would
        // silently drop (resolveNovelBookNativeSeekTarget returns null for unmounted sections).
        refreshBookWindow()
        refreshBookModeState()
        return true
    }

    /**
     * Retries a section that failed to load, e.g. after a network drop.
     */
    fun onBookModeRetrySection(sectionIndex: Int) {
        if (!bookModeRuntime.isActive) return
        val section = bookModeRuntime.engineSpine.sectionAt(sectionIndex) ?: return
        host.bookScope.launch {
            val retried = withContext(Dispatchers.IO) {
                runCatching { bookModeRuntime.retrySection(section.loaderKey) }
                    .onFailure { error -> logBookModeFailure("retry section $sectionIndex", error) }
                    .isSuccess
            }
            if (retried) {
                bumpBookSectionRevision(sectionIndex)
            }
            refreshBookModeState()
        }
    }

    private var bookPrepareJob: Job? = null

    private var bookPrepareRunning: Boolean = false

    private var bookPreparedChapterCount: Int = 0

    private var bookPrepareTotalCount: Int = 0

    /**
     * Prepares every chapter of the book, so the whole novel can be read offline without waiting for
     * the sliding window. Sections are prepared one by one to stay friendly to source rate limits.
     */
    fun prepareWholeBook() {
        if (!bookModeRuntime.isActive || bookPrepareJob?.isActive == true) return
        // Sections, not chapters: the unit that gets prepared and cached is a spine section, and
        // walking chapters prepared the wrong things (and the wrong count) over a compiled book.
        val sections = bookModeRuntime.engineSpine.sections
        if (sections.isEmpty()) return
        bookPrepareTotalCount = sections.size
        bookPreparedChapterCount = 0
        bookPrepareRunning = true
        refreshBookModeState()
        bookPrepareJob = host.bookScope.launch(Dispatchers.IO) {
            try {
                sections.forEach { section ->
                    runCatching { bookModeRuntime.prepareSection(section.loaderKey) }
                        .onFailure { error ->
                            logBookModeFailure("prepare section ${section.index}", error)
                        }
                    bookPreparedChapterCount += 1
                    refreshBookModeState()
                }
            } finally {
                bookPrepareRunning = false
                refreshBookModeState()
            }
        }
    }

    /**
     * Flushes the debounced book-mode position and tears the runtime down. Called from the screen
     * model's disposal: leaving the reader mid-section would otherwise lose up to one debounce
     * window of reading progress, and chapters the reader scrolled past would never be marked read.
     */
    fun flushAndStop() {
        readingModeJob?.cancel()
        readingModeJob = null
        if (bookModeRuntime.isActive) {
            markBookModeCrossedChaptersRead()
            persistBookModeProgress(bookModeRuntime.uiState().currentSectionFraction)
        }
        bookModeProgressJob?.cancel()
        bookEnginePrefetchJob?.cancel()
        bookPrepareJob?.cancel()
        lastPersistedBookModeSectionIndex = -1
        bookSeekRequestState.value = null
        bookModeRestoringPosition = false
        bookModeSessionStartCharOffset = 0
        readMarkAnchorCharOffset = null
        bookSeekInFlight = false
        lastBookModeChapterId = null
        bookModeMarkedReadChapterIds.clear()
        bookModeRuntime.stop()
        bookSectionRevisions.clear()
        bookWindowState.value = NovelBookWindowState.EMPTY
    }

    companion object {
        /**
         * Structured logcat tag for every book-mode trace and metric line.
         *
         * One tag for the whole subsystem means a session can be captured with
         * `adb logcat -s NovelBook` instead of grepping the "Book mode: " prefix out of the
         * app-wide log.
         */
        const val BOOK_MODE_LOG_TAG = "NovelBook"

        /**
         * How long an unacked book seek may pause read marking before the flag is treated as
         * stale (renderer crashed/reloaded without applying the request).
         */
        private const val BOOK_SEEK_ACK_TIMEOUT_MS = 30_000L

        /**
         * Share of a chapter that has to be passed before book mode marks it read.
         *
         * It mirrors the per-chapter reader's threshold: without it a chapter the reader stopped in
         * the middle of stayed unread until almost the whole book was finished.
         */
        private const val BOOK_MODE_READ_THRESHOLD = 0.9f
    }
}
