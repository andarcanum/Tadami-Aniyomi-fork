package eu.kanade.tachiyomi.ui.reader.novel.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NovelTtsTextSource {
    ORIGINAL,
    TRANSLATED,
}

enum class NovelTtsPlaybackState {
    IDLE,
    PLAYING,
    PAUSED,
    COMPLETED,
}

data class NovelTtsResolvedChapter(
    val chapterId: Long,
    val nextChapterId: Long? = null,
    val originalModel: NovelTtsChapterModel,
    val translatedModel: NovelTtsChapterModel? = null,
)

data class NovelTtsSessionCheckpoint(
    val chapterId: Long,
    val utteranceId: String,
    val segmentId: String,
    val wordIndex: Int,
    val textSource: NovelTtsTextSource,
    val autoAdvanceChapter: Boolean,
)

data class NovelTtsSession(
    val chapterId: Long,
    val nextChapterId: Long?,
    val model: NovelTtsChapterModel,
    val textSource: NovelTtsTextSource,
    val utteranceIndex: Int,
    val wordIndex: Int,
    val autoAdvanceChapter: Boolean,
) {
    val utterance: NovelTtsUtterance
        get() = model.utterances[utteranceIndex]
}

data class NovelTtsSessionUiState(
    val playbackState: NovelTtsPlaybackState = NovelTtsPlaybackState.IDLE,
    val session: NovelTtsSession? = null,
    val pendingChapterHandoffId: Long? = null,
)

interface NovelTtsChapterSource {
    suspend fun loadChapter(chapterId: Long): NovelTtsResolvedChapter?
}

interface NovelTtsPlaybackSpeaker {
    suspend fun speak(
        utterance: NovelTtsUtterance,
        flushQueue: Boolean,
        startWordIndex: Int = 0,
    )

    fun stop()
}

interface NovelTtsPlaybackController {
    val state: StateFlow<NovelTtsSessionUiState>

    suspend fun pause()

    suspend fun resume()

    suspend fun stop()

    suspend fun skipNext()

    suspend fun skipPrevious()
}

