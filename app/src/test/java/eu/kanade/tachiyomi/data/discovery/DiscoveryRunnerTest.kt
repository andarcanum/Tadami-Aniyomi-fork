package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionFetchResult
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import java.io.IOException

class DiscoveryRunnerTest {

    @Test
    fun `runner cancellation propagates and clears only owned media progress`() = runTest {
        val cancelled = kotlinx.coroutines.CancellationException("cancelled")
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput> =
                    throw cancelled
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            },
        )
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.ANIME, stageText = "Still running")
        try {
            runCatching { runner.run(listOf(DiscoveryMediaType.NOVEL)) }.exceptionOrNull() shouldBe cancelled
            DiscoveryProgressTracker.progress(DiscoveryMediaType.NOVEL).value.isRunning shouldBe false
            DiscoveryProgressTracker.progress(DiscoveryMediaType.ANIME).value.stageText shouldBe "Still running"
        } finally {
            DiscoveryProgressTracker.reset()
        }
    }

    @Test
    fun `unexpected media failure is returned while other media can finish`() = runTest {
        val failure = IllegalStateException("seed storage failed")
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput> {
                    if (mediaType == DiscoveryMediaType.NOVEL) throw failure
                    return listOf(DiscoverySeedInput(entryId = 1, title = "Seed One", isCompleted = true))
                }
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            },
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = false)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )

        val outcomes = runner.run(listOf(DiscoveryMediaType.NOVEL, DiscoveryMediaType.ANIME))
        outcomes[DiscoveryMediaType.NOVEL]?.status shouldBe DiscoveryRunStatus.FAILED
        outcomes[DiscoveryMediaType.NOVEL]?.cause shouldBe failure
        outcomes[DiscoveryMediaType.ANIME]?.status shouldBe DiscoveryRunStatus.PARTIAL
        outcomes[DiscoveryMediaType.ANIME]?.persistedRows shouldBe setOf(DiscoveryRowType.LIKE)
    }

    private open class FakeRepository(
        val initialSignals: List<DiscoverySignal> = emptyList(),
        initialSuggestions: List<DiscoverySuggestion> = emptyList(),
    ) : DiscoveryRepository {
        val replaced = mutableListOf<Triple<DiscoveryMediaType, DiscoveryRowType, List<DiscoverySuggestion>>>()
        val flow = MutableStateFlow(initialSuggestions)
        override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> = flow
        override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun replaceRows(
            mediaType: DiscoveryMediaType,
            rowType: DiscoveryRowType,
            items: List<DiscoverySuggestion>,
        ) {
            replaced += Triple(mediaType, rowType, items)
            flow.value = flow.value.filterNot { it.mediaType == mediaType && it.rowType == rowType } + items
        }

        override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> = setOf("hidden one")
        override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) {}
        override suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String) {}
        override suspend fun clearHidden(mediaType: DiscoveryMediaType) {}
        override suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long? = null
        override fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String> = setOf("harem")
        override suspend fun blacklistTag(mediaType: DiscoveryMediaType, tag: String) {}
        override suspend fun unblacklistTag(mediaType: DiscoveryMediaType, tag: String) {}
        override suspend fun clearBlacklist(mediaType: DiscoveryMediaType) {}
        override suspend fun getHiddenEntries(
            mediaType: DiscoveryMediaType,
        ): List<tachiyomi.domain.discovery.model.DiscoveryHiddenEntry> = emptyList()

        override suspend fun getBlacklistEntries(
            mediaType: DiscoveryMediaType,
        ): List<tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry> = emptyList()

        override suspend fun restoreHiddenEntries(
            mediaType: DiscoveryMediaType,
            entries: List<tachiyomi.domain.discovery.model.DiscoveryHiddenEntry>,
        ) {}

        override suspend fun restoreBlacklistEntries(
            mediaType: DiscoveryMediaType,
            entries: List<tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry>,
        ) {}

        val markedShown = mutableListOf<Pair<DiscoveryMediaType, Collection<String>>>()
        override suspend fun getShownTitles(mediaType: DiscoveryMediaType, windowMillis: Long): Set<String> = emptySet()
        override suspend fun getShownTitlesWithTimestamp(
            mediaType: DiscoveryMediaType,
            windowMillis: Long,
        ): Map<String, Long> = emptyMap()

        // Подменяемое окно показов со счётчиком (для неявного негатива).
        var shownWithCount: List<Triple<String, Long, Int>> = emptyList()
        override suspend fun getShownWithCount(
            mediaType: DiscoveryMediaType,
            windowMillis: Long,
        ): List<Triple<String, Long, Int>> = shownWithCount

        override suspend fun recordExposure(
            mediaType: DiscoveryMediaType,
            cleanTitles: Collection<String>,
            timestamp: Long,
        ) {
            markedShown += mediaType to cleanTitles
        }

        override suspend fun suppressRecentlyConsumed(
            mediaType: DiscoveryMediaType,
            cleanTitles: Collection<String>,
            timestamp: Long,
        ) = Unit

        override suspend fun getRecentExclusions(
            mediaType: DiscoveryMediaType,
            windowMillis: Long,
        ): Set<String> = emptySet()

        override suspend fun getRecentExclusionsWithTimestamp(
            mediaType: DiscoveryMediaType,
            windowMillis: Long,
        ): Map<String, Long> = emptyMap()

        override suspend fun markShown(
            mediaType: DiscoveryMediaType,
            cleanTitles: Collection<String>,
            timestamp: Long,
        ) {
            markedShown += mediaType to cleanTitles
        }
        override suspend fun clearShown(mediaType: DiscoveryMediaType) {
            markedShown.clear()
        }
        override suspend fun hasUnboundSourceRows(): Boolean = false

        val recordedSignals = mutableListOf<Triple<DiscoveryMediaType, String, DiscoverySignalType>>()
        var signals: List<DiscoverySignal> = initialSignals
        override suspend fun getSignals(mediaType: DiscoveryMediaType): List<DiscoverySignal> = signals
        override fun subscribeConsumed(mediaType: DiscoveryMediaType): kotlinx.coroutines.flow.Flow<Set<String>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptySet())
        override suspend fun recordSignal(
            mediaType: DiscoveryMediaType,
            cleanTitle: String,
            title: String,
            signalType: DiscoverySignalType,
            genres: List<String>,
            provider: String?,
            sourceKey: String?,
            timestamp: Long,
        ) {
            recordedSignals += Triple(mediaType, cleanTitle, signalType)
        }
        override suspend fun enrichSignalMetadataIfCurrent(
            mediaType: DiscoveryMediaType,
            cleanTitle: String,
            expectedCreatedAt: Long,
            genres: List<String>,
            provider: String?,
            sourceKey: String?,
        ): Boolean = false
        override suspend fun removeSignal(mediaType: DiscoveryMediaType, cleanTitle: String) {}
        override suspend fun clearSignals(mediaType: DiscoveryMediaType) {}
        override suspend fun clearAllSignals() {}
        override suspend fun restoreSignals(signals: List<DiscoverySignal>) {}
    }

    private class FakeSeedSources : DiscoverySeedSources {
        override suspend fun candidates(mediaType: DiscoveryMediaType) = listOf(
            DiscoverySeedInput(
                entryId = 1,
                title = "Seed One",
                genres = listOf("Drama"),
                isCompleted = true,
                completedAt = 1L,
                lastInteraction = 1L,
            ),
            // Присутствует в библиотеке → одноимённый совет обязан отфильтроваться.
            DiscoverySeedInput(
                entryId = 2,
                title = "In Library",
                genres = listOf("Drama"),
                isCompleted = true,
                completedAt = 1L,
                lastInteraction = 1L,
            ),
        )

        override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = setOf("in history")
    }

    private fun testSourcePrefs() = SourcePreferences(InMemoryPreferenceStore())

    private fun fakeBuilders(failLike: Boolean) = listOf(
        object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.LIKE
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                if (failLike) throw IOException("boom")
                return listOf(
                    DiscoveryRowItem("Fresh Pick", "fresh pick", null, null, "Seed One", "anilist", 1.0),
                    DiscoveryRowItem("In Library", "in library", null, null, "Seed One", "anilist", 0.5),
                    DiscoveryRowItem("Hidden One", "hidden one", null, null, "Seed One", "anilist", 0.4),
                )
            }
        },
    )

    @Test
    fun `runner persists successful rows and filters library and hidden`() = runTest {
        val repo = FakeRepository()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = false)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        val (media, row, items) = repo.replaced.first { it.second == DiscoveryRowType.LIKE }
        media shouldBe DiscoveryMediaType.NOVEL
        row shouldBe DiscoveryRowType.LIKE
        items.map { it.title } shouldBe listOf("Fresh Pick")
        items.single().seedTitle shouldBe "Seed One"
    }

    @Test
    fun `learned signals merge into builder taste profile`() = runTest {
        val repo = FakeRepository()
        // 30 свежих ADD-сигналов по «romance» — выученный профиль обязан попасть
        // в контекст строителя поверх библиотечного (FakeSeedSources: Drama).
        repo.signals = (1..30).map {
            DiscoverySignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "liked $it",
                title = "Liked $it",
                signalType = DiscoverySignalType.ADD,
                genres = listOf("romance"),
                provider = null,
                sourceKey = null,
                createdAt = System.currentTimeMillis(),
            )
        }
        val seenProfiles = mutableListOf<List<Pair<String, Double>>>()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = {
                DiscoveryCoordinator(
                    listOf(
                        object : DiscoveryRowBuilder {
                            override val rowType = DiscoveryRowType.LIKE
                            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                                seenProfiles += context.tasteProfile
                                return emptyList()
                            }
                        },
                    ),
                )
            },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        val profile = seenProfiles.single()
        // Выученный жанр (romance, 30 ADD-сигналов) обязан попасть в профиль строителя.
        // Библиотечный вклад FakeSeedSources пуст (lastInteraction=1 вне 90-дневного окна)
        // — это и есть деградированный кейс, где taste держится только на сигналах.
        profile.map { it.first }.contains("romance") shouldBe true
        profile.filter { it.first == "romance" }.single().second shouldBe 3.0
    }

    @Test
    fun `consumed signal excludes title from generation without touching taste`() = runTest {
        val repo = FakeRepository()
        repo.signals = listOf(
            DiscoverySignal(
                mediaType = DiscoveryMediaType.NOVEL,
                cleanTitle = "fresh pick",
                title = "Fresh Pick",
                signalType = DiscoverySignalType.CONSUMED,
                genres = listOf("romance"),
                provider = null,
                sourceKey = "com.example.plugin",
                createdAt = System.currentTimeMillis(),
            ),
        )
        val seenHidden = mutableListOf<Set<String>>()
        val seenProfiles = mutableListOf<List<Pair<String, Double>>>()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = {
                DiscoveryCoordinator(
                    listOf(
                        object : DiscoveryRowBuilder {
                            override val rowType = DiscoveryRowType.LIKE
                            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                                seenHidden += context.hiddenCleanTitles
                                seenProfiles += context.tasteProfile
                                return emptyList()
                            }
                        },
                    ),
                )
            },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        // «Просмотрено»: тайтл попал в excluded-сет (как hidden), но профиль вкуса
        // остался пустым — consumed не должен влиять на вкус.
        seenHidden.single().contains("fresh pick") shouldBe true
        seenProfiles.single() shouldBe emptyList()
    }

    @Test
    fun `failing row is not persisted (cache preserved)`() = runTest {
        val repo = FakeRepository()
        val failedRows = mutableListOf<Set<DiscoveryRowType>>()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = true)) },
            sourcePreferencesProvider = ::testSourcePrefs,
            failedRowsSink = { _, rows -> failedRows += rows },
        )
        val outcomes = runner.run(listOf(DiscoveryMediaType.NOVEL))
        outcomes[DiscoveryMediaType.NOVEL]?.status shouldBe DiscoveryRunStatus.FAILED
        outcomes[DiscoveryMediaType.NOVEL]?.failedRows shouldBe setOf(DiscoveryRowType.LIKE)
        repo.replaced.none { it.second == DiscoveryRowType.LIKE } shouldBe true
        failedRows.single() shouldBe setOf(DiscoveryRowType.LIKE)
    }

    @Test
    fun `partial row failure produces PARTIAL status and preserves successful rows`() = runTest {
        val repo = FakeRepository()
        val failedRows = mutableListOf<Set<DiscoveryRowType>>()
        val tasteBuilder = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.TASTE
            override suspend fun build(context: DiscoveryBuildContext) = listOf(
                DiscoveryRowItem("Taste Pick", "taste pick", null, null, null, "catalog", 0.9),
            )
        }
        val mixedBuilders = fakeBuilders(failLike = true) + tasteBuilder
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(mixedBuilders) },
            sourcePreferencesProvider = ::testSourcePrefs,
            failedRowsSink = { _, rows -> failedRows += rows },
        )
        val outcomes = runner.run(listOf(DiscoveryMediaType.NOVEL))
        val outcome = outcomes[DiscoveryMediaType.NOVEL]!!
        outcome.status shouldBe DiscoveryRunStatus.PARTIAL
        outcome.failedRows shouldBe setOf(DiscoveryRowType.LIKE)
        outcome.persistedRows shouldBe setOf(DiscoveryRowType.TASTE)
        repo.replaced.any { it.second == DiscoveryRowType.TASTE } shouldBe true
        repo.replaced.none { it.second == DiscoveryRowType.LIKE } shouldBe true
    }

    @Test
    fun `disabled discovery returns SKIPPED status`() = runTest {
        val prefs = discoveryPrefs(
            InMemoryPreferenceStore.InMemoryPreference("discovery_enabled", false, true),
        )
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = prefs,
            seedSources = FakeSeedSources(),
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        val outcomes = runner.run(listOf(DiscoveryMediaType.NOVEL))
        outcomes[DiscoveryMediaType.NOVEL]?.status shouldBe DiscoveryRunStatus.SKIPPED
    }

    @Test
    fun `empty successful row does not wipe cache when seeds exist`() = runTest {
        val repo = FakeRepository()
        val seedSourcesWithActiveSeeds = object : DiscoverySeedSources {
            override suspend fun candidates(mediaType: DiscoveryMediaType) = listOf(
                DiscoverySeedInput(
                    entryId = 1,
                    title = "Seed One",
                    genres = listOf("Drama"),
                    isCompleted = true,
                    completedAt = System.currentTimeMillis(),
                    lastInteraction = System.currentTimeMillis(),
                ),
            )
            override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
        }
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = seedSourcesWithActiveSeeds,
            coordinatorFactory = {
                DiscoveryCoordinator(
                    listOf(
                        object : DiscoveryRowBuilder {
                            override val rowType = DiscoveryRowType.LIKE
                            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> =
                                emptyList()
                        },
                    ),
                )
            },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        repo.replaced.none { it.second == DiscoveryRowType.LIKE } shouldBe true
    }

    @Test
    fun `disabled discovery performs no work`() = runTest {
        val repo = FakeRepository()
        // InMemoryPreferenceStore не сохраняет set() после выдачи Preference —
        // значение задаётся через initialPreferences конструктора.
        val prefs = DiscoveryPreferences(
            InMemoryPreferenceStore(
                sequenceOf(InMemoryPreferenceStore.InMemoryPreference("discovery_enabled", false, true)),
            ),
        )
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = prefs,
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = false)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        repo.replaced shouldBe emptyList()
    }

    @Test
    fun `runner ranks library source ids into context`() = runTest {
        val captured = mutableListOf<DiscoveryBuildContext>()
        val seedSources = object : DiscoverySeedSources {
            override suspend fun candidates(mediaType: DiscoveryMediaType) = listOf(
                DiscoverySeedInput(entryId = 1, title = "A", sourceId = 11L),
                DiscoverySeedInput(entryId = 2, title = "B", sourceId = 11L),
                DiscoverySeedInput(entryId = 3, title = "C", sourceId = 22L),
            )

            override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
        }
        val capturingBuilder = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.LIKE
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                captured += context
                return emptyList()
            }
        }
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = seedSources,
            coordinatorFactory = { DiscoveryCoordinator(listOf(capturingBuilder)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        captured.single().sourceIds shouldBe listOf(11L, 22L)
        captured.single().blacklistedTags shouldBe setOf("harem")
    }

    @Test
    fun `failed rows are reported through sink and cleared on success`() = runTest {
        val captured = mutableListOf<Pair<DiscoveryMediaType, Set<DiscoveryRowType>>>()
        val failingRunner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = true)) },
            sourcePreferencesProvider = ::testSourcePrefs,
            failedRowsSink = { media, rows -> captured += media to rows },
        )
        failingRunner.run(listOf(DiscoveryMediaType.NOVEL))
        captured.single() shouldBe (DiscoveryMediaType.NOVEL to setOf(DiscoveryRowType.LIKE))

        captured.clear()
        val okRunner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = false)) },
            sourcePreferencesProvider = ::testSourcePrefs,
            failedRowsSink = { media, rows -> captured += media to rows },
        )
        okRunner.run(listOf(DiscoveryMediaType.NOVEL))
        captured.single() shouldBe (DiscoveryMediaType.NOVEL to emptySet())
    }

    @Test
    fun `disabled rows are cleared in repository and builders omitted`() = runTest {
        val repo = FakeRepository()
        val prefs = DiscoveryPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference("discovery_row_like", false, true),
                    InMemoryPreferenceStore.InMemoryPreference("discovery_row_taste", false, true),
                    InMemoryPreferenceStore.InMemoryPreference("discovery_row_trend", false, true),
                    InMemoryPreferenceStore.InMemoryPreference("discovery_row_source", false, true),
                ),
            ),
        )
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = prefs,
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(emptyList()) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        repo.replaced.map { it.second } shouldBe listOf(
            DiscoveryRowType.LIKE,
            DiscoveryRowType.TASTE,
            DiscoveryRowType.TREND,
            DiscoveryRowType.SOURCE,
        )
    }

    @Test
    fun `background page offset cycles one to ten without duplicates`() {
        backgroundPageOffset(1) shouldBe 1
        backgroundPageOffset(2) shouldBe 2
        backgroundPageOffset(10) shouldBe 10
        backgroundPageOffset(11) shouldBe 1
        backgroundPageOffset(20) shouldBe 10
    }

    @Test
    fun `external providers off skips trend builder, enables local like, and clears trend cache`() = runTest {
        val repo = FakeRepository()
        val prefs = DiscoveryPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference("discovery_external_providers", false, true),
                ),
            ),
        )
        val seenBuilderTypes = mutableListOf<DiscoveryRowType>()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = prefs,
            seedSources = FakeSeedSources(),
            coordinatorFactory = { builders ->
                seenBuilderTypes += builders.map { it.rowType }
                DiscoveryCoordinator(emptyList())
            },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        // Чисто внешний ряд TREND не строится — билдер не создаётся; LIKE строится локально из каталогов.
        seenBuilderTypes.filter { it == DiscoveryRowType.TREND } shouldBe emptyList()
        seenBuilderTypes.filter { it == DiscoveryRowType.LIKE } shouldBe listOf(DiscoveryRowType.LIKE)
        // Кэш чисто внешнего ряда TREND очищен (как при выключенных рядах), TASTE и LIKE сохранены/обновлены.
        repo.replaced.map { it.second }.toSet() shouldBe setOf(
            DiscoveryRowType.LIKE,
            DiscoveryRowType.TASTE,
            DiscoveryRowType.TREND,
        )
        repo.replaced.all { it.third.isEmpty() } shouldBe true
    }

    @Test
    fun `global external opt out prunes only external like cache when local replacement is empty`() = runTest {
        val repo = FakeRepository(initialSuggestions = cachedProviderSuggestions())
        val runner = cachePolicyRunner(
            repo,
            preferences = discoveryPrefs(
                InMemoryPreferenceStore.InMemoryPreference("discovery_external_providers", false, true),
            ),
        )

        runner.run(listOf(DiscoveryMediaType.NOVEL))

        repo.flow.value.filter { it.rowType == DiscoveryRowType.LIKE }.map { it.provider } shouldBe
            listOf("Local Plugin", "AniList Plugin")
        repo.flow.value.filter { it.rowType == DiscoveryRowType.SOURCE }.map { it.provider } shouldBe
            listOf("Local Plugin")
    }

    @Test
    fun `shikimori similar opt out prunes only its like cache and preserves trend on row failure`() = runTest {
        val repo = FakeRepository(initialSuggestions = cachedProviderSuggestions())
        val runner = cachePolicyRunner(
            repo,
            sourcePreferences = cachePolicySourcePrefs("suggestions_use_shikimori"),
            failLike = true,
        )

        runner.run(listOf(DiscoveryMediaType.NOVEL))

        repo.flow.value.filter { it.rowType == DiscoveryRowType.LIKE }.map { it.provider } shouldBe
            listOf("AniList", "MyAnimeList", "MangaUpdates", "NovelUpdates", "Local Plugin", "AniList Plugin")
        repo.flow.value.filter { it.rowType == DiscoveryRowType.TREND }.map { it.provider } shouldBe
            listOf("Shikimori")
        repo.replaced.none { it.second == DiscoveryRowType.TREND || it.second == DiscoveryRowType.SOURCE } shouldBe true
    }

    @Test
    fun `manga updates opt out prunes only its like cache`() = runTest {
        val repo = FakeRepository(initialSuggestions = cachedProviderSuggestions())
        val runner = cachePolicyRunner(
            repo,
            sourcePreferences = cachePolicySourcePrefs("suggestions_use_mangaupdates_novel"),
        )

        runner.run(listOf(DiscoveryMediaType.NOVEL))

        repo.flow.value.filter { it.rowType == DiscoveryRowType.LIKE }.map { it.provider } shouldBe
            listOf("AniList", "Shikimori", "MyAnimeList", "NovelUpdates", "Local Plugin", "AniList Plugin")
    }

    @Test
    fun `novel updates opt out prunes only its like cache`() = runTest {
        val repo = FakeRepository(initialSuggestions = cachedProviderSuggestions())
        val runner = cachePolicyRunner(
            repo,
            sourcePreferences = cachePolicySourcePrefs("suggestions_use_novelupdates"),
        )

        runner.run(listOf(DiscoveryMediaType.NOVEL))

        repo.flow.value.filter { it.rowType == DiscoveryRowType.LIKE }.map { it.provider } shouldBe
            listOf("AniList", "Shikimori", "MyAnimeList", "MangaUpdates", "Local Plugin", "AniList Plugin")
    }

    @Test
    fun `enabled providers keep like cache on row failure`() = runTest {
        val cached = cachedProviderSuggestions()
        val repo = FakeRepository(initialSuggestions = cached)
        val runner = cachePolicyRunner(repo, failLike = true)

        runner.run(listOf(DiscoveryMediaType.NOVEL))

        repo.flow.value shouldBe cached
        repo.replaced.none { it.second == DiscoveryRowType.LIKE } shouldBe true
    }

    @Test
    fun `partial provider failure yields fresh recommendations instead of discarding them`() = runTest {
        val repo = FakeRepository(initialSuggestions = cachedProviderSuggestions())
        val suggestions = mockk<SuggestionCoordinator>()
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns SuggestionFetchResult(
            items = listOf(
                SuggestionItem(
                    title = "Fresh Pick",
                    thumbnailUrl = null,
                    providerName = "AniList",
                    providerUrl = "https://anilist.co/manga/1",
                    providerId = "1",
                    mediaType = SuggestionMediaType.NOVEL,
                ),
            ),
            attemptedSources = 2,
            failedSources = 1,
        )
        val runner = cachePolicyRunner(
            repo,
            sourcePreferences = cachePolicySourcePrefs("suggestions_use_shikimori"),
            likeBuilder = DiscoveryLikeRowBuilder(suggestions),
        )

        val outcomes = runner.run(listOf(DiscoveryMediaType.NOVEL))
        val outcome = outcomes[DiscoveryMediaType.NOVEL]!!
        outcome.degradedRows shouldBe setOf(DiscoveryRowType.LIKE)

        repo.flow.value.filter { it.rowType == DiscoveryRowType.LIKE }.map { it.provider } shouldBe
            listOf("AniList")
        repo.flow.value.filter { it.rowType == DiscoveryRowType.LIKE }.map { it.title } shouldBe
            listOf("Fresh Pick")
        repo.replaced.filter { it.second == DiscoveryRowType.LIKE }.size shouldBe 2
        repo.replaced.none { it.second == DiscoveryRowType.TREND || it.second == DiscoveryRowType.SOURCE } shouldBe true
    }

    @Test
    fun `complete provider failure preserves enabled like cache after explicit opt out pruning`() = runTest {
        val repo = FakeRepository(initialSuggestions = cachedProviderSuggestions())
        val suggestions = mockk<SuggestionCoordinator>()
        coEvery { suggestions.fetchSuggestions(any(), any(), any()) } returns SuggestionFetchResult(
            items = emptyList(),
            attemptedSources = 2,
            failedSources = 2,
        )
        val runner = cachePolicyRunner(
            repo,
            sourcePreferences = cachePolicySourcePrefs("suggestions_use_shikimori"),
            likeBuilder = DiscoveryLikeRowBuilder(suggestions),
        )

        val outcomes = runner.run(listOf(DiscoveryMediaType.NOVEL))
        val outcome = outcomes[DiscoveryMediaType.NOVEL]!!
        outcome.failedRows shouldBe setOf(DiscoveryRowType.LIKE)

        repo.flow.value.filter { it.rowType == DiscoveryRowType.LIKE }.map { it.provider } shouldBe
            listOf("AniList", "MyAnimeList", "MangaUpdates", "NovelUpdates", "Local Plugin", "AniList Plugin")
        repo.flow.value.map { it.title } shouldBe
            listOf("Cached 7", "Cached 8", "Cached 0", "Cached 2", "Cached 3", "Cached 4", "Cached 5", "Cached 6")
        repo.replaced.filter { it.second == DiscoveryRowType.LIKE }.map { it.second } shouldBe
            listOf(DiscoveryRowType.LIKE)
        repo.replaced.none { it.second == DiscoveryRowType.TREND || it.second == DiscoveryRowType.SOURCE } shouldBe true
    }

    private fun cachedProviderSuggestions(): List<DiscoverySuggestion> {
        val likeProviders = listOf(
            "AniList",
            "Shikimori",
            "MyAnimeList",
            "MangaUpdates",
            "NovelUpdates",
            "Local Plugin",
            "AniList Plugin",
        )
        val rows = likeProviders.map { DiscoveryRowType.LIKE to it } + listOf(
            DiscoveryRowType.TREND to "Shikimori",
            DiscoveryRowType.SOURCE to "Local Plugin",
        )
        return rows.mapIndexed { index, (rowType, provider) ->
            DiscoverySuggestion(
                id = index.toLong(),
                mediaType = DiscoveryMediaType.NOVEL,
                rowType = rowType,
                title = "Cached $index",
                cleanTitle = "cached $index",
                coverUrl = null,
                reason = null,
                seedTitle = "Seed One",
                provider = provider,
                score = 1.0,
                position = index.toLong(),
                createdAt = 1L,
            )
        }
    }

    private fun cachePolicySourcePrefs(disabledKey: String? = null) = SourcePreferences(
        InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    "suggestions_use_shikimori",
                    disabledKey != "suggestions_use_shikimori",
                    true,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    "suggestions_use_mangaupdates_novel",
                    disabledKey != "suggestions_use_mangaupdates_novel",
                    true,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    "suggestions_use_novelupdates",
                    disabledKey != "suggestions_use_novelupdates",
                    false,
                ),
            ),
        ),
    )

    private fun cachePolicyRunner(
        repo: FakeRepository,
        preferences: DiscoveryPreferences = discoveryPrefs(),
        sourcePreferences: SourcePreferences = cachePolicySourcePrefs(),
        failLike: Boolean = false,
        likeBuilder: DiscoveryRowBuilder? = null,
    ) = DiscoveryRunner(
        repository = repo,
        preferences = preferences,
        seedSources = object : DiscoverySeedSources {
            override suspend fun candidates(mediaType: DiscoveryMediaType) = listOf(
                DiscoverySeedInput(
                    entryId = 1L,
                    title = "Seed One",
                    sourceId = 77L,
                    isCompleted = true,
                    completedAt = System.currentTimeMillis(),
                    lastInteraction = System.currentTimeMillis(),
                ),
            )
            override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
        },
        coordinatorFactory = {
            DiscoveryCoordinator(
                listOf(
                    likeBuilder ?: object : DiscoveryRowBuilder {
                        override val rowType = DiscoveryRowType.LIKE
                        override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                            context.seeds.size shouldBe 1
                            if (failLike) throw IOException("provider unavailable")
                            return emptyList()
                        }
                    },
                ),
            )
        },
        sourcePreferencesProvider = { sourcePreferences },
        installedPluginsProvider = { listOf(DiscoveryInstalledPlugin("local.plugin", listOf(77L))) },
    )

    @Test
    fun `status filter active wipes empty row caches instead of keeping stale statuses`() = runTest {
        val repo = FakeRepository()
        // Фильтр «только Завершённый»; билдер честно возвращает пусто (нет завершённых).
        // «Пустой ряд не затирает кэш» обязан отступить: старые ряды с НЕзавершёнными
        // статусами — ровно та жалоба, из-за которой фильтр «не работал».
        val prefs = DiscoveryPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference("discovery_release_status_filter", "finished", ""),
                ),
            ),
        )
        val seenStatuses = mutableListOf<Set<tachiyomi.domain.discovery.model.DiscoveryReleaseStatus>>()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = prefs,
            seedSources = FakeSeedSources(),
            coordinatorFactory = { builders ->
                DiscoveryCoordinator(
                    builders.map { b ->
                        object : DiscoveryRowBuilder {
                            override val rowType = b.rowType
                            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                                seenStatuses += context.releaseStatuses
                                return emptyList()
                            }
                        }
                    },
                )
            },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        // Контекст донёс фильтр до билдеров, пустые ряды вычищены из кэша.
        seenStatuses.all { it == setOf(tachiyomi.domain.discovery.model.DiscoveryReleaseStatus.FINISHED) } shouldBe true
        repo.replaced.map { it.second }.toSet() shouldBe setOf(
            DiscoveryRowType.LIKE,
            DiscoveryRowType.TASTE,
            DiscoveryRowType.TREND,
            DiscoveryRowType.SOURCE,
        )
        repo.replaced.all { it.third.isEmpty() } shouldBe true
    }

    @Test
    fun `status filter wipe does not touch failed rows`() = runTest {
        val repo = FakeRepository()
        // Активный фильтр + ряд LIKE упал (сеть/провайдер): кэш LIKE обязан выжить —
        // «пустой ряд не перезаписывает» старше явного вайпа, иначе при отвале сети
        // с включённым фильтром лента пропадала бы целиком.
        val prefs = DiscoveryPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference("discovery_release_status_filter", "finished", ""),
                ),
            ),
        )
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = prefs,
            seedSources = FakeSeedSources(),
            coordinatorFactory = {
                DiscoveryCoordinator(
                    listOf(
                        object : DiscoveryRowBuilder {
                            override val rowType = DiscoveryRowType.LIKE
                            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> =
                                throw IOException("network boom")
                        },
                    ),
                )
            },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        // LIKE упал → кэш не тронут (вайп только по честно-пустым рядам).
        repo.replaced.none { it.second == DiscoveryRowType.LIKE } shouldBe true
    }

    // ── Source participation (Task 2) ────────────────────────────────────────────

    private fun weightedSeedSources() = object : DiscoverySeedSources {
        override suspend fun candidates(mediaType: DiscoveryMediaType) = listOf(
            DiscoverySeedInput(entryId = 1, title = "A", sourceId = 11L),
            DiscoverySeedInput(entryId = 2, title = "B", sourceId = 11L),
            DiscoverySeedInput(entryId = 3, title = "C", sourceId = 22L),
        )

        override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
    }

    private fun emptySeedSources() = object : DiscoverySeedSources {
        override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
        override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
    }

    private fun capturingBuilder(captured: MutableList<DiscoveryBuildContext>) = object : DiscoveryRowBuilder {
        override val rowType = DiscoveryRowType.LIKE
        override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
            captured += context
            return emptyList()
        }
    }

    private fun discoveryPrefs(vararg prefs: InMemoryPreferenceStore.InMemoryPreference<*>) =
        DiscoveryPreferences(InMemoryPreferenceStore(prefs.asSequence()))

    @Test
    fun `excluded sources are filtered from context in auto mode`() = runTest {
        val captured = mutableListOf<DiscoveryBuildContext>()
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = discoveryPrefs(
                InMemoryPreferenceStore.InMemoryPreference("discovery_source_excluded_novel", "p2", ""),
            ),
            seedSources = weightedSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(listOf(capturingBuilder(captured))) },
            sourcePreferencesProvider = ::testSourcePrefs,
            installedPluginsProvider = {
                listOf(
                    DiscoveryInstalledPlugin("p1", listOf(11L)),
                    DiscoveryInstalledPlugin("p2", listOf(22L)),
                )
            },
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        captured.single().sourceIds shouldBe listOf(11L)
    }

    @Test
    fun `manual mode uses installed weight order and library-top primary`() = runTest {
        val captured = mutableListOf<DiscoveryBuildContext>()
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = discoveryPrefs(
                InMemoryPreferenceStore.InMemoryPreference("discovery_source_mode_novel", "manual", "auto"),
            ),
            seedSources = weightedSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(listOf(capturingBuilder(captured))) },
            sourcePreferencesProvider = ::testSourcePrefs,
            installedPluginsProvider = {
                listOf(
                    DiscoveryInstalledPlugin("p1", listOf(11L)),
                    DiscoveryInstalledPlugin("p2", listOf(22L)),
                    DiscoveryInstalledPlugin("p3", listOf(33L)),
                )
            },
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        // weightOrder: библиотечные по весу (11×2, 22×1) + нулевой вес 33 хвостом.
        captured.single().sourceIds shouldBe listOf(11L, 22L, 33L)
        // lastUsed не задан (−1) → primary = топ библиотеки.
        captured.single().sourceId shouldBe 11L
    }

    @Test
    fun `manual mode includes all 15 plugin representatives and maps source weights`() = runTest {
        val captured = mutableListOf<DiscoveryBuildContext>()
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = discoveryPrefs(
                InMemoryPreferenceStore.InMemoryPreference("discovery_source_mode_novel", "manual", "auto"),
            ),
            seedSources = emptySeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(listOf(capturingBuilder(captured))) },
            sourcePreferencesProvider = ::testSourcePrefs,
            installedPluginsProvider = { (1L..15L).map { DiscoveryInstalledPlugin("p$it", listOf(it)) } },
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        captured.single().sourceIds shouldBe (1L..15L).toList()
        captured.single().sourceWeights.keys shouldBe (1L..15L).toSet()
    }

    @Test
    fun `excluded lastUsed yields primary from allowed`() = runTest {
        val captured = mutableListOf<DiscoveryBuildContext>()
        val sourcePrefs = SourcePreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    // Preference.appStateKey("last_novel_catalogue_source"), default −1.
                    InMemoryPreferenceStore.InMemoryPreference("__APP_STATE_last_novel_catalogue_source", 22L, -1L),
                ),
            ),
        )
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = discoveryPrefs(
                InMemoryPreferenceStore.InMemoryPreference("discovery_source_excluded_novel", "p2", ""),
            ),
            seedSources = weightedSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(listOf(capturingBuilder(captured))) },
            sourcePreferencesProvider = { sourcePrefs },
            installedPluginsProvider = {
                listOf(
                    DiscoveryInstalledPlugin("p1", listOf(11L)),
                    DiscoveryInstalledPlugin("p2", listOf(22L)),
                )
            },
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        captured.single().sourceIds shouldBe listOf(11L)
        captured.single().sourceId shouldBe 11L
    }

    @Test
    fun `language variants of one plugin collapse into single pick source`() = runTest {
        val captured = mutableListOf<DiscoveryBuildContext>()
        val appLang = java.util.Locale.getDefault().language.lowercase()
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = discoveryPrefs(),
            seedSources = emptySeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(listOf(capturingBuilder(captured))) },
            sourcePreferencesProvider = ::testSourcePrefs,
            // Одно расширение с пятью языковыми вариантами — в ряд попадает один репрезентативный.
            installedPluginsProvider = {
                listOf(
                    DiscoveryInstalledPlugin(
                        "multi.lang",
                        listOf(101L, 102L, 103L, 104L, 105L),
                        mapOf(101L to appLang, 102L to "id", 103L to "en", 104L to "es", 105L to "ja"),
                    ),
                )
            },
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        captured.single().sourceIds shouldBe listOf(101L)
    }

    @Test
    fun `feed rows avoid indonesian variant when app language available`() = runTest {
        // Индонезийский имеет МИНИМАЛЬНЫЙ id — прежний выбор отдал бы его.
        val captured = mutableListOf<DiscoveryBuildContext>()
        val appLang = java.util.Locale.getDefault().language.lowercase()
        val nonAppLang = if (appLang == "id") "xx" else "id"
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = discoveryPrefs(),
            seedSources = emptySeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(listOf(capturingBuilder(captured))) },
            sourcePreferencesProvider = ::testSourcePrefs,
            installedPluginsProvider = {
                listOf(
                    DiscoveryInstalledPlugin(
                        "multi.lang",
                        listOf(200L, 250L),
                        mapOf(200L to nonAppLang, 250L to appLang),
                    ),
                )
            },
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        captured.single().sourceIds shouldBe listOf(250L)
    }

    // ── Прямое открытие plugin-bound подборок (Task 1, data-срез) ───────────────

    @Test
    fun `row item source binding is persisted into suggestion`() = runTest {
        val repo = FakeRepository()
        val boundBuilder = object : DiscoveryRowBuilder {
            // SOURCE: по спеку привязка рождается в каталоге плагина (LIKE всегда null).
            override val rowType = DiscoveryRowType.SOURCE
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> = listOf(
                DiscoveryRowItem(
                    title = "Bound Title",
                    cleanTitle = "bound title",
                    coverUrl = null,
                    reason = null,
                    seedTitle = "Seed One",
                    provider = "source-77",
                    score = 1.0,
                    sourceId = 77L,
                    sourceUrl = "/manga/77",
                ),
            )
        }
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(listOf(boundBuilder)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        val sourceRow = repo.replaced.first { it.second == DiscoveryRowType.SOURCE }
        sourceRow.third.single().sourceId shouldBe 77L
        sourceRow.third.single().sourceUrl shouldBe "/manga/77"
    }

    @Test
    fun `manual refresh count not persisted when generation yields no rows`() = runTest {
        val prefs = DiscoveryPreferences(eu.kanade.tachiyomi.test.PersistingPreferenceStore())
        val runner = DiscoveryRunner(
            repository = FakeRepository(),
            preferences = prefs,
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(emptyList()) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.MANGA), isManualRefresh = true)
        prefs.manualRefreshCount(DiscoveryMediaType.MANGA).get() shouldBe 0
    }

    @Test
    fun `like row cache cleared when seeds empty and row honestly empty`() = runTest {
        val repo = FakeRepository()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = emptySeedSources(),
            coordinatorFactory = {
                DiscoveryCoordinator(
                    listOf(
                        object : DiscoveryRowBuilder {
                            override val rowType = DiscoveryRowType.LIKE
                            override suspend fun build(
                                context: DiscoveryBuildContext,
                            ): List<DiscoveryRowItem> = emptyList()
                        },
                    ),
                )
            },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.MANGA), isManualRefresh = false)
        repo.replaced.any { it.second == DiscoveryRowType.LIKE && it.third.isEmpty() } shouldBe true
    }

    @Test
    fun `parallel runs for the same media type are serialized`() = runTest {
        var currentConcurrent = 0
        var maxConcurrent = 0
        val concurrencyLock = Mutex()
        val repo = object : FakeRepository() {
            override suspend fun replaceRows(
                mediaType: DiscoveryMediaType,
                rowType: DiscoveryRowType,
                items: List<DiscoverySuggestion>,
            ) {
                concurrencyLock.withLock {
                    currentConcurrent++
                    if (currentConcurrent > maxConcurrent) maxConcurrent = currentConcurrent
                }
                delay(50)
                concurrencyLock.withLock {
                    currentConcurrent--
                }
                super.replaceRows(mediaType, rowType, items)
            }
        }
        val builder = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.LIKE
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                return listOf(DiscoveryRowItem("T", "t", null, null, "Seed", "anilist", 1.0))
            }
        }
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(listOf(builder)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        val j1 = async { runner.run(listOf(DiscoveryMediaType.MANGA)) }
        val j2 = async { runner.run(listOf(DiscoveryMediaType.MANGA)) }
        listOf(j1, j2).awaitAll()
        maxConcurrent shouldBe 1
    }

    // ── Языковой преференс представителя плагина ───────────────────────────────

    @Test
    fun `representative prefers app language variant regardless of id order`() {
        // id инверсия: индонезийский id меньше русского — прежде выбирался бы он.
        val langs = mapOf(900L to "id", 950L to "ru", 910L to "en")
        selectPluginRepresentative(
            memberIds = setOf(900L, 950L, 910L),
            weights = emptyMap(),
            langs = langs,
            appLang = "ru",
        ) shouldBe 950L
    }

    @Test
    fun `representative falls back to english when app language variant absent`() {
        val langs = mapOf(900L to "id", 940L to "en")
        selectPluginRepresentative(
            memberIds = setOf(900L, 940L),
            weights = emptyMap(),
            langs = langs,
            appLang = "ru",
        ) shouldBe 940L
    }

    @Test
    fun `representative weight dominates within preferred language group`() {
        // Оба варианта ru, но 902 имеет вес библиотеки — он и представитель.
        val langs = mapOf(901L to "ru", 902L to "ru", 903L to "id")
        selectPluginRepresentative(
            memberIds = setOf(901L, 902L, 903L),
            weights = mapOf(901L to 3, 902L to 7),
            langs = langs,
            appLang = "ru",
        ) shouldBe 902L
    }

    @Test
    fun `representative keeps legacy behaviour when langs unknown`() {
        // Пустая карта lang (легаси-провайдер / тест) — прежняя семантика max weight → min id.
        selectPluginRepresentative(
            memberIds = setOf(101L, 102L, 103L),
            weights = mapOf(101L to 2, 102L to 2, 103L to 5),
            langs = emptyMap(),
            appLang = "ru",
        ) shouldBe 103L
        selectPluginRepresentative(
            memberIds = setOf(101L, 102L),
            weights = emptyMap(),
            langs = emptyMap(),
            appLang = "ru",
        ) shouldBe 101L
    }

    @Test
    fun `representative returns null for empty members`() {
        selectPluginRepresentative(setOf(), emptyMap(), emptyMap(), "ru") shouldBe null
    }

    @Test
    fun `consumed positive signals remain feedback seeds without becoming output candidates`() {
        val signals = listOf(
            testSignal("consumed liked", DiscoverySignalType.CONSUMED_LIKED, createdAt = 300L),
            testSignal("consumed added", DiscoverySignalType.CONSUMED_ADDED, createdAt = 200L),
            testSignal("consumed", DiscoverySignalType.CONSUMED, createdAt = 400L),
        )
        selectFeedbackSeeds(signals, DiscoveryMediaType.MANGA).map { it.cleanTitle } shouldBe
            listOf("consumed liked", "consumed added")
        signals.all { it.signalType.excludesFromFeed } shouldBe true
    }

    private fun testSignal(
        cleanTitle: String,
        signalType: DiscoverySignalType,
        mediaType: DiscoveryMediaType = DiscoveryMediaType.MANGA,
        title: String = cleanTitle,
        genres: List<String> = emptyList(),
        createdAt: Long = 1000L,
    ): DiscoverySignal = DiscoverySignal(
        mediaType = mediaType,
        cleanTitle = cleanTitle,
        title = title,
        signalType = signalType,
        genres = genres,
        provider = null,
        sourceKey = null,
        createdAt = createdAt,
    )

    @Test
    fun `selectFeedbackSeeds selects newest like or add and dedupes cleanTitle up to two`() {
        val signals = listOf(
            testSignal("title 1", DiscoverySignalType.LIKE, createdAt = 100L),
            testSignal("title 1", DiscoverySignalType.ADD, createdAt = 200L),
            testSignal("Title 1", DiscoverySignalType.LIKE, createdAt = 50L),
            testSignal("title 2", DiscoverySignalType.LIKE, createdAt = 150L),
            testSignal("title 3", DiscoverySignalType.ADD, createdAt = 120L),
            testSignal("other media", DiscoverySignalType.LIKE, mediaType = DiscoveryMediaType.ANIME, createdAt = 300L),
            testSignal("hide", DiscoverySignalType.HIDE, createdAt = 500L),
            testSignal("click", DiscoverySignalType.CLICK, createdAt = 500L),
            testSignal("consumed", DiscoverySignalType.CONSUMED, createdAt = 500L),
        )

        val selected = selectFeedbackSeeds(signals, DiscoveryMediaType.MANGA, limit = 2)

        selected.size shouldBe 2
        selected[0].cleanTitle shouldBe "title 1"
        selected[0].timestamp shouldBe 200L
        selected[1].cleanTitle shouldBe "title 2"
        selected[1].timestamp shouldBe 150L
    }

    @Test
    fun `runner populates feedback seeds from signals with priority without fake entry id`() = runTest {
        val captured = mutableListOf<DiscoveryBuildContext>()
        val signals = listOf(
            testSignal(
                "liked title 1",
                DiscoverySignalType.LIKE,
                mediaType = DiscoveryMediaType.MANGA,
                genres = listOf("Action"),
                createdAt = 100L,
            ),
            testSignal(
                "liked title 2",
                DiscoverySignalType.ADD,
                mediaType = DiscoveryMediaType.MANGA,
                genres = listOf("Fantasy"),
                createdAt = 200L,
            ),
        )
        val now = System.currentTimeMillis()
        val libraryCandidates = listOf(
            DiscoverySeedInput(
                entryId = 101L,
                title = "liked title 1",
                isCompleted = true,
                completedAt = now,
                lastInteraction = now,
            ),
            DiscoverySeedInput(
                entryId = 102L,
                title = "library title 2",
                isCompleted = true,
                completedAt = now,
                lastInteraction = now,
            ),
            DiscoverySeedInput(
                entryId = 103L,
                title = "library title 3",
                isCompleted = true,
                completedAt = now,
                lastInteraction = now,
            ),
        )
        val seedSources = object : DiscoverySeedSources {
            override suspend fun candidates(mediaType: DiscoveryMediaType) = libraryCandidates
            override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
        }
        val runner = DiscoveryRunner(
            repository = FakeRepository(initialSignals = signals),
            preferences = discoveryPrefs(),
            seedSources = seedSources,
            coordinatorFactory = { DiscoveryCoordinator(listOf(capturingBuilder(captured))) },
            sourcePreferencesProvider = ::testSourcePrefs,
            installedPluginsProvider = { emptyList() },
        )

        runner.run(listOf(DiscoveryMediaType.MANGA))

        val ctx = captured.single()
        ctx.feedbackSeeds.size shouldBe 2
        ctx.feedbackSeeds.map { it.cleanTitle } shouldBe listOf("liked title 2", "liked title 1")
        ctx.feedbackSeeds[0].title shouldBe "liked title 2"
        ctx.feedbackSeeds[0].genres shouldBe listOf("Fantasy")
        ctx.feedbackSeeds[0].timestamp shouldBe 200L
        ctx.feedbackSeeds[1].title shouldBe "liked title 1"
        ctx.feedbackSeeds[1].genres shouldBe listOf("Action")
        ctx.feedbackSeeds[1].timestamp shouldBe 100L

        ctx.seeds.size shouldBe 1
        ctx.seeds.single().entryId shouldBe 102L
        ctx.seeds.single().title shouldBe "library title 2"
    }
}
