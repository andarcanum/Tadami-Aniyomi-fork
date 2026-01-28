package tachiyomi.domain.achievement.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats

interface ActivityDataRepository {
    fun getActivityData(days: Int = 365): Flow<List<DayActivity>>
    suspend fun getMonthStats(year: Int, month: Int): MonthStats
    suspend fun getCurrentMonthStats(): MonthStats
    suspend fun getPreviousMonthStats(): MonthStats
    suspend fun recordReading(chaptersCount: Int)
    suspend fun recordWatching(episodesCount: Int)
    suspend fun recordAppOpen()
}
