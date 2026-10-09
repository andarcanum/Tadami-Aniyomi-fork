package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class TasteLearningEngineTest {

    private val now = 1_000_000_000_000L

    private fun signal(
        title: String,
        type: DiscoverySignalType,
        genres: List<String> = emptyList(),
        sourceKey: String? = null,
        createdAt: Long = now,
    ) = DiscoverySignal(
        mediaType = DiscoveryMediaType.ANIME,
        cleanTitle = title.lowercase(),
        title = title,
        signalType = type,
        genres = genres,
        provider = null,
        sourceKey = sourceKey,
        createdAt = createdAt,
    )

    // ==================== fold ====================

    @Test
    fun `positive signals accumulate genre weights`() {
        val profile = foldLearnedTasteProfile(
            listOf(
                signal("a", DiscoverySignalType.ADD, listOf("action", "comedy")),
                signal("b", DiscoverySignalType.LIKE, listOf("action")),
            ),
            nowMs = now,
        )
        val weights = profile.genres.toMap()
        (weights.getValue("action") > 1.5) shouldBe true
        (weights.getValue("comedy") > 0.5) shouldBe true
        profile.signalCount shouldBe 2
    }

    @Test
    fun `hide signal produces negative genre weight retained in learned profile`() {
        val negative = foldLearnedTasteProfile(
            listOf(signal("t2", DiscoverySignalType.HIDE, genres = listOf("harem"))),
            nowMs = now,
        )
        negative.genres.first { it.first == "harem" }.second shouldBe (-1.0)
    }

    @Test
    fun `merge lets learned negative suppress library genre`() {
        val library = listOf("harem" to 2.0, "drama" to 1.0)
        val learned = LearnedTasteProfile(genres = listOf("harem" to -0.9), signalCount = 20)
        val merged = mergeTasteProfiles(library, learned)
        merged.first { it.first == "harem" }.second shouldBe 1.1
    }

    @Test
    fun `implicit negative skips genres already in taste profile`() {
        val suggestion = DiscoverySuggestion(
            id = 1L,
            mediaType = DiscoveryMediaType.MANGA,
            rowType = DiscoveryRowType.TASTE,
            title = "t",
            cleanTitle = "t",
            coverUrl = null,
            reason = "harem",
            seedTitle = null,
            provider = "anilist",
            score = 0.0,
            position = 0L,
            createdAt = 0L,
        )
        val signals = synthesizeImplicitNegatives(
            shownWithCount = listOf(Triple("t", 0L, 5)),
            explicitSignals = emptyList(),
            currentSuggestions = listOf(suggestion),
            mediaType = DiscoveryMediaType.MANGA,
            profileGenres = listOf("harem"),
        )
        signals.shouldBeEmpty()
    }

    @Test
    fun `genre from k liked titles gets overlap multiplier`() {
        val single = foldLearnedTasteProfile(
            listOf(signal("a", DiscoverySignalType.LIKE, listOf("drama"))),
            nowMs = now,
        ).genres.toMap().getValue("drama")

        val triple = foldLearnedTasteProfile(
            listOf(
                signal("a", DiscoverySignalType.LIKE, listOf("drama")),
                signal("b", DiscoverySignalType.LIKE, listOf("drama")),
                signal("c", DiscoverySignalType.LIKE, listOf("drama")),
            ),
            nowMs = now,
        ).genres.toMap().getValue("drama")

        // k=3: буст (1 + 0.5·2) = 2x от суммы трёх — строго больше трёх одиночных? Нет:
        // 3 сигнала уже суммируются; проверяем что буст СВЕРХ суммы трёх «одиноких».
        (triple > 3 * single) shouldBe true
    }

    @Test
    fun `old signals decay against fresh ones`() {
        val old = foldLearnedTasteProfile(
            listOf(signal("old", DiscoverySignalType.ADD, listOf("drama"), createdAt = now - 90L * DAY_MS_TEST)),
            nowMs = now,
        ).genres.toMap().getValue("drama")

        val fresh = foldLearnedTasteProfile(
            listOf(signal("new", DiscoverySignalType.ADD, listOf("drama"), createdAt = now)),
            nowMs = now,
        ).genres.toMap().getValue("drama")

        (old < fresh) shouldBe true
    }

    @Test
    fun `vetoed genre is excluded from learned profile`() {
        val profile = foldLearnedTasteProfile(
            listOf(signal("a", DiscoverySignalType.LIKE, listOf("mecha", "drama"))),
            nowMs = now,
            vetoGenres = setOf("mecha"),
        )
        profile.genres.map { it.first } shouldBe listOf("drama")
    }

    @Test
    fun `genre weight is clamped`() {
        val many = (1..30).map { signal("t$it", DiscoverySignalType.ADD, listOf("isekai")) }
        val profile = foldLearnedTasteProfile(many, nowMs = now)
        profile.genres.toMap().getValue("isekai") shouldBe 3.0
    }

    @Test
    fun `source affinity sums signal weights per source`() {
        val profile = foldLearnedTasteProfile(
            listOf(
                signal("a", DiscoverySignalType.ADD, sourceKey = "pkg.x"),
                signal("b", DiscoverySignalType.LIKE, sourceKey = "pkg.x"),
                signal("c", DiscoverySignalType.HIDE, sourceKey = "pkg.y"),
            ),
            nowMs = now,
        )
        profile.sourceAffinity.getValue("pkg.x") shouldBe 1.8
        profile.sourceAffinity.getValue("pkg.y") shouldBe -1.0
    }

    // ==================== merge ====================

    @Test
    fun `implicit shown-ignored penalty softens genres without ramp-up`() {
        // Тайтл из тизера показан 3+ раз и не кликнут: жанр получает мягкий минус,
        // но в ramp-up не считается (signalCount только по явным).
        val explicit = listOf(signal("liked", DiscoverySignalType.LIKE, listOf("romance")))
        val implicit = listOf(
            signal("shownIgnored", DiscoverySignalType.SHOWN_IGNORED, listOf("romance")),
        )
        val profile = foldLearnedTasteProfile(explicit + implicit, nowMs = now)
        // romance: +0.8 (like) - 0.15 (ignored) = 0.65 — минус применён (float-окно).
        val romance = profile.genres.toMap().getValue("romance")
        (romance > 0.64 && romance < 0.66) shouldBe true
        // В ramp-up только явный сигнал (не consumed, не shown-ignored).
        profile.signalCount shouldBe 1
    }

    @Test
    fun `consumed signal is taste-neutral`() {
        val consumedOnly = foldLearnedTasteProfile(
            listOf(
                signal("a", DiscoverySignalType.CONSUMED, listOf("romance"), sourceKey = "pkg"),
                signal("b", DiscoverySignalType.CONSUMED, listOf("romance"), sourceKey = "pkg"),
            ),
            nowMs = now,
        )
        // Ни жанров, ни source-аффинити, ни вклада в ramp-up — чистое исключение.
        consumedOnly.genres shouldBe emptyList()
        consumedOnly.sourceAffinity shouldBe emptyMap()
        consumedOnly.signalCount shouldBe 0

        // Смешанный лог: consumed не разогревает blend (иначе 10 лайков + 10 consumed
        // давали бы blend 1.0 вместо честных 0.5).
        val mixed = foldLearnedTasteProfile(
            listOf(
                signal("a", DiscoverySignalType.LIKE, listOf("romance")),
                signal("b", DiscoverySignalType.CONSUMED, listOf("action")),
            ),
            nowMs = now,
        )
        mixed.signalCount shouldBe 1
        mixed.genres.any { it.first == "action" } shouldBe false
    }

    @Test
    fun `merge keeps library profile when nothing learned`() {
        val library = listOf("action" to 2.0, "drama" to 1.0)
        mergeTasteProfiles(library, LearnedTasteProfile()) shouldBe library
    }

    @Test
    fun `merge takes max not sum for double-counted genre`() {
        val library = listOf("action" to 2.0)
        val learned = LearnedTasteProfile(
            genres = listOf("action" to 3.0),
            signalCount = 100, // blend = 1
        )
        val merged = mergeTasteProfiles(library, learned).toMap()
        merged.getValue("action") shouldBe 3.0
    }

    @Test
    fun `cold start blends linearly`() {
        val library = listOf("action" to 2.0)
        val learned = LearnedTasteProfile(
            genres = listOf("comedy" to 1.0),
            signalCount = 5, // blend = 5/20 = 0.25
        )
        val merged = mergeTasteProfiles(library, learned).toMap()
        merged.getValue("comedy") shouldBe 0.25
    }

    @Test
    fun `new learned genre joins library genres`() {
        val library = listOf("action" to 1.0)
        val learned = LearnedTasteProfile(
            genres = listOf("romance" to 2.0),
            signalCount = 40,
        )
        val merged = mergeTasteProfiles(library, learned)
        merged.map { it.first }.toSet() shouldBe setOf("action", "romance")
        merged.map { it.first }.first() shouldBe "romance"
    }

    // ==================== source affinity boost ====================

    @Test
    fun `affinity boost is monotonic and clamped`() {
        val zero = applySourceAffinity(libraryWeight = 10, affinity = 0.0)
        val positive = applySourceAffinity(libraryWeight = 10, affinity = 2.0)
        val huge = applySourceAffinity(libraryWeight = 10, affinity = 1000.0)
        val negative = applySourceAffinity(libraryWeight = 10, affinity = -1000.0)
        (positive > zero) shouldBe true
        (huge <= 20.0) shouldBe true
        (negative >= 4.0) shouldBe true
    }

    @Test
    fun `affinity boost works when libraryWeight is zero`() {
        val zeroLibZeroAffinity = applySourceAffinity(libraryWeight = 0, affinity = 0.0)
        val zeroLibPositive = applySourceAffinity(libraryWeight = 0, affinity = 2.0)
        (zeroLibPositive > zeroLibZeroAffinity) shouldBe true
        (zeroLibZeroAffinity >= 1.0) shouldBe true
    }

    private companion object {
        const val DAY_MS_TEST = 24L * 60 * 60 * 1000
    }
}
