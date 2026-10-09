package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.io.IOException

class DiscoveryCoordinatorTest {

    private fun item(title: String, score: Double = 0.0) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = score,
    )

    private fun builder(type: DiscoveryRowType, items: List<DiscoveryRowItem>, fail: Boolean = false) =
        object : DiscoveryRowBuilder {
            override val rowType = type
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                if (fail) throw IOException("boom")
                return items
            }
        }

    private val baseContext = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.NOVEL,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
    )

    @Test
    fun `library history and hidden titles are filtered out`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, listOf(item("Overlord"), item("Hidden One"), item("In Library")))
        val coordinator = DiscoveryCoordinator(listOf(like))
        val feed = coordinator.buildFeed(
            baseContext.copy(
                libraryCleanTitles = setOf("in library"),
                hiddenCleanTitles = setOf("hidden one"),
            ),
        )
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Overlord")
        feed.failedRows shouldBe emptySet()
    }

    @Test
    fun `cross-row dedup keeps higher priority row`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, listOf(item("Dup")))
        val trend = builder(DiscoveryRowType.TREND, listOf(item("Dup"), item("Unique Trend")))
        val feed = DiscoveryCoordinator(listOf(trend, like)).buildFeed(baseContext)
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Dup")
        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Unique Trend")
    }

    @Test
    fun `row limit caps items preserving order`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, (1..25).map { item("T$it") })
        val feed = DiscoveryCoordinator(listOf(like), rowLimit = 20).buildFeed(baseContext)
        feed.rows[DiscoveryRowType.LIKE]?.size shouldBe 20
    }

    @Test
    fun `stale backfill is ordered by least recently shown, untimestamped last`() = runTest {
        val like = builder(
            DiscoveryRowType.LIKE,
            listOf(item("Fresh"), item("ShownRecent"), item("NoTimestamp"), item("ShownOld")),
        )
        val feed = DiscoveryCoordinator(listOf(like), rowLimit = 10).buildFeed(
            baseContext.copy(
                recentCleanTitles = setOf("shownrecent", "notimestamp", "shownold"),
                shownCutoffMap = mapOf(
                    "shownrecent" to 2_000L,
                    "shownold" to 1_000L,
                ),
            ),
        )
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe
            listOf("Fresh", "ShownOld", "ShownRecent", "NoTimestamp")
    }

    @Test
    fun `manual refresh stale backfill excludes current feed titles`() = runTest {
        // Ручной рефреш: B — тайтл ТЕКУЩЕЙ ленты (и «показанный»), C — показан раньше,
        // но из ленты уже выпал. Добор обязан вернуть C и НЕ вернуть B —
        // наполнение ряда реально сменяется, а не «новые + всё старое».
        val like = builder(
            DiscoveryRowType.LIKE,
            listOf(item("Fresh A"), item("Current B"), item("Fallen C")),
        )
        val feed = DiscoveryCoordinator(listOf(like), rowLimit = 10).buildFeed(
            baseContext.copy(
                recentCleanTitles = setOf("current b", "fallen c"),
                shownCutoffMap = mapOf("current b" to 2_000L, "fallen c" to 1_000L),
                currentFeedCleanTitles = setOf("current b"),
            ),
        )
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Fresh A", "Fallen C")

        // Фон (currentFeed пуст): прежнее поведение — добор возвращает обоих.
        val backgroundFeed = DiscoveryCoordinator(listOf(like), rowLimit = 10).buildFeed(
            baseContext.copy(
                recentCleanTitles = setOf("current b", "fallen c"),
                shownCutoffMap = mapOf("current b" to 2_000L, "fallen c" to 1_000L),
            ),
        )
        backgroundFeed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe
            listOf("Fresh A", "Fallen C", "Current B")
    }

    @Test
    fun `failing builder yields empty row and failedRows marker, others survive`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, emptyList(), fail = true)
        val trend = builder(DiscoveryRowType.TREND, listOf(item("Ok")))
        val feed = DiscoveryCoordinator(listOf(like, trend)).buildFeed(baseContext)
        feed.failedRows shouldBe setOf(DiscoveryRowType.LIKE)
        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Ok")
    }

    @Test
    fun `item matching library by alt title is excluded`() = runTest {
        val item = DiscoveryRowItem(
            title = "Kimetsu no Yaiba",
            cleanTitle = "kimetsu no yaiba",
            coverUrl = null,
            reason = null,
            seedTitle = null,
            provider = "anilist",
            score = 1.0,
            altCleanTitles = listOf("клинок рассекающий демонов"),
        )
        val context = DiscoveryBuildContext(
            mediaType = DiscoveryMediaType.MANGA,
            seeds = emptyList(),
            libraryCleanTitles = setOf("клинок рассекающий демонов"),
            historyCleanTitles = emptySet(),
            hiddenCleanTitles = emptySet(),
        )
        val feed = DiscoveryCoordinator(listOf(builder(DiscoveryRowType.TREND, listOf(item)))).buildFeed(context)
        feed.rows[DiscoveryRowType.TREND].orEmpty() shouldBe emptyList()
    }
}
