package eu.kanade.domain.description

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class DescriptionEngineTest {

    private val corpusSamples = listOf(
        "structured_tbate.txt",
        "markdown_links_and_titles.txt",
        "wall_ru_no_spaces.txt",
        "honest_wall_dialog.txt",
        "crlf_line_per_sentence.txt",
        "label_inline_ru.txt",
        "labels_rank_rating.txt",
    )

    private fun corpus(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/descriptions/$name")) { "missing corpus $name" }
            .bufferedReader()
            .readText()

    // ------------------------------------------------------------------ invariants

    @Test
    fun `no text is lost for any corpus sample`() {
        for (name in corpusSamples) {
            val raw = corpus(name)
            val blocks = DescriptionEngine.beautify(raw)
            val out = DescriptionEngine.blocksToText(blocks)
            assertNoTextLost(raw, out, name)
        }
    }

    private fun assertNoTextLost(raw: String, out: String, name: String) {
        val rawSeqs = wordSeqs(raw)
        val outSeqs = wordSeqs(out).toSet()
        val missing = rawSeqs.filterNot { it in outSeqs }
        check(missing.isEmpty()) { "$name: text lost -> $missing" }
    }

    private val seqRegex = Regex("[\\p{L}\\p{N}]+")

    private fun wordSeqs(text: String): List<String> = seqRegex.findAll(text)
        .map { it.value.lowercase() }
        .filter { !(it.length == 1 && it[0].isDigit()) } // ignore list markers ("1.")
        .toList()

    // ------------------------------------------------------------------ block shapes

    @Test
    fun `structured markdown is preserved as sections and lists`() {
        val blocks = DescriptionEngine.beautify(corpus("structured_tbate.txt"))
        blocks.filterIsInstance<DescriptionBlock.SectionHeading>().map { it.text } shouldBe
            listOf("Original Webcomic", "Alternative Titles")
        val items = blocks.filterIsInstance<DescriptionBlock.ListItem>()
        items.map { it.text } shouldBe listOf(
            "Digital",
            "Volumes",
            "La Vida después de la Muerte",
            "The Beginning After the End",
        )
        items.first().url shouldNotBe null
        blocks.filterIsInstance<DescriptionBlock.Paragraph>().size shouldBe 2
    }

    @Test
    fun `link-only lines become a links row`() {
        val blocks = DescriptionEngine.beautify(corpus("markdown_links_and_titles.txt"))
        val linksRow = blocks.filterIsInstance<DescriptionBlock.LinksRow>().single()
        linksRow.links.map { it.text } shouldBe listOf("MyAnimeList", "AniList")
        linksRow.links.map { it.url } shouldBe listOf(
            "https://myanimelist.net/manga/121496",
            "https://anilist.co/manga/105398",
        )
        blocks.filterIsInstance<DescriptionBlock.Paragraph>().size shouldBe 2
        blocks.filterIsInstance<DescriptionBlock.SectionHeading>().map { it.text } shouldBe
            listOf("Alternative Titles")
    }

    @Test
    fun `external-links label with bare url becomes heading and links row`() {
        val raw = """
            Копия прибыла! Удача — мое главное оружие.

            External links:
            https://www.mangaupdates.com/series/pbivvin/the-billion-luck-player
        """.trimIndent()
        val blocks = DescriptionEngine.beautify(raw)
        blocks.filterIsInstance<DescriptionBlock.Paragraph>().size shouldBe 1
        val heading = blocks.filterIsInstance<DescriptionBlock.SectionHeading>().single()
        heading.text shouldBe "External links"
        val row = blocks.filterIsInstance<DescriptionBlock.LinksRow>().single()
        row.links.single().url shouldBe "https://www.mangaupdates.com/series/pbivvin/the-billion-luck-player"
    }

    @Test
    fun `ru wall with missing spaces and inline alt titles is recovered`() {
        val blocks = DescriptionEngine.beautify(corpus("wall_ru_no_spaces.txt"))
        blocks.filterIsInstance<DescriptionBlock.SectionHeading>().map { it.text } shouldBe
            listOf("Альтернативные названия")
        blocks.filterIsInstance<DescriptionBlock.ListItem>().size shouldBe 4
        // The missing-space defect was repaired and the wall was split into several paragraphs.
        check(blocks.filterIsInstance<DescriptionBlock.Paragraph>().size >= 2) { "expected multiple paragraphs" }
        val combined = blocks.filterIsInstance<DescriptionBlock.Paragraph>().joinToString(" ") { it.text }
        combined shouldNotContain "бойцов.Сражение"
    }

    @Test
    fun `inline alternative-titles label becomes heading and list items`() {
        val blocks = DescriptionEngine.beautify(corpus("label_inline_ru.txt"))
        blocks.filterIsInstance<DescriptionBlock.SectionHeading>().map { it.text } shouldBe
            listOf("Альтернативные названия")
        blocks.filterIsInstance<DescriptionBlock.ListItem>().size shouldBe 4
        blocks.filterIsInstance<DescriptionBlock.Paragraph>().size shouldBe 3
    }

    @Test
    fun `rank and rating lines become label rows`() {
        val blocks = DescriptionEngine.beautify(corpus("labels_rank_rating.txt"))
        val rows = blocks.filterIsInstance<DescriptionBlock.LabelRow>()
        rows.map { it.label } shouldBe listOf("Rank", "Rating")
        rows.map { it.value } shouldBe listOf("#4", "9,46")
        blocks.filterIsInstance<DescriptionBlock.SectionHeading>().map { it.text } shouldBe
            listOf("Alternative Titles")
        blocks.filterIsInstance<DescriptionBlock.ListItem>().size shouldBe 2
    }

    @Test
    fun `crlf line-per-sentence wall is normalized and split into paragraphs`() {
        val raw = corpus("crlf_line_per_sentence.txt").replace("\n", "\r\n")
        val blocks = DescriptionEngine.beautify(raw)
        check(blocks.isNotEmpty()) { "expected blocks" }
        blocks.all { it is DescriptionBlock.Paragraph } shouldBe true
        check(blocks.filterIsInstance<DescriptionBlock.Paragraph>().size >= 3) { "expected multiple paragraphs" }
    }

    @Test
    fun `short honest wall stays a single paragraph`() {
        val raw = corpus("honest_wall_dialog.txt")
        val blocks = DescriptionEngine.beautify(raw)
        blocks shouldBe listOf(DescriptionBlock.Paragraph(raw.trim()))
    }

    // ------------------------------------------------------------------ unit behavior

    @Test
    fun `long wall is not split after abbreviations or initials`() {
        val sentence =
            "Dr. Watson went with Mr. Smith to the U.S.A. yesterday. They solved the strange case before noon. "
        val raw = sentence.repeat(12)
        val blocks = DescriptionEngine.beautify(raw)
        val text = DescriptionEngine.blocksToText(blocks)
        text shouldContain "Dr. Watson"
        text shouldContain "Mr. Smith"
        text shouldContain "U.S.A."
        check(blocks.filterIsInstance<DescriptionBlock.Paragraph>().size > 1) { "expected multiple paragraphs" }
    }

    @Test
    fun `empty and blank descriptions produce no blocks`() {
        DescriptionEngine.beautify("") shouldBe emptyList<DescriptionBlock>()
        DescriptionEngine.beautify("   \n\t  ") shouldBe emptyList<DescriptionBlock>()
    }

    @Test
    fun `multi-label metadata block parses each label row cleanly without swallowing subsequent labels`() {
        val raw = """
            Альт. название: Eleceed
            Другие названия: |Illegsideu, 일렉сид, بذور الكهرباء, تجاوز
            Год выпуска: 2018

            Кайден, таинственная личность, словно ниспосланная с небес в виде кометы...
        """.trimIndent()
        val blocks = DescriptionEngine.beautify(raw)

        val labels = blocks.filterIsInstance<DescriptionBlock.LabelRow>()
        labels.map { it.label } shouldBe listOf("Альт. название", "Другие названия", "Год выпуска")
        labels.map { it.value } shouldBe listOf(
            "Eleceed",
            "Illegsideu, 일렉сид, بذور الكهرباء, تجاوز",
            "2018",
        )
        val paragraphs = blocks.filterIsInstance<DescriptionBlock.Paragraph>()
        paragraphs.size shouldBe 1
        paragraphs.first().text shouldContain "Кайден"
    }

    @Test
    fun `pipe delimited alternative titles are parsed into clean items without leading pipes`() {
        val raw = "Другие названия: | Illegsideu | 일렉сид | Eleceed"
        val blocks = DescriptionEngine.beautify(raw)
        val heading = blocks.filterIsInstance<DescriptionBlock.SectionHeading>().single()
        heading.text shouldBe "Другие названия"
        val items = blocks.filterIsInstance<DescriptionBlock.ListItem>()
        items.map { it.text } shouldBe listOf("Illegsideu", "일렉сид", "Eleceed")
    }

    @Test
    fun `blocks survive json round trip`() {
        val blocks = DescriptionEngine.beautify(corpus("structured_tbate.txt"))
        val decoded = DescriptionBlockCodec.decode(DescriptionBlockCodec.encode(blocks))
        decoded shouldBe blocks
        DescriptionBlockCodec.decode(null) shouldBe null
        DescriptionBlockCodec.decode("not json") shouldBe null
    }
}
