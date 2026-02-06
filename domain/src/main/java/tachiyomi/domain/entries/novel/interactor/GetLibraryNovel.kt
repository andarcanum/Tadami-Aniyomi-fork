package tachiyomi.domain.entries.novel.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.library.novel.LibraryNovel
import kotlin.time.Duration.Companion.seconds

class GetLibraryNovel(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(): List<LibraryNovel> {
        return novelRepository.getLibraryNovel()
    }

    fun subscribe(): Flow<List<LibraryNovel>> {
        return novelRepository.getLibraryNovelAsFlow()
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryNovel.logcat(LogPriority.ERROR, it)
            }
    }
}