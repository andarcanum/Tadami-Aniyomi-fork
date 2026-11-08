package tachiyomi.domain.easteregg.crystal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CrystalMemorialProgressCounterTest {

    private val counter = CrystalProgressCounter()

    @Test
    fun `manga and novel require ten distinct read chapters not nine`() {
        for (media in listOf(CrystalMedia.MANGA, CrystalMedia.NOVEL)) {
            val nine = entry(media, completed(9))
            counter.evaluate(listOf(nine)).isEligible shouldBe false
            val ten = counter.evaluate(listOf(entry(media, completed(10))))
            ten.isEligible shouldBe true
            ten.entries.single().requiredCount shouldBe 10
            ten.entries.single().distinctCompleted shouldBe 10
        }
    }

    @Test
    fun `anime requires five distinct seen episodes not four`() {
        counter.evaluate(listOf(entry(CrystalMedia.ANIME, completed(4)))).isEligible shouldBe false
        val five = counter.evaluate(listOf(entry(CrystalMedia.ANIME, completed(5))))
        five.isEligible shouldBe true
        five.entries.single().requiredCount shouldBe 5
    }

    @Test
    fun `one episode numbered five is still one seen episode`() {
        val result = counter.evaluate(listOf(entry(CrystalMedia.ANIME, listOf(item(1, 5.0)))))
        result.isEligible shouldBe false
        result.entries.single().distinctCompleted shouldBe 1
    }

    @Test
    fun `progress is never pooled across local entries works or media`() {
        val result = counter.evaluate(
            listOf(
                entry(CrystalMedia.ANIME, completed(3), id = 1),
                entry(CrystalMedia.ANIME, completed(3), id = 2),
                entry(CrystalMedia.MANGA, completed(6), id = 3),
                entry(CrystalMedia.NOVEL, completed(6), id = 4),
                entry(CrystalMedia.ANIME, completed(3), id = 5, title = "My Roommate is a Cat"),
            ),
        )
        result.isEligible shouldBe false
        result.eligibleEntries shouldBe emptyList()
        result.entries.map { it.distinctCompleted } shouldBe listOf(3, 3, 6, 6, 3)
    }

    @Test
    fun `one qualifying local adaptation suffices independently of the others`() {
        val result = counter.evaluate(
            listOf(
                entry(CrystalMedia.MANGA, completed(1), id = 1),
                entry(CrystalMedia.ANIME, completed(5), id = 2, title = "My Roommate is a Cat"),
            ),
        )
        result.isEligible shouldBe true
        result.eligibleEntries.map { it.entryId } shouldBe listOf(2L)
        result.eligibleEntries.single().titleMatch.adaptation shouldBe
            CrystalAdaptation(CrystalWork.ROOMMATE_CAT, CrystalMedia.ANIME)
    }

    @Test
    fun `nonlocal unsupported and unmatched entries cannot qualify`() {
        val result = counter.evaluate(
            listOf(
                entry(CrystalMedia.ANIME, completed(20)).copy(isLocal = false),
                entry(CrystalMedia.NOVEL, completed(20), title = "My Roommate is a Cat"),
                entry(CrystalMedia.MANGA, completed(20), title = "Unrelated"),
            ),
        )
        result.isEligible shouldBe false
        result.entries.map { it.distinctCompleted } shouldBe listOf(20, 20, 20)
    }

    @Test
    fun `duplicate scanlations same numbers and same URLs count once`() {
        val rows = listOf(
            item(1, 1.0, "scanlator-a/ch1"),
            item(2, 1.0, "scanlator-b/ch1"),
            item(3, 2.0, "scanlator-b/ch1"),
            item(4, 3.0, "chapter/3"),
            item(5, 4.0, "chapter/3"),
        )
        count(rows) shouldBe 2
        count(rows.reversed()) shouldBe 2
    }

    @Test
    fun `fractional finite nonnegative numbers stay distinct and negative zero equals zero`() {
        val rows = listOf(
            item(1, 0.0),
            item(2, -0.0),
            item(3, 0.5),
            item(4, 1.0),
            item(5, 1.5),
            item(6, 1.50001),
            item(7, 1.5),
        )
        count(rows) shouldBe 5
        counter.evaluate(listOf(entry(CrystalMedia.ANIME, rows))).isEligible shouldBe true
    }

    @Test
    fun `unknown numbers deduplicate via URL else stable row ID`() {
        count(
            listOf(
                item(1, -1.0, "unknown/one"),
                item(2, Double.NaN, "unknown/one"),
                item(3, -1.0, "unknown/two"),
                item(4, Double.NaN),
                item(4, -1.0, "  "),
                item(5, null),
            ),
        ) shouldBe 4
    }

    @Test
    fun `infinite and other negative numbers are unknown rather than a shared number`() {
        count(
            listOf(
                item(1, Double.POSITIVE_INFINITY),
                item(2, Double.POSITIVE_INFINITY),
                item(3, Double.NEGATIVE_INFINITY),
                item(4, -2.0),
                item(5, -2.0),
            ),
        ) shouldBe 5
    }

    @Test
    fun `known and unknown copies sharing URL are one chapter`() {
        count(listOf(item(1, 1.0, "same"), item(2, -1.0, "same"), item(3, Double.NaN, "same"))) shouldBe 1
    }

    @Test
    fun `current read or seen flags suffice without time or event history`() {
        val oldOrManualFlags = completed(5)
        val unread = (6..20).map { item(it.toLong(), it.toDouble()).copy(isCompleted = false) }
        count(oldOrManualFlags + unread) shouldBe 5
        counter.evaluate(listOf(entry(CrystalMedia.ANIME, oldOrManualFlags + unread))).isEligible shouldBe true
        count(unread) shouldBe 0
    }

    @Test
    fun `unread duplicate cannot erase a completed flag and connects shared identities`() {
        val rows = listOf(
            item(1, 1.0, "a"),
            item(2, 1.0, "b").copy(isCompleted = false),
            item(3, 2.0, "b"),
        )
        count(rows) shouldBe 1
        count(rows.reversed()) shouldBe 1
    }

    @Test
    fun `empty input and replayed identical rows never create progress`() {
        counter.evaluate(emptyList()).isEligible shouldBe false
        count(emptyList()) shouldBe 0
        count(List(100) { item(1, null) }) shouldBe 1
    }

    private fun count(items: List<CrystalProgressItem>) =
        counter.evaluate(listOf(entry(CrystalMedia.MANGA, items))).entries.single().distinctCompleted

    private fun completed(count: Int) = (1..count).map { item(it.toLong(), it.toDouble()) }

    private fun item(id: Long, number: Double?, url: String? = null) =
        CrystalProgressItem(rowId = id, number = number, url = url, isCompleted = true)

    private fun entry(
        media: CrystalMedia,
        items: List<CrystalProgressItem>,
        id: Long = 1L,
        title: String = "Sakurasou no Pet na Kanojo",
    ) = CrystalProgressEntry(id, CrystalTitleInput(media, title), items, isLocal = true)
}
