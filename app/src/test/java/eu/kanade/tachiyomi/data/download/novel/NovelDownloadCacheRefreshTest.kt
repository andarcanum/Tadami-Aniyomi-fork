package eu.kanade.tachiyomi.data.download.novel

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NovelDownloadCacheRefreshTest {

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `known empty counts do not trigger another lookup`() = runTest {
        var calls = 0
        val cache = createCache(backgroundScope, lookup = {
            calls++
            emptySet()
        })
        runCurrent()
        val novel = Novel.create().copy(id = 1L)

        cache.getDownloadCount(novel) shouldBe 0
        runCurrent()
        repeat(20) { cache.getDownloadCount(novel) shouldBe 0 }
        runCurrent()
        calls shouldBe 1
    }

    @Test
    fun `refresh publishes count chapter ids and downloaded title together`() = runTest {
        val cache = createCache(backgroundScope, lookup = { setOf(10L, 11L) })
        runCurrent()
        val novel = Novel.create().copy(id = 1L)

        cache.getDownloadCount(novel)
        runCurrent()

        cache.getDownloadCount(novel) shouldBe 2
        cache.getDownloadedChapterIds(1L) shouldBe setOf(10L, 11L)
        cache.downloadedIds.value shouldBe setOf(1L)
    }

    @Test
    fun `late lookup cannot overwrite a newer download event`() = runTest {
        val novel = Novel.create().copy(id = 1L)
        lateinit var cache: NovelDownloadCache
        cache = createCache(backgroundScope, lookup = {
            cache.onChaptersChanged(novel, setOf(20L), downloaded = true)
            setOf(10L)
        })
        runCurrent()

        cache.getDownloadCount(novel)
        runCurrent()

        cache.getDownloadedChapterIds(1L) shouldBe setOf(20L)
        cache.getDownloadCount(novel) shouldBe 1
    }

    @Test
    fun `invalidated lookup cannot resurrect the previous storage generation`() = runTest {
        lateinit var cache: NovelDownloadCache
        cache = createCache(backgroundScope, lookup = {
            cache.invalidateAll()
            setOf(10L)
        })
        runCurrent()

        cache.getDownloadCount(Novel.create().copy(id = 1L))
        runCurrent()

        cache.getDownloadedChapterIds(1L) shouldBe null
        cache.downloadedIds.value shouldBe emptySet()
    }

    @Test
    fun `renewal does not hold cache monitor during filesystem lookup`() = runTest {
        val novel = Novel.create().copy(id = 1L)
        val repository = createRepository(novel)
        val executor = Executors.newSingleThreadExecutor()
        lateinit var cache: NovelDownloadCache
        try {
            cache = createCache(backgroundScope, repository, lookup = {
                try {
                    executor.submit {
                        cache.onChaptersChanged(novel, setOf(20L), downloaded = true)
                    }.get(2, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    throw AssertionError("Cache monitor blocked a concurrent mutation", e)
                }
                setOf(10L)
            })
            runCurrent()
            cache.renewCache()
            runCurrent()

            cache.getDownloadedChapterIds(1L) shouldBe setOf(20L)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `renewal request arriving during scan is replayed once`() = runTest {
        val novel = Novel.create().copy(id = 1L)
        var scans = 0
        lateinit var cache: NovelDownloadCache
        cache = createCache(backgroundScope, createRepository(novel), lookup = {
            scans++
            if (scans == 1) cache.renewCache()
            setOf(10L)
        })
        runCurrent()

        cache.renewCache()
        runCurrent()

        scans shouldBe 2
        cache.getDownloadedChapterIds(1L) shouldBe setOf(10L)
    }

    private fun createRepository(book: Novel? = null): NovelRepository = mockk {
        coEvery { getLibraryNovel() } returns if (book == null) {
            emptyList()
        } else {
            listOf(mockk { every { novel } returns book })
        }
        coEvery { getReadNovelNotInLibrary() } returns emptyList()
    }

    private fun createCache(
        scope: CoroutineScope,
        repository: NovelRepository = createRepository(),
        lookup: (Novel) -> Set<Long>,
    ): NovelDownloadCache {
        val file = tempDir.resolve("dl_novel_cache_v1").toFile()
        file.writeBytes(ProtoBuf.encodeToByteArray(NovelDiskCache(data = emptyMap())))
        return NovelDownloadCache(
            storageManager = mockk<StorageManager> {
                every { changes } returns MutableSharedFlow()
            },
            sourceManager = mockk<NovelSourceManager> {
                every { isInitialized } returns MutableStateFlow(false)
            },
            novelRepository = repository,
            scope = scope,
            cacheFileProvider = { file },
            downloadedChapterIdsLookup = lookup,
        )
    }
}
