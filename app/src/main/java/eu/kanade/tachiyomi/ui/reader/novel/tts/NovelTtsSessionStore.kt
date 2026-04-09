package eu.kanade.tachiyomi.ui.reader.novel.tts

interface NovelTtsSessionStore {
    suspend fun saveCheckpoint(checkpoint: NovelTtsSessionCheckpoint)

    suspend fun loadCheckpoint(): NovelTtsSessionCheckpoint?

    suspend fun clearCheckpoint()
}

class InMemoryNovelTtsSessionStore : NovelTtsSessionStore {
    private var checkpoint: NovelTtsSessionCheckpoint? = null

    override suspend fun saveCheckpoint(checkpoint: NovelTtsSessionCheckpoint) {
        this.checkpoint = checkpoint
    }

    override suspend fun loadCheckpoint(): NovelTtsSessionCheckpoint? = checkpoint

    override suspend fun clearCheckpoint() {
        checkpoint = null
    }
}

object SharedNovelTtsSessionStore : NovelTtsSessionStore {
    @Volatile
    private var checkpoint: NovelTtsSessionCheckpoint? = null

    override suspend fun saveCheckpoint(checkpoint: NovelTtsSessionCheckpoint) {
        this.checkpoint = checkpoint
    }

    override suspend fun loadCheckpoint(): NovelTtsSessionCheckpoint? = checkpoint

    override suspend fun clearCheckpoint() {
        checkpoint = null
    }
}
