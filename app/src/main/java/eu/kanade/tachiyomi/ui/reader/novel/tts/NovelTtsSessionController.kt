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

    suspend fun startFromCurrentPosition(
        chapterId: Long,
        utteranceId: String?,
        preferTranslatedText: Boolean,
        autoAdvanceChapter: Boolean,
    ) {
        val resolvedChapter = chapterSource.loadChapter(chapterId) ?: return
        val session = buildSession(
            resolvedChapter = resolvedChapter,
            utteranceId = utteranceId,
            preferTranslatedText = preferTranslatedText,
            autoAdvanceChapter = autoAdvanceChapter,
            restoredWordIndex = 0,
        ) ?: return
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
            val nextChapter = chapterSource.loadChapter(session.nextChapterId) ?: run {
                completeSession()
                return
            }
            val nextSession = buildSession(
                resolvedChapter = nextChapter,
                utteranceId = null,
                preferTranslatedText = session.textSource == NovelTtsTextSource.TRANSLATED,
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
        val updatedSession = session.copy(wordIndex = wordIndex.coerceAtLeast(0))
        updateState(updatedSession, mutableState.value.playbackState)
        persistCheckpoint(updatedSession)
    }

    suspend fun restoreFromCheckpoint() {
        val checkpoint = sessionStore.loadCheckpoint() ?: return
        val resolvedChapter = chapterSource.loadChapter(checkpoint.chapterId) ?: return
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

    private suspend fun completeSession() {
        sessionStore.clearCheckpoint()
        mutableState.value = mutableState.value.copy(
            playbackState = NovelTtsPlaybackState.COMPLETED,
            session = null,
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
        val utteranceIndex = utteranceId?.let { requestedId ->
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
        )
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
}
