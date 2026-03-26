package tachiyomi.data.source.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.repository.NovelStubSourceRepository

class NovelStubSourceRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelStubSourceRepository {

    override fun subscribeAllNovel(): Flow<List<StubNovelSource>> {
        return handler.subscribeToList { db -> db.novelsourcesQueries.findAll(::mapStubSource) }
    }

    override suspend fun getStubNovelSource(id: Long): StubNovelSource? {
        return handler.awaitOneOrNull { db ->
            db.novelsourcesQueries.findOne(
                id,
                ::mapStubSource,
            )
        }
    }

    override suspend fun upsertStubNovelSource(id: Long, lang: String, name: String) {
        handler.await { db -> db.novelsourcesQueries.upsert(id, lang, name) }
    }

    private fun mapStubSource(
        id: Long,
        lang: String,
        name: String,
    ): StubNovelSource = StubNovelSource(id = id, lang = lang, name = name)
}
