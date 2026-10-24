package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository

/**
 * Симуляция работы Discovery с РЕАЛЬНЫМ плагином TomiloLib (D:\lnreader\extensions-source\src\ru\tomilolib).
 * Проверяет:
 * 1. Распознавание жанровых фильтров плагина через AppDiscoverySourceCatalog (с учётом RU/EN алиасов).
 * 2. Обязательный тег "Система": тайтлы корректно отображаются, выборка не пустая.
 * 3. Исключаемые теги (ignoredGenres): строгий отсев (например, "Ужасы").
 * 4. Предпочтительные теги (priorityGenres): бустинг и поднятие наверх (например, "Культивация").
 * 5. Комбинации: Обязательный + Предпочтительный + Исключаемый (приоритет игнора над бустом).
 * 6. Режимы Strict vs Best-Effort при отсутствии совпадений.
 * 7. Поведение Configuration Refresh в DiscoveryRunner (страница 1, без исключения текущего окна).
 */
class TomiloLibDiscoverySimulationTest {

    private val media = DiscoveryMediaType.MANGA
    private val catalog = AppDiscoverySourceCatalog()

    // ── 1:1 Фильтры из TomiloLib (Filters.kt) ───────────────────────────────
    private class TomiloGenreCheckBox(name: String) : Filter.CheckBox(name)
    private class TomiloGenreFilter(genres: List<String>) :
        Filter.Group<TomiloGenreCheckBox>(
            "Жанры (могут не комбинироваться с текстовым поиском)",
            genres.map {
                TomiloGenreCheckBox(it)
            },
        )

    // Полный список ключевых жанров из каталога TomiloLib (Filters.kt)
    private val tomiloGenres = listOf(
        "Боевик", "Боевые искусства", "Выживание", "Драма", "Исекай", "Культивация",
        "Мистика", "Перерождение", "Приключения", "Сверхъестественное", "Сёнэн",
        "Система", "Триллер", "Ужасы", "Фэнтези", "Экшен", "Этти",
    )

    private fun createTomiloFilters() = FilterList(
        Filter.Header("Жанры"),
        TomiloGenreFilter(tomiloGenres),
    )

    // ── Реальные тайтлы из API https://tomilo-lib.ru/api/titles ─────────────
    // 1. Тайтл с "Система" и "Фэнтези"
    private val evolutionWorlds = DiscoveryRowItem(
        title = "Эволюция: Слияние миров",
        cleanTitle = "эволюция слияние миров",
        coverUrl = "https://cdn.tomilo-lib.ru/titles/cover1.jpeg",
        reason = null,
        seedTitle = null,
        provider = "Tomilo-lib",
        score = 1.0,
        sourceId = 100L,
        sourceUrl = "/titles/6accb46a43f5ccf698890a98",
        genres = listOf("Монстр", "Выживание", "Артефакты", "Ранги силы", "Система", "Фэнтези", "Сёнэн"),
    )

    // 2. Тайтл с "Система", "Культивация" и "этти"
    private val autoCultivation = DiscoveryRowItem(
        title = "Интеллектуальная система культивации",
        cleanTitle = "интеллектуальная система культивации",
        coverUrl = "https://cdn.tomilo-lib.ru/titles/cover2.jpeg",
        reason = null,
        seedTitle = null,
        provider = "Tomilo-lib",
        score = 1.0,
        sourceId = 100L,
        sourceUrl = "/titles/6abd59d0bfe9f769c393499a",
        genres = listOf("Культивация", "Выживание", "Игровые элементы", "Система", "Фэнтези", "этти"),
    )

    // 3. Тайтл с "Система" и "Ужасы"
    private val netherworldTrillions = DiscoveryRowItem(
        title = "Я попал в мир ужасов с системой богатства",
        cleanTitle = "я попал в мир ужасов с системой богатства",
        coverUrl = "https://cdn.tomilo-lib.ru/titles/cover3.jpeg",
        reason = null,
        seedTitle = null,
        provider = "Tomilo-lib",
        score = 1.0,
        sourceId = 100L,
        sourceUrl = "/titles/694afecf37431a042d527fb2",
        genres = listOf("Боевые искусства", "Мистика", "Ужасы", "Триллер", "Фэнтези", "Система"),
    )

    // 4. Популярный тайтл БЕЗ "Система" (из топа TomiloLib)
    private val treeEvolution = DiscoveryRowItem(
        title = "Моё перерождение в древо: Начало эволюции",
        cleanTitle = "мое перерождение в древо начало эволюции",
        coverUrl = "https://cdn.tomilo-lib.ru/titles/cover4.jpeg",
        reason = null,
        seedTitle = null,
        provider = "Tomilo-lib",
        score = 1.0,
        sourceId = 100L,
        sourceUrl = "/titles/69483be137431a042d492671",
        genres = listOf("Боевик", "драма", "Исекай", "приключения", "Фэнтези", "Экшен"),
    )

