package tachiyomi.domain.extension.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.extension.novel.model.NovelPlugin

interface NovelPluginRepository {
    fun subscribeAll(): Flow<List<NovelPlugin.Installed>>

    suspend fun getAll(): List<NovelPlugin.Installed>

    suspend fun getById(id: String): NovelPlugin.Installed?

    suspend fun upsert(plugin: NovelPlugin.Installed)

    suspend fun delete(id: String)
}
