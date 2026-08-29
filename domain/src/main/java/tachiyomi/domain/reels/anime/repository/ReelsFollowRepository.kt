package tachiyomi.domain.reels.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.reels.anime.model.ReelsFollow

interface ReelsFollowRepository {

    fun subscribeAll(): Flow<List<ReelsFollow>>

    suspend fun getAll(): List<ReelsFollow>

    suspend fun getBySource(sourceId: Long): List<ReelsFollow>

    suspend fun getCreatorsBySource(sourceId: Long): List<String>

    suspend fun insert(follow: ReelsFollow)

    suspend fun delete(sourceId: Long, creator: String)
}
