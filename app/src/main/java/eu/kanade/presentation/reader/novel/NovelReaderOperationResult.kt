package eu.kanade.presentation.reader.novel

import kotlinx.coroutines.CancellationException

/** Operation failures are data; cancellation still belongs to the owning composition/job. */
internal suspend fun <T> runNovelReaderOperation(operation: suspend () -> T): Result<T> {
    return try {
        Result.success(operation())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}