    private val allCandidates = listOf(
        evolutionWorlds,
        autoCultivation,
        netherworldTrillions,
        treeEvolution,
    )

    @Test
    fun `withMangaGenreFilters identifies TomiloLib genre group and enables checkbox for Система`() = runTest {
        val filters = createTomiloFilters()
        val applied = catalog.withMangaGenreFilters(filters, listOf("Система"))

        applied shouldBe filters
        val group = filters.filterIsInstance<Filter.Group<*>>().first()
        val boxes = group.state.filterIsInstance<Filter.CheckBox>()
        val systemBox = boxes.first { it.name == "Система" }
        systemBox.state shouldBe true

        // Все остальные чекбоксы должны оставаться выключенными
        boxes.filter { it.name != "Система" }.all { !it.state } shouldBe true
    }

    @Test
    fun `withMangaGenreFilters matches English system alias to Russian Система`() = runTest {
        val filters = createTomiloFilters()
        // Пользователь или внешний провайдер передал 'system' на английском
        val applied = catalog.withMangaGenreFilters(filters, listOf("system"))

        applied shouldBe filters
        val group = filters.filterIsInstance<Filter.Group<*>>().first()
        val boxes = group.state.filterIsInstance<Filter.CheckBox>()
        boxes.first { it.name == "Система" }.state shouldBe true
    }

    @Test
    fun `Required genre Система admits titles with Система and strictly rejects title without it`() {
        val policy = DiscoveryCandidatePolicy(
            requiredGenres = setOf("Система"),
            strictRequired = true,
        )

        // Тайтлы с "Система" принимаются
        policy.evaluate(evolutionWorlds.genres).shouldBeInstanceOf<CandidateVerdict.Admitted>()
        // Тайтл без "Система" строго отклоняется
        val rejectedVerdict = policy.evaluate(treeEvolution.genres)
        rejectedVerdict.shouldBeInstanceOf<CandidateVerdict.Rejected>()
        rejectedVerdict.reason.shouldBeInstanceOf<RejectionReason.MissingRequiredGenre>()

        // Выборка ряда: не пустая, содержит все тайтлы с "Система"
        val filtered = policy.filterRowCandidates(allCandidates, { it.genres })
        filtered.map { it.title } shouldBe listOf(
            evolutionWorlds.title,
            autoCultivation.title,
            netherworldTrillions.title,
        )
        filtered shouldNotContain treeEvolution
    }

    @Test
    fun `Required Система plus Ignored Ужасы strictly excludes horror titles`() {
        val policy = DiscoveryCandidatePolicy(
            requiredGenres = setOf("Система"),
            ignoredGenres = setOf("Ужасы"),
            strictRequired = true,
        )

        val filtered = policy.filterRowCandidates(allCandidates, { it.genres })

        // "Я попал в мир ужасов с системой богатства" содержит "Ужасы" -> ИСКЛЮЧЁН
        filtered.map { it.title } shouldBe listOf(
            evolutionWorlds.title,
            autoCultivation.title,
        )
        filtered shouldNotContain netherworldTrillions
        filtered shouldNotContain treeEvolution
    }

    @Test
    fun `Priority genre Культивация boosts title ranking to the top of the feed`() {
        val policy = DiscoveryCandidatePolicy(
            requiredGenres = setOf("Система"),
            priorityGenres = setOf("Культивация"),
        )

        val filtered = policy.filterRowCandidates(allCandidates, { it.genres })

        // Тайтл с "Культивация" ("Интеллектуальная система культивации") поднимается на 1-е место!
        filtered.first().title shouldBe autoCultivation.title
        filtered.map { it.title } shouldContain evolutionWorlds.title
    }

    @Test
    fun `Combined Required Система, Priority Культивация, and Ignored этти prioritizes exclusion over boost`() {
        // У пользователя:
        // - Обязательно: "Система"
        // - Предпочитает: "Культивация"
        // - Исключает: "этти" и "Ужасы"
        val policy = DiscoveryCandidatePolicy(
            requiredGenres = setOf("Система"),
            priorityGenres = setOf("Культивация"),
            ignoredGenres = setOf("этти", "Ужасы"),
            strictRequired = true,
        )

        val filtered = policy.filterRowCandidates(allCandidates, { it.genres })

        // autoCultivation имеет "Культивация", но содержит "этти" -> ДОЛЖЕН БЫТЬ ОТКЛОНЁН (строгий игнор)
        filtered shouldNotContain autoCultivation
        // netherworldTrillions содержит "Ужасы" -> ДОЛЖЕН БЫТЬ ОТКЛОНЁН
        filtered shouldNotContain netherworldTrillions
        // treeEvolution не имеет "Система" -> ОТКЛОНЁН
        filtered shouldNotContain treeEvolution

        // Остаётся только чистый подходящий тайтл
        filtered.map { it.title } shouldBe listOf(evolutionWorlds.title)
    }

