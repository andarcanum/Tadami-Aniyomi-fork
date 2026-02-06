package tachiyomi.domain.source.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.novel.model.StubNovelSource

interface NovelStubSourceRepository {
    fun subscribeAllNovel(): Flow<List<StubNovelSource>>

    suspend fun getStubNovelSource(id: Long): StubNovelSource?

    suspend fun upsertStubNovelSource(id: Long, lang: String, name: String)
}
