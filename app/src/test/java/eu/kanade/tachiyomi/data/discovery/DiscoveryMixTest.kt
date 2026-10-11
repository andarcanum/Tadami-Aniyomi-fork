package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryMixTest {

    private fun item(
        type: DiscoveryRowType,
        title: String,
        score: Double = 1.0,
        seed: String? = null,
    ) = DiscoveryRowItem(title, title.lowercase(), null, null, seed, "p", score)

    @Test
    fun `interleave respects quotas round-robin`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to (1..6).map { item(DiscoveryRowType.LIKE, "L$it", 6.0 - it) },
            DiscoveryRowType.TASTE to (1..4).map { item(DiscoveryRowType.TASTE, "T$it", 4.0 - it) },
            DiscoveryRowType.TREND to (1..3).map { item(DiscoveryRowType.TREND, "F$it", 3.0 - it) },
            DiscoveryRowType.SOURCE to (1..3).map { item(DiscoveryRowType.SOURCE, "S$it", 2.0 - it) },
        )
        val mix = interleaveMix(rows)
        mix.size shouldBe 16
        mix.take(4).map { it.title } shouldBe listOf("L1", "T1", "F1", "S1")
        mix.count { it.title.startsWith("L") } shouldBe 6
        mix.count { it.title.startsWith("T") } shouldBe 4
    }

    @Test
    fun `default quotas sum to 30 items`() {
        val quotas = DiscoveryMixQuotas()
        quotas.total shouldBe 30
        quotas.similar shouldBe 10
        quotas.taste shouldBe 8
        quotas.fresh shouldBe 6
        quotas.source shouldBe 6
    }

    @Test
    fun `interleave deduplicates by cleanTitle across rows`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to listOf(
                item(DiscoveryRowType.LIKE, "Solo Leveling", 5.0),
                item(DiscoveryRowType.LIKE, "Unique Like", 4.0),
            ),
            DiscoveryRowType.TASTE to listOf(
                item(DiscoveryRowType.TASTE, "Solo Leveling", 4.5),
                item(DiscoveryRowType.TASTE, "Unique Taste", 3.0),
            ),
            DiscoveryRowType.TREND to listOf(
                item(DiscoveryRowType.TREND, "Solo Leveling", 4.0),
                item(DiscoveryRowType.TREND, "Unique Trend", 2.0),
            ),
        )
        val mix = interleaveMix(rows, total = 10)
        mix.map { it.cleanTitle } shouldBe listOf("solo leveling", "unique taste", "unique trend", "unique like")
        mix.size shouldBe 4
    }

    @Test
    fun `interleave deduplicates intra-row duplicate items`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to listOf(
                item(DiscoveryRowType.LIKE, "Solo Leveling", 5.0),
                item(DiscoveryRowType.LIKE, "solo leveling", 4.8),
                item(DiscoveryRowType.LIKE, "SOLO LEVELING", 4.6),
                item(DiscoveryRowType.LIKE, "Frieren", 3.0),
            ),
        )
        val mix = interleaveMix(rows, total = 10)
        mix.map { it.cleanTitle } shouldBe listOf("solo leveling", "frieren")
        mix.size shouldBe 2
    }

    @Test
    fun `interleave handles zero or negative total and empty rows`() {
        interleaveMix(emptyMap(), total = 10) shouldBe emptyList()
        interleaveMix(mapOf(DiscoveryRowType.LIKE to listOf(item(DiscoveryRowType.LIKE, "A"))), total = 0) shouldBe
            emptyList()
        interleaveMix(mapOf(DiscoveryRowType.LIKE to listOf(item(DiscoveryRowType.LIKE, "A"))), total = -5) shouldBe
            emptyList()
    }

    @Test
    fun `sparse rows yield fewer items without crash`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to listOf(item(DiscoveryRowType.LIKE, "L1", 1.0)),
            DiscoveryRowType.TREND to (1..3).map { item(DiscoveryRowType.TREND, "F$it", 3.0 - it) },
        )
        val mix = interleaveMix(rows)
        mix.size shouldBe 4
        mix.map { it.title } shouldBe listOf("L1", "F1", "F2", "F3")
    }

    @Test
    fun `fill remainder by score across signals`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to (1..2).map { item(DiscoveryRowType.LIKE, "L$it", it.toDouble()) },
            DiscoveryRowType.TREND to (1..10).map { item(DiscoveryRowType.TREND, "T$it", (11 - it).toDouble()) },
        )
        val mix = interleaveMix(rows)
        // round-robin: L1,T1,L2,T2,T3,T4 (квоты L=2,T=4 исчерпаны) = 6, добивка по скору: T5..T10
        mix.size shouldBe 12
        mix.take(6).map { it.title } shouldBe listOf("L1", "T1", "L2", "T2", "T3", "T4")
        mix.last().title shouldBe "T10"
    }

    @Test
    fun `overlap of two seeds boosts score and joins reasons`() {
        val seedA = listOf(
            item(DiscoveryRowType.LIKE, "Dup", 2.0, seed = "A"),
            item(DiscoveryRowType.LIKE, "OnlyA", 1.0, seed = "A"),
        )
        val seedB = listOf(item(DiscoveryRowType.LIKE, "Dup", 1.5, seed = "B"))
        val merged = mergeSeedResults(listOf(seedA, seedB))
        val dup = merged.first { it.cleanTitle == "dup" }
        dup.score shouldBe 2.0 * 1.5 // k=2 → maxScore * (1 + 0.5)
        dup.seedTitle shouldBe "A, B"
        merged.first().cleanTitle shouldBe "dup"
    }

    @Test
    fun `per-seed cap limits cards from one seed`() {
        val seedA = (1..5).map { item(DiscoveryRowType.LIKE, "A$it", 5.0 - it, seed = "A") }
        val merged = mergeSeedResults(listOf(seedA), perSeedCap = 2)
        merged.size shouldBe 2
        merged.map { it.title } shouldBe listOf("A1", "A2")
    }

    @Test
    fun `taste profile weights genres by recency and window`() {
        val now = 1_800_000_000_000L
        val day = 86_400_000L
        val candidates = listOf(
            DiscoverySeedInput(
                entryId = 1,
                title = "X",
                genres = listOf("Drama", "Action"),
                lastInteraction = now - 10 * day,
            ),
            // вне окна 90 дней — не участвует
            DiscoverySeedInput(
                entryId = 2,
                title = "Y",
                genres = listOf("Drama"),
                lastInteraction = now - 200 * day,
            ),
            DiscoverySeedInput(
                entryId = 3,
                title = "Z",
                genres = listOf("Action"),
                lastInteraction = now - 2 * day,
            ),
        )
        val profile = buildTasteProfile(candidates, nowMs = now)
        profile.map { it.first } shouldBe listOf("Action", "Drama")
        (tasteScore(listOf("Action", "Comedy"), profile) > tasteScore(listOf("Drama"), profile)) shouldBe true
        matchedGenres(listOf("Drama", "Action"), profile) shouldBe listOf("Drama", "Action")
    }

    @Test
    fun `dropped titles penalize their genres in taste profile`() {
        val now = 1_000_000_000L
        val dropped = DiscoverySeedInput(
            entryId = 1,
            title = "Dropped Isekai",
            genres = listOf("Isekai"),
            dateAdded = now - 2 * 24 * 3600_000L,
            isDropped = true,
        )
        val active = DiscoverySeedInput(
            entryId = 2,
            title = "Active Drama",
            genres = listOf("Drama"),
            dateAdded = now - 2 * 24 * 3600_000L,
        )
        val profile = buildTasteProfile(listOf(dropped, active), nowMs = now).toMap()
        profile.containsKey("Isekai") shouldBe false
        profile["Drama"]!! shouldBeGreaterThan 0.0
    }

    @Test
    fun `genreDecayDays maps categories and defaults to 30`() {
        genreDecayDays("Science Fiction") shouldBe 180.0
        genreDecayDays("исекай") shouldBe 20.0
        genreDecayDays("romance") shouldBe 30.0
    }

    @Test
    fun `fundamental genres decay slower than trendy ones`() {
        val now = 1_000_000_000L
        val age45 = now - 45L * 24 * 3600_000L
        val profile = buildTasteProfile(
            listOf(
                DiscoverySeedInput(
                    entryId = 1,
                    title = "Old SciFi",
                    genres = listOf("Science Fiction"),
                    dateAdded = age45,
                ),
                DiscoverySeedInput(
                    entryId = 2,
                    title = "Old Isekai",
                    genres = listOf("Isekai"),
                    dateAdded = age45,
                ),
            ),
            nowMs = now,
        ).toMap()
        // Дискриминация: со старым единым 30.0 оба веса равны (1/(1+45/30)); с per-genre:
        // Science Fiction = 1/(1+45/180) = 0.8 > Isekai = 1/(1+45/20) ≈ 0.3077
        profile.getValue("Science Fiction") shouldBeGreaterThan profile.getValue("Isekai")
    }

    @Test
    fun `expandGenreSet adds russian and english variants both ways`() {
        val fromRu = expandGenreSet(listOf("Фэнтези"))
        fromRu shouldContain "фэнтези"
        fromRu shouldContain "fantasy"

        val fromEn = expandGenreSet(listOf("Action"))
        fromEn shouldContain "action"
        fromEn shouldContain "экшен"
        fromEn shouldContain "боевик"
    }

    @Test
    fun `taste matching works across russian profile and english item genres`() {
        val profile = listOf("Фэнтези" to 3.0, "Экшен" to 2.0)
        tasteScore(listOf("Fantasy", "Action", "Comedy"), profile) shouldBe 5.0
        tasteScore(listOf("Comedy"), profile) shouldBe 0.0
        // обоснование — в spelling профиля (язык библиотеки пользователя)
        matchedGenres(listOf("Fantasy", "Comedy"), profile) shouldBe listOf("Фэнтези")
        matchedGenres(listOf("action"), profile) shouldBe listOf("Экшен")
    }

    @Test
    fun `dedupeCrossRow keeps higher priority row for stale duplicates`() {
        val items = listOf(
            dbSuggestion(DiscoveryRowType.TREND, "Solo Leveling"),
            dbSuggestion(DiscoveryRowType.LIKE, "Solo Leveling"),
            dbSuggestion(DiscoveryRowType.LIKE, "Unique Like", position = 1),
            dbSuggestion(DiscoveryRowType.SOURCE, "Unique Like"),
        )
        val out = dedupeCrossRow(items)
        out.map { it.rowType to it.title } shouldBe listOf(
            DiscoveryRowType.LIKE to "Solo Leveling",
            DiscoveryRowType.LIKE to "Unique Like",
        )
    }

    @Test
    fun `isBlacklisted filters taste rows by reason csv with translations`() {
        val taste = dbSuggestion(DiscoveryRowType.TASTE, "Item").copy(reason = "Фэнтези, Драма")
        val like = dbSuggestion(DiscoveryRowType.LIKE, "Item").copy(reason = "Фэнтези")
        val expanded = expandGenreSet(listOf("Fantasy"))
        isBlacklisted(taste, expanded) shouldBe true
        // LIKE-ряд не имеет персистентных жанров — read-time фильтр его не трогает
        isBlacklisted(like, expanded) shouldBe false
        isBlacklisted(taste, emptySet()) shouldBe false
        isBlacklisted(dbSuggestion(DiscoveryRowType.TASTE, "NoReason"), expanded) shouldBe false
    }

    @Test
    fun `rrfScores sums reciprocal ranks across rows`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to listOf(item(DiscoveryRowType.LIKE, "A"), item(DiscoveryRowType.LIKE, "B")),
            DiscoveryRowType.TREND to listOf(item(DiscoveryRowType.TREND, "A"), item(DiscoveryRowType.TREND, "C")),
        )
        val scores = rrfScores(rows)
        (scores.getValue("a") > scores.getValue("b")) shouldBe true
        scores.getValue("a") shouldBe (2.0 / 60 plusOrMinus 1e-9)
        scores.getValue("b") shouldBe (1.0 / 61 plusOrMinus 1e-9)
        scores.getValue("b") shouldBe scores.getValue("c")
    }

    @Test
    fun `fill phase uses rrf ranks instead of raw scores when provided`() {
        val like = (1..12).map { i ->
            item(DiscoveryRowType.LIKE, "L$i", if (i >= 11) 100.0 - (i - 11) else 1.0)
        }
        val trend = (1..8).map { item(DiscoveryRowType.TREND, "T$it", 0.0) }
        val rows = mapOf(DiscoveryRowType.LIKE to like, DiscoveryRowType.TREND to trend)

        // без rrf — прежнее поведение по сырым скорам (LIKE 100/99 топится выше TREND 0.0)
        interleaveMix(rows, total = 30).takeLast(4).map { it.title } shouldBe
            listOf("L11", "L12", "T7", "T8")
        // с rrf — ранг важнее шкалы: T7 (rank 6) опережает L11 (rank 10)
        interleaveMix(rows, total = 30, rrf = rrfScores(rows)).takeLast(4).map { it.title } shouldBe
            listOf("T7", "T8", "L11", "L12")
    }

    @Test
    fun `mergeNormalized spreads degenerate list to neutral and orders by normalized score`() {
        val a = listOf(
            item(DiscoveryRowType.TASTE, "A1", 0.0),
            item(DiscoveryRowType.TASTE, "A2", 2.5),
            item(DiscoveryRowType.TASTE, "A3", 5.0),
        )
        val b = listOf(item(DiscoveryRowType.TASTE, "B1", 0.6))
        // A3=1.0; A2=0.5; B1 — вырожденный список → нейтральные 0.5 (стабильно после A2); A1=0.0
        mergeNormalized(a, b).map { it.title } shouldBe listOf("A3", "A2", "B1", "A1")

        val equal = listOf(
            item(DiscoveryRowType.TASTE, "E1", 2.0),
            item(DiscoveryRowType.TASTE, "E2", 2.0),
        )
        mergeNormalized(equal, emptyList()).map { it.title } shouldBe listOf("E1", "E2")
    }

    private fun dbSuggestion(
        row: DiscoveryRowType,
        title: String,
        position: Long = 0,
    ) = tachiyomi.domain.discovery.model.DiscoverySuggestion(
        id = position,
        mediaType = tachiyomi.domain.discovery.model.DiscoveryMediaType.ANIME,
        rowType = row,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "p",
        score = 0.0,
        position = position,
        createdAt = 0L,
    )

    private fun itemWithGenres(title: String, genres: List<String>) =
        DiscoveryTrendingItem(title, title.lowercase(), null, 0L, null, genres)

    @Test
    fun `required genres filter is strict and may return empty`() {
        val items = listOf(itemWithGenres("a", listOf("harem")), itemWithGenres("b", listOf("drama")))
        applyRequiredGenres(items, setOf("romance")) { it.genres }.shouldBeEmpty()
    }

    @Test
    fun `blacklist checks genres column for any row type`() {
        val item = DiscoverySuggestion(
            id = 1L, mediaType = DiscoveryMediaType.MANGA, rowType = DiscoveryRowType.TREND,
            title = "t", cleanTitle = "t", coverUrl = null, reason = "next", seedTitle = null,
            provider = "anilist", score = 0.0, position = 0L, createdAt = 0L,
            genres = listOf("harem"),
        )
        isBlacklisted(item, expandGenreSet(listOf("гарем"))).shouldBeTrue()
    }

    @Test
    fun `blacklist falls back to taste reason for legacy rows without genres`() {
        val legacy = DiscoverySuggestion(
            id = 1L, mediaType = DiscoveryMediaType.MANGA, rowType = DiscoveryRowType.TASTE,
            title = "t", cleanTitle = "t", coverUrl = null, reason = "harem, drama", seedTitle = null,
            provider = "anilist", score = 0.0, position = 0L, createdAt = 0L,
        )
        isBlacklisted(legacy, expandGenreSet(listOf("harem"))).shouldBeTrue()
    }

    @Test
    fun `allocateSourceSlots equal 3 weights 10 slots gives 4 3 3 in input order`() {
        val slots = allocateSourceSlots(
            sourceIds = listOf(1L, 2L, 3L),
            effectiveWeights = mapOf(1L to 2.0, 2L to 2.0, 3L to 2.0),
            totalSlots = 10,
        )
        slots shouldBe mapOf(1L to 4, 2L to 3, 3L to 3)
    }

    @Test
    fun `allocateSourceSlots weights 6 2 1 with 11 slots respects ranking and minimums`() {
        val slots = allocateSourceSlots(
            sourceIds = listOf(1L, 2L, 3L),
            effectiveWeights = mapOf(1L to 6.0, 2L to 2.0, 3L to 1.0),
            totalSlots = 11,
        )
        slots.values.all { it >= 1 }.shouldBeTrue()
        slots[1L]!! shouldBeGreaterThan slots[2L]!!
        slots[2L]!! shouldBeGreaterThan slots[3L]!!
        slots shouldBe mapOf(1L to 6, 2L to 3, 3L to 2)
    }

    @Test
    fun `allocateSourceSlots handles empty or zero totalSlots`() {
        allocateSourceSlots(emptyList(), emptyMap(), 10) shouldBe emptyMap()
        allocateSourceSlots(listOf(1L, 2L), mapOf(1L to 1.0), 0) shouldBe emptyMap()
        allocateSourceSlots(listOf(1L, 2L), mapOf(1L to 1.0), -3) shouldBe emptyMap()
    }

    @Test
    fun `allocateSourceSlots normalizes super positive and non numeric weights deterministically`() {
        val slots = allocateSourceSlots(
            sourceIds = listOf(1L, 2L, 3L),
            effectiveWeights = mapOf(1L to Double.POSITIVE_INFINITY, 2L to Double.NaN, 3L to -5.0),
            totalSlots = 10,
        )
        slots.values.sum() shouldBe 10
        slots.values.all { it >= 1 }.shouldBeTrue()
        slots[1L]!! shouldBeGreaterThan slots[2L]!!
        slots[1L]!! shouldBeGreaterThan slots[3L]!!
    }

    @Test
    fun `allocateSourceSlots enforces 30 percent dominance cap when source count is at least 4`() {
        val sources = (1L..15L).toList()
        // Source 1L has huge weight, others have weight 1.0
        val weights = sources.associateWith { if (it == 1L) 1000.0 else 1.0 }
        val slots = allocateSourceSlots(
            sourceIds = sources,
            effectiveWeights = weights,
            totalSlots = 50,
            maxDominantShare = 0.30,
        )
        slots.values.sum() shouldBe 50
        // Dominant source 1L should not exceed 30% of 50 = 15 slots
        slots[1L]!! shouldBe 15
        // Other sources all get at least 1 slot
        sources.drop(1).forEach { id ->
            (slots[id]!! >= 1).shouldBeTrue()
        }
    }

    @Test
    fun `balancePluginDiversity enforces 30 percent dominance cap when 4 or more sources present`() {
        data class TestItem(val title: String, val provider: String)
        val dominantItems = (1..30).map { TestItem("Dominant $it", "src1") }
        val other1 = (1..5).map { TestItem("Other1 $it", "src2") }
        val other2 = (1..5).map { TestItem("Other2 $it", "src3") }
        val other3 = (1..5).map { TestItem("Other3 $it", "src4") }
        val allItems = dominantItems + other1 + other2 + other3

        val balanced = balancePluginDiversity(
            items = allItems,
            limit = 20,
            sourceKeyExtractor = { it.provider },
            maxDominantShare = 0.30,
        )
        balanced.size shouldBe 20
        // Dominant source src1 should not exceed 30% of 20 = 6 items
        balanced.count { it.provider == "src1" } shouldBe 6
    }
}
