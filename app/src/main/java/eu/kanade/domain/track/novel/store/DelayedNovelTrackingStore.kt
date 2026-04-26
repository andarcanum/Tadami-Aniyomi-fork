package eu.kanade.domain.track.novel.store

import android.content.Context
import androidx.core.content.edit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class DelayedNovelTrackingStore(context: Context) {

    private val preferences = context.getSharedPreferences("novel_tracking_queue", Context.MODE_PRIVATE)

    fun addNovel(trackId: Long, lastChapterRead: Double) {
        val previousLastChapterRead = preferences.getFloat(trackId.toString(), 0f)
        if (lastChapterRead > previousLastChapterRead) {
            logcat(LogPriority.DEBUG) { "Queuing novel track item: $trackId, last chapter read: $lastChapterRead" }
            preferences.edit {
                putFloat(trackId.toString(), lastChapterRead.toFloat())
            }
        }
    }

    fun removeNovelItem(trackId: Long) {
        preferences.edit {
            remove(trackId.toString())
        }
    }

    fun getNovelItems(): List<DelayedTrackingItem> {
        return preferences.all.mapNotNull {
            DelayedTrackingItem(
                trackId = it.key.toLong(),
                lastChapterRead = it.value.toString().toFloat(),
            )
        }
    }

    data class DelayedTrackingItem(
        val trackId: Long,
        val lastChapterRead: Float,
    )
}