    @Test
    fun `Strict vs Best-effort fallback when required genre has zero matches`() {
        val missingGenre = "НесуществующийЖанр"

        // 1. Строгий режим (Strict): ничего не подошло -> пустая выдача
        val strictPolicy = DiscoveryCandidatePolicy(
            requiredGenres = setOf(missingGenre),
            ignoredGenres = setOf("Ужасы"),
            strictRequired = true,
        )
        val strictFiltered = strictPolicy.filterRowCandidates(allCandidates, { it.genres })
        strictFiltered shouldBe emptyList()

        // 2. Мягкий режим (Best-effort): требование ослабляется, но игнорируемые "Ужасы" ВСЁ РАВНО исключаются
        val bestEffortPolicy = DiscoveryCandidatePolicy(
            requiredGenres = setOf(missingGenre),
            ignoredGenres = setOf("Ужасы"),
            strictRequired = false,
        )
        val bestEffortFiltered = bestEffortPolicy.filterRowCandidates(allCandidates, { it.genres })
        // Должен вернуть тайтлы БЕЗ "Ужасы", чтобы лента не пустовала
        bestEffortFiltered.map { it.title } shouldBe listOf(
            evolutionWorlds.title,
            autoCultivation.title,
            treeEvolution.title,
        )
        bestEffortFiltered shouldNotContain netherworldTrillions
    }

    @Test
    fun `Configuration refresh preserves existing cached titles and starts from page one`() = runTest {
        val preferences = DiscoveryPreferences(PersistingPreferenceStore())
        preferences.rowSourceEnabled().set(true)
        preferences.rowLikeEnabled().set(false)
        preferences.rowTasteEnabled().set(false)
        preferences.rowTrendEnabled().set(false)
        preferences.requiredGenres().set("система")

        val stored = MutableStateFlow<List<DiscoverySuggestion>>(
            listOf(
                DiscoverySuggestion(
                    id = 1L,
                    mediaType = media,
                    rowType = DiscoveryRowType.SOURCE,
                    title = evolutionWorlds.title,
                    cleanTitle = evolutionWorlds.cleanTitle,
                    coverUrl = evolutionWorlds.coverUrl,
                    reason = null,
                    seedTitle = null,
                    provider = "Tomilo-lib",
                    score = 1.0,
                    position = 0L,
                    createdAt = 1L,
                    sourceId = 100L,
                    sourceUrl = evolutionWorlds.sourceUrl,
                    genres = listOf("Система", "Фэнтези"),
                ),
            ),
        )

        val repository = mockk<DiscoveryRepository>(relaxed = true)
        every { repository.subscribe(media) } returns stored
        coEvery { repository.getSignals(media) } returns emptyList()
        coEvery { repository.getHiddenTitles(media) } returns emptySet()
        coEvery { repository.getBlacklistedTags(media) } returns emptySet()
        coEvery { repository.getRecentExclusionsWithTimestamp(media, any()) } returns emptyMap()
        coEvery { repository.getShownWithCount(media, any()) } returns emptyList()
        coEvery { repository.replaceRows(any(), any(), any()) } coAnswers {
            val items = thirdArg<List<DiscoverySuggestion>>()
            stored.value = items
        }

        val capturedContexts = mutableListOf<DiscoveryBuildContext>()
        val tomiloRowBuilder = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.SOURCE
            override val isDegraded = false
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                capturedContexts += context
                return context.candidatePolicy.filterRowCandidates(allCandidates, { it.genres })
            }
        }

        val runner = DiscoveryRunner(
            repository = repository,
            preferences = preferences,
            seedSources = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            },
            coordinatorFactory = { DiscoveryCoordinator(listOf(tomiloRowBuilder)) },
            trendingFactory = { mockk(relaxed = true) },
            sourcePreferencesProvider = { SourcePreferences(PersistingPreferenceStore()) },
            installedPluginsProvider = { emptyList() },
            fallbackSourceIdProvider = { -1L },
        )

        val outcome = runner.run(listOf(media), isConfigurationRefresh = true).getValue(media)

        outcome.status shouldBe DiscoveryRunStatus.SUCCESS
        outcome.persistedRows shouldBe setOf(DiscoveryRowType.SOURCE)

        // Контекст запроса: страница 1, старое окно не исключается
        capturedContexts.single().pageOffset shouldBe 1
        capturedContexts.single().currentFeedCleanTitles shouldBe emptySet()

        // В базе сохранены все подходящие тайтлы с "Система"
        stored.value.map { it.title } shouldContain evolutionWorlds.title
        stored.value.map { it.title } shouldContain autoCultivation.title
        stored.value.map { it.title } shouldContain netherworldTrillions.title
        stored.value.map { it.title } shouldNotContain treeEvolution.title
    }
}
