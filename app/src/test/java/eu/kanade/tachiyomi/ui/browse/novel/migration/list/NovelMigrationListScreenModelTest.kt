package eu.kanade.tachiyomi.ui.browse.novel.migration.list

import android.app.Application
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

/**
 * Regression (crash log 0.62 build 209, 2026-09-19, OPPO CPH2591): the constructor resolved
 * its `context` default as `Context` from Injekt (added by 22834f00d to localize the
 * migration search progress label), but the production DI graph binds only `Application`
 * (`addSingleton(app)` in AppModule) -- Injekt matches exact type keys and does not walk
 * supertypes, so opening the migration list screen threw
 * "InjektionException: No registered instance or factory for type class android.content.Context"
 * on the main thread. Voyager restored the screen after each process death, turning it into a
 * crash loop.
 *
 * This test deliberately registers ONLY what production registers: Application. Do not add a
 * Context binding here -- its absence is the contract under test.
 */
class NovelMigrationListScreenModelTest {

    private val created = mutableListOf<NovelMigrationListScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runCatching { Injekt.get<Application>() }
            .getOrElse {
                Injekt.addSingleton(fullType<Application>(), mockk(relaxed = true))
            }
    }

    @AfterEach
    fun tearDown() {
        created.forEach { runCatching { it.onDispose() } }
        created.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `constructor resolves its context default from the Application binding`() {
        val fakeSourceManager = object : NovelSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
            override val catalogueSources: Flow<List<NovelCatalogueSource>> = emptyFlow()
            override fun get(sourceKey: Long): NovelSource? = null
            override fun getOrStub(sourceKey: Long): NovelSource = mockk(relaxed = true)
            override fun getOnlineSources(): List<NovelHttpSource> = emptyList()
            override fun getCatalogueSources(): List<NovelCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubNovelSource> = emptyList()
        }
        val screenModel = NovelMigrationListScreenModel(
            novelIds = emptyList(),
            sourceIds = emptyList(),
            extraSearchQuery = null,
            // The init search reads source and migration preferences even with no novel ids.
            // Real defaults avoid relaxed generic return values escaping into another test.
            sourcePreferences = SourcePreferences(InMemoryPreferenceStore()),
            sourceManager = fakeSourceManager,
            getNovel = mockk(relaxed = true),
            novelChapterRepository = mockk(relaxed = true),
            migrateNovel = mockk(relaxed = true),
            preferenceStore = InMemoryPreferenceStore(),
        )
        created += screenModel

        // Empty ids: the init search pipeline is a no-op, items stay empty.
        screenModel.state.value.items.isEmpty() shouldBe true
    }
}
