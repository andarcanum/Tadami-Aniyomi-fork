package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.domain.items.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import kotlin.math.ceil
import kotlin.random.Random

class MangaChapterSortAllocationTest {

    @Test
    fun `characterize unmocked allocation of both actual item sorting consumers`() {
        val counter = allocationCounter()
        Assumptions.assumeTrue(counter != null, "ThreadMXBean allocation counter unavailable")
        val availableCounter = checkNotNull(counter)
        val items = fixtureItems()
        val manga = Manga.create().copy(
            id = 10L,
            source = 1L,
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_ASC,
        )
        report(
            listOf(
                "M12_ALLOC_PROTOCOL", "N=1000", "seed=20261115", "NUMBER_ASC", "warmup=5x5", "samples=30x5",
                "jdk=${System.getProperty("java.version")}", "vm=${System.getProperty("java.vm.name")}",
                "unmocked", "current-thread Java allocation includes fixed reflective probe overhead",
                "not Android RSS native GPU or device frame time",
            ),
        )
        report(listOf("M12_ALLOC_COLUMNS", "consumer", "N", "batch", "B_per_sort", "ns_per_sort", "checksum"))
        measure("item-filter", false, items, manga, availableCounter)
        measure("processed-state", true, items, manga, availableCounter)
    }

    private fun measure(
        consumer: String,
        useState: Boolean,
        items: List<ChapterList.Item>,
        manga: Manga,
        counter: AllocationCounter,
    ) {
        val outputs = arrayOfNulls<List<ChapterList.Item>>(5)
        var expectedChecksum: Long? = null
        repeat(5) {
            val states = if (useState) Array(5) { state(manga, items) } else null
            consume(items, manga, states, outputs)
            val checksum = validateOutputs(outputs)
            expectedChecksum?.let { checksum shouldBe it }
            expectedChecksum = checksum
        }
        val samples = mutableListOf<Sample>()
        repeat(30) { batch ->
            // State construction and result-slot allocation must not enter the measured window.
            val states = if (useState) Array(5) { state(manga, items) } else null
            val beforeBytes = counter.readBytes()
            val beforeNanos = System.nanoTime()
            consume(items, manga, states, outputs)
            val afterNanos = System.nanoTime()
            val afterBytes = counter.readBytes()
            Assumptions.assumeTrue(
                beforeBytes >= 0L && afterBytes >= beforeBytes,
                "Allocation counter became unavailable",
            )
            val checksum = validateOutputs(outputs)
            checksum shouldBe expectedChecksum
            val sample = Sample((afterBytes - beforeBytes) / 5.0, (afterNanos - beforeNanos) / 5.0)
            samples += sample
            report(listOf("M12_ALLOC_RAW", consumer, 1000, batch, sample.bytesPerSort, sample.nanosPerSort, checksum))
        }
        summarize(consumer, "B_per_sort", samples.map { it.bytesPerSort })
        summarize(consumer, "ns_per_sort", samples.map { it.nanosPerSort })
    }

    private fun consume(
        items: List<ChapterList.Item>,
        manga: Manga,
        states: Array<MangaScreenModel.State.Success>?,
        outputs: Array<List<ChapterList.Item>?>,
    ) {
        for (index in outputs.indices) {
            outputs[index] = if (states == null) {
                items.applyFilters(manga, downloadedOnly = false).toList()
            } else {
                states[index].processedChapters
            }
        }
    }

    private fun validateOutputs(outputs: Array<List<ChapterList.Item>?>): Long {
        var batchChecksum = 0L
        for (output in outputs) {
            val items = checkNotNull(output)
            items.size shouldBe 1000
            var checksum = 0L
            for (item in items) checksum = checksum * 31L + item.chapter.id
            batchChecksum = batchChecksum * 31L + checksum
        }
        return batchChecksum
    }

    private fun summarize(consumer: String, metric: String, samples: List<Double>) {
        val sorted = samples.sorted()
        val median = (sorted[14] + sorted[15]) / 2.0
        fun percentile(value: Double): Double = sorted[(ceil(value * sorted.size).toInt() - 1).coerceAtLeast(0)]
        report(
            listOf(
                "M12_ALLOC_SUMMARY", consumer, metric, "n=30", "batchSize=5", "median=$median",
                "p95=${percentile(0.95)}", "min=${sorted.first()}", "max=${sorted.last()}",
                "IQR=${percentile(0.75) - percentile(0.25)}",
            ),
        )
    }

    private fun report(values: List<Any>) {
        println(values.joinToString(separator = 9.toChar().toString()))
    }

    private fun fixtureItems(): List<ChapterList.Item> {
        return List(1000) { index ->
            val number = when (index % 101) {
                0 -> -2.0
                1 -> -1.0
                else -> (index % 37).toDouble()
            }
            ChapterList.Item(
                chapter = Chapter.create().copy(
                    id = index + 1L,
                    mangaId = 10L,
                    sourceOrder = (999 - index).toLong(),
                    chapterNumber = number,
                    dateUpload = (index % 53) * 1000L,
                    name = "Chapter ${index % 41}",
                    url = "/test/chapter-$index",
                ),
                downloadState = MangaDownload.State.NOT_DOWNLOADED,
                downloadProgress = 0,
                selected = false,
            )
        }.shuffled(Random(20261115))
    }

    private fun state(manga: Manga, items: List<ChapterList.Item>): MangaScreenModel.State.Success {
        return MangaScreenModel.State.Success(
            manga = manga,
            source = SortingSource,
            isFromSource = false,
            chapters = items,
            availableScanlators = emptySet(),
            scanlatorChapterCounts = emptyMap(),
            excludedScanlators = emptySet(),
        )
    }

    private class AllocationCounter(val readBytes: () -> Long)

    private fun allocationCounter(): AllocationCounter? {
        try {
            val factory = Class.forName("java.lang.management.ManagementFactory")
            val bean = factory.getMethod("getThreadMXBean").invoke(null)
            val api = Class.forName("com.sun.management.ThreadMXBean")
            check(api.isInstance(bean)) { "ThreadMXBean implementation unsupported" }
            check(api.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true) {
                "Allocation counting unsupported"
            }
            check(api.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean) == true) {
                "Allocation counting disabled"
            }
            val read = api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
            val threadId = Thread.currentThread().id
            val counter = AllocationCounter { (read.invoke(bean, threadId) as Number).toLong() }
            check(counter.readBytes() >= 0L) { "Current-thread allocation counter unavailable" }
            return counter
        } catch (error: Exception) {
            report(listOf("M12_ALLOC_UNAVAILABLE", error.javaClass.simpleName, error.message.orEmpty()))
            return null
        }
    }

    private data class Sample(val bytesPerSort: Double, val nanosPerSort: Double)

    private object SortingSource : MangaSource {
        override val id: Long = 1L
        override val name: String = "SortingSource"
    }
}