class NovelTtsSessionController(
    private val chapterSource: NovelTtsChapterSource,
    private val speaker: NovelTtsPlaybackSpeaker,
    private val sessionStore: NovelTtsSessionStore,
) : NovelTtsPlaybackController {
    private val mutableState = MutableStateFlow(NovelTtsSessionUiState())
    override val state: StateFlow<NovelTtsSessionUiState> = mutableState.asStateFlow()
    private var preferredTranslatedText: Boolean = false

    /** In-memory cache of the last successfully loaded chapter. Avoids redundant IO when
     *  only the text source changes (e.g. original <-> translated switch during resume). */
    private var cachedResolvedChapter: NovelTtsResolvedChapter? = null

    suspend fun startFromCurrentPosition(
        chapterId: Long,
        utteranceId: String?,
        preferTranslatedText: Boolean,
        autoAdvanceChapter: Boolean,
    ) {
        preferredTranslatedText = preferTranslatedText
        val resolvedChapter = loadChapterCached(chapterId) ?: return
        val session = buildSession(
            resolvedChapter = resolvedChapter,
            utteranceId = utteranceId,
            preferTranslatedText = preferTranslatedText,
            autoAdvanceChapter = autoAdvanceChapter,
            restoredWordIndex = 0,
        ) ?: return
        clearPendingChapterHandoff()
        updateState(session, NovelTtsPlaybackState.PLAYING)
        persistCheckpoint(session)
        speaker.speak(session.utterance, flushQueue = true, startWordIndex = session.wordIndex)
    }

    override suspend fun pause() {
        val session = mutableState.value.session ?: return
        speaker.stop()
        updateState(session, NovelTtsPlaybackState.PAUSED)
        persistCheckpoint(session)
    }

    override suspend fun resume() {
        val session = mutableState.value.session ?: return
        val sessionPrefersTranslatedText = session.textSource == NovelTtsTextSource.TRANSLATED
        if (sessionPrefersTranslatedText != preferredTranslatedText) {
            restartFromCurrentPosition(preferredTranslatedText)
            return
        }
        updateState(session, NovelTtsPlaybackState.PLAYING)
        persistCheckpoint(session)
        speaker.speak(session.utterance, flushQueue = true, startWordIndex = session.wordIndex)
    }

    override suspend fun stop() {
        speaker.stop()
        sessionStore.clearCheckpoint()
        mutableState.value = NovelTtsSessionUiState()
    }

    override suspend fun skipNext() {
        val session = mutableState.value.session ?: return
        speaker.stop()
        onUtteranceCompleted(session.utterance.id)
    }

    override suspend fun skipPrevious() {
        val session = mutableState.value.session ?: return
        val previousUtteranceIndex = (session.utteranceIndex - 1).coerceAtLeast(0)
        val previousSession = session.copy(
            utteranceIndex = previousUtteranceIndex,
            wordIndex = 0,
        )
        updateState(previousSession, NovelTtsPlaybackState.PLAYING)
        persistCheckpoint(previousSession)
        speaker.speak(previousSession.utterance, flushQueue = true, startWordIndex = 0)
    }

    suspend fun onUtteranceCompleted(utteranceId: String) {
        val session = mutableState.value.session ?: return
        if (session.utterance.id != utteranceId) return

        val nextUtteranceIndex = session.utteranceIndex + 1
        if (nextUtteranceIndex < session.model.utterances.size) {
            val nextSession = session.copy(
                utteranceIndex = nextUtteranceIndex,
                wordIndex = 0,
            )
            updateState(nextSession, NovelTtsPlaybackState.PLAYING)
            persistCheckpoint(nextSession)
            speaker.speak(nextSession.utterance, flushQueue = true, startWordIndex = 0)
            return
        }

        if (session.autoAdvanceChapter && session.nextChapterId != null) {
            persistChapterHandoffCheckpoint(session)
            mutableState.value = mutableState.value.copy(
                playbackState = NovelTtsPlaybackState.PLAYING,
                pendingChapterHandoffId = session.nextChapterId,
            )
            val nextChapter = chapterSource.loadChapter(session.nextChapterId) ?: run {
                completeSession()
                return
            }
            val nextSession = buildSession(
                resolvedChapter = nextChapter,
                utteranceId = null,
                preferTranslatedText = preferredTranslatedText,
                autoAdvanceChapter = session.autoAdvanceChapter,
                restoredWordIndex = 0,
            ) ?: run {
                completeSession()
                return
            }
            updateState(nextSession, NovelTtsPlaybackState.PLAYING)
            persistCheckpoint(nextSession)
            speaker.speak(nextSession.utterance, flushQueue = true, startWordIndex = 0)
            return
        }

        completeSession()
    }

    suspend fun updateWordProgress(wordIndex: Int) {
        val session = mutableState.value.session ?: return
        val lastWordIndex = session.utterance.wordRanges.lastIndex
        val safeWordIndex = when {
            lastWordIndex < 0 -> 0
            else -> wordIndex.coerceIn(0, lastWordIndex)
        }
        val updatedSession = session.copy(wordIndex = safeWordIndex)
        updateState(updatedSession, mutableState.value.playbackState)
        persistCheckpoint(updatedSession)
    }

    suspend fun restoreFromCheckpoint() {
        val checkpoint = sessionStore.loadCheckpoint() ?: return
        preferredTranslatedText = checkpoint.textSource == NovelTtsTextSource.TRANSLATED
        val resolvedChapter = loadChapterCached(checkpoint.chapterId) ?: return
        val session = buildSession(
            resolvedChapter = resolvedChapter,
            utteranceId = checkpoint.utteranceId,
            preferTranslatedText = checkpoint.textSource == NovelTtsTextSource.TRANSLATED,
            autoAdvanceChapter = checkpoint.autoAdvanceChapter,
            restoredWordIndex = checkpoint.wordIndex,
        ) ?: return
        updateState(session, NovelTtsPlaybackState.PLAYING)
        persistCheckpoint(session)
        speaker.speak(session.utterance, flushQueue = true, startWordIndex = session.wordIndex)
    }

    fun setPreferredTranslatedText(preferTranslatedText: Boolean) {
        preferredTranslatedText = preferTranslatedText
    }

    suspend fun setAutoAdvanceChapter(autoAdvanceChapter: Boolean) {
        val session = mutableState.value.session ?: return
        if (session.autoAdvanceChapter == autoAdvanceChapter) return
        val updatedSession = session.copy(autoAdvanceChapter = autoAdvanceChapter)
        updateState(updatedSession, mutableState.value.playbackState)
        persistCheckpoint(updatedSession)
    }

    suspend fun switchToPreferredTextSource(preferTranslatedText: Boolean) {
        preferredTranslatedText = preferTranslatedText
        val session = mutableState.value.session ?: return
        val wantsTranslated = preferTranslatedText
        val isTranslated = session.textSource == NovelTtsTextSource.TRANSLATED
        if (wantsTranslated == isTranslated) return
        restartFromCurrentPosition(preferTranslatedText)
    }

    private suspend fun completeSession() {
        sessionStore.clearCheckpoint()
        mutableState.value = mutableState.value.copy(
            playbackState = NovelTtsPlaybackState.COMPLETED,
            session = null,
            pendingChapterHandoffId = null,
        )
    }

    private fun buildSession(
        resolvedChapter: NovelTtsResolvedChapter,
        utteranceId: String?,
        preferTranslatedText: Boolean,
        autoAdvanceChapter: Boolean,
        restoredWordIndex: Int,
    ): NovelTtsSession? {
        val textSource = if (preferTranslatedText && resolvedChapter.translatedModel != null) {
            NovelTtsTextSource.TRANSLATED
        } else {
            NovelTtsTextSource.ORIGINAL
        }
        val model = when (textSource) {
            NovelTtsTextSource.ORIGINAL -> resolvedChapter.originalModel
            NovelTtsTextSource.TRANSLATED -> resolvedChapter.translatedModel ?: resolvedChapter.originalModel
        }
        if (model.utterances.isEmpty()) return null
        val utteranceIndex = utteranceId
            ?.takeIf { it.isNotBlank() }
            ?.let { requestedId ->
                model.utterances.indexOfFirst { it.id == requestedId }.takeIf { it >= 0 }
            } ?: 0
        if (utteranceIndex !in model.utterances.indices) return null
        return NovelTtsSession(
            chapterId = resolvedChapter.chapterId,
            nextChapterId = resolvedChapter.nextChapterId,
            model = model,
            textSource = textSource,
            utteranceIndex = utteranceIndex,
            wordIndex = restoredWordIndex.coerceAtLeast(0),
            autoAdvanceChapter = autoAdvanceChapter,
        )
    }

    private fun updateState(
        session: NovelTtsSession,
        playbackState: NovelTtsPlaybackState,
    ) {
        mutableState.value = NovelTtsSessionUiState(
            playbackState = playbackState,
            session = session,
            pendingChapterHandoffId = mutableState.value.pendingChapterHandoffId,
        )
    }

    private fun clearPendingChapterHandoff() {
        val state = mutableState.value
        if (state.pendingChapterHandoffId == null) return
        mutableState.value = state.copy(pendingChapterHandoffId = null)
    }

    private suspend fun persistCheckpoint(session: NovelTtsSession) {
        sessionStore.saveCheckpoint(
            NovelTtsSessionCheckpoint(
                chapterId = session.chapterId,
                utteranceId = session.utterance.id,
                segmentId = session.utterance.segmentId,
                wordIndex = session.wordIndex,
                textSource = session.textSource,
                autoAdvanceChapter = session.autoAdvanceChapter,
            ),
        )
    }

    private suspend fun persistChapterHandoffCheckpoint(session: NovelTtsSession) {
        val nextChapterId = session.nextChapterId ?: return
        sessionStore.saveCheckpoint(
            NovelTtsSessionCheckpoint(
                chapterId = nextChapterId,
                utteranceId = "",
                segmentId = "",
                wordIndex = 0,
                textSource = if (preferredTranslatedText) {
                    NovelTtsTextSource.TRANSLATED
                } else {
                    NovelTtsTextSource.ORIGINAL
                },
                autoAdvanceChapter = session.autoAdvanceChapter,
            ),
        )
    }

    private suspend fun restartFromCurrentPosition(preferTranslatedText: Boolean) {
        val session = mutableState.value.session ?: return
        val cachedChapter = cachedResolvedChapter?.takeIf { it.chapterId == session.chapterId }
        val resolvedChapter = when {
            preferTranslatedText && cachedChapter?.translatedModel == null -> loadChapterCached(session.chapterId)
            cachedChapter != null -> cachedChapter
            else -> loadChapterCached(session.chapterId)
        } ?: return
        val rebuiltSession = buildSession(
            resolvedChapter = resolvedChapter,
            utteranceId = session.utterance.id,
            preferTranslatedText = preferTranslatedText,
            autoAdvanceChapter = session.autoAdvanceChapter,
            restoredWordIndex = session.wordIndex,
        ) ?: return
        updateState(rebuiltSession, NovelTtsPlaybackState.PLAYING)
        persistCheckpoint(rebuiltSession)
        speaker.speak(
            rebuiltSession.utterance,
            flushQueue = true,
            startWordIndex = rebuiltSession.wordIndex,
        )
    }

    /** Loads a chapter from [chapterSource], updating [cachedResolvedChapter] on success. */
    private suspend fun loadChapterCached(chapterId: Long): NovelTtsResolvedChapter? {
        return chapterSource.loadChapter(chapterId)?.also { cachedResolvedChapter = it }
    }
}
