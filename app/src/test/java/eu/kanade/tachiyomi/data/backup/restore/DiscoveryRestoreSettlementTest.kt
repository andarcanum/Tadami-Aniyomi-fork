package eu.kanade.tachiyomi.data.backup.restore

import android.app.Application
import android.net.Uri
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.BackupDiagnosticLog
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.DecodedBackup
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.TranslationConfigurationRestorer
import eu.kanade.tachiyomi.data.discovery.DiscoverySourceInventorySource
import eu.kanade.tachiyomi.data.discovery.DiscoverySourcePolicyResolver
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySourceInventory
import tachiyomi.domain.discovery.model.DiscoverySourcePlugin
import tachiyomi.domain.discovery.model.DiscoverySourcePolicy
import tachiyomi.domain.discovery.service.DiscoverySourcePolicyProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DiscoveryRestoreSettlementTest {
    @Before
    fun suppressDiagnosticFileBoundary() {
        mockkObject(BackupDiagnosticLog)
        justRun { BackupDiagnosticLog.log(any(), any(), any()) }
    }

    @After
    fun restoreDiagnosticBoundary() {
        unmockkObject(BackupDiagnosticLog)
    }

    @Test
    fun `source reconciliation and refresh occur once after every restore writer has settled`() = runTest {
        val appEntered = CompletableDeferred<Unit>()
        val sourceEntered = CompletableDeferred<Unit>()
        val releaseApp = CompletableDeferred<Unit>()
        val releaseSource = CompletableDeferred<Unit>()
        val preferences = mockk<PreferenceRestorer>()
        coEvery { preferences.restoreApp(any(), any()) } coAnswers {
            appEntered.complete(Unit)
            releaseApp.await()
        }
        coEvery { preferences.restoreSource(any()) } coAnswers {
            sourceEntered.complete(Unit)
            releaseSource.await()
        }
        val calls = mutableListOf<Set<DiscoveryMediaType>>()
        val restorer = restorer(preferences) { calls += it }
        val task = async { restorer.restoreFromFile(Uri.EMPTY, options()) }
        appEntered.await()
        sourceEntered.await()
        releaseApp.complete(Unit)
        runCurrent()
        calls shouldBe emptyList()
        releaseSource.complete(Unit)
        task.await()
        calls shouldBe listOf(setOf(DiscoveryMediaType.NOVEL))
    }

    @Test
    fun `cancelled restore never schedules configuration from a finally block`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val preferences = mockk<PreferenceRestorer>()
        coEvery { preferences.restoreApp(any(), any()) } coAnswers {
            entered.complete(Unit)
            release.await()
        }
        coEvery { preferences.restoreSource(any()) } returns Unit
        val calls = mutableListOf<Set<DiscoveryMediaType>>()
        val restorer = restorer(preferences) { calls += it }
        val task = async { restorer.restoreFromFile(Uri.EMPTY, options()) }
        entered.await()
        task.cancelAndJoin()
        calls shouldBe emptyList()
    }

    @Test
    fun `settlement preserves restored known unions without waiting for a not-yet-installed plugin`() = runTest {
        val media = DiscoveryMediaType.NOVEL
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.a,ext.b")
            discoverySourceExcluded(media).set("ext.b")
        }
        val inventory = kotlinx.coroutines.flow.MutableStateFlow(
            DiscoverySourceInventory(
                true,
                listOf(DiscoverySourcePlugin("ext.a", listOf(11L))),
            ),
        )
        val policies = DiscoverySourcePolicyResolver(
            preferences = preferences,
            sourcePreferences = SourcePreferences(PersistingPreferenceStore()),
            inventorySourceLoader = {
                DiscoverySourceInventorySource({
                    inventory.value
                }, inventory)
            },
            scope = backgroundScope,
        )
        runCurrent()
        val requests = mutableListOf<DiscoveryMediaType>()
        settleRestoredDiscovery(mockk(relaxed = true), setOf(media), preferences, policies, reschedule = {
        }, refresh = {
            requests += it
        })
        preferences.discoverySourceKnown(media).get() shouldBe "ext.a,ext.b"
        preferences.discoverySourceExcluded(media).get() shouldBe "ext.b"
        requests shouldBe listOf(media)
    }

    @Test
    fun `incomplete settlement leaves the restored selection untouched and queues bounded readiness work`() = runTest {
        val media = DiscoveryMediaType.NOVEL
        val preferences = DiscoveryPreferences(PersistingPreferenceStore()).apply {
            discoverySourceMode(media).set("manual")
            discoverySourceKnown(media).set("ext.a")
        }
        val policy = DiscoverySourcePolicy(
            media,
            "partial",
            false,
            emptySet(),
            mapOf("ext.new" to setOf(12L)),
        )
        val provider = object : DiscoverySourcePolicyProvider {
            override fun current(mediaType: DiscoveryMediaType) = policy
            override fun observe(mediaType: DiscoveryMediaType) = kotlinx.coroutines.flow.flowOf(policy)
            override suspend fun awaitComplete(
                mediaType: DiscoveryMediaType,
            ) = error("Settlement must not wait for an installer")
        }
        val requests = mutableListOf<DiscoveryMediaType>()
        settleRestoredDiscovery(mockk(relaxed = true), setOf(media), preferences, provider, reschedule = {
        }, refresh = {
            requests += it
        })
        preferences.discoverySourceKnown(media).get() shouldBe "ext.a"
        preferences.discoverySourceExcluded(media).get() shouldBe ""
        requests shouldBe listOf(media)
    }

    private fun restorer(
        preferences: PreferenceRestorer,
        after: suspend (Set<DiscoveryMediaType>) -> Unit,
    ): BackupRestorer {
        val payload = Backup(
            backupPreferences = listOf(
                BackupPreference("discovery_source_mode_novel", StringPreferenceValue("manual")),
                BackupPreference("discovery_source_known_novel", StringPreferenceValue("ext.a,ext.b")),
            ),
        )
        val translation = mockk<TranslationConfigurationRestorer>()
        coEvery { translation.restore(any(), any()) } returns null
        return BackupRestorer(
            context = mockk(relaxed = true),
            notifier = mockk(relaxed = true),
            isSync = false,
            animeCategoriesRestorer = mockk(),
            mangaCategoriesRestorer = mockk(),
            novelCategoriesRestorer = mockk(),
            preferenceRestorer = preferences,
            animeExtensionRepoRestorer = mockk(),
            mangaExtensionRepoRestorer = mockk(),
            novelExtensionRepoRestorer = mockk(),
            animeExtensionStoreRestorer = mockk(),
            mangaExtensionStoreRestorer = mockk(),
            novelExtensionStoreRestorer = mockk(),
            customButtonRestorer = mockk(),
            animeRestorer = mockk(),
            mangaRestorer = mockk(),
            novelRestorer = mockk(),
            animeSeriesRestorer = mockk(),
            mangaSeriesRestorer = mockk(),
            novelSeriesRestorer = mockk(),
            feedRestorer = mockk(),
            reelsFavoritesRestorer = mockk(),
            reelsFollowsRestorer = mockk(),
            discoveryRestorer = mockk(),
            extensionsRestorer = mockk(),
            achievementRestorer = mockk(), translationConfigurationRestorer = translation,
            decodeBackup = { _, policy ->
                DecodedBackup(payload, BackupOrigin.TADAMI, policy)
            }, afterRestoreDiscovery = after,
        )
    }

    @Test
    fun `restore precision ignores runtime or UI keys and scopes per-media preferences accurately`() = runTest {
        val calls = mutableListOf<Set<DiscoveryMediaType>>()
        val preferences = mockk<PreferenceRestorer>()
        coEvery { preferences.restoreApp(any(), any()) } returns Unit
        coEvery { preferences.restoreSource(any()) } returns Unit
        val payload = Backup(
            backupPreferences = listOf(
                BackupPreference(
                    "discovery_teaser_count",
                    eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue(12),
                ),
                BackupPreference(
                    "discovery_manual_refresh_at_anime",
                    eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue(999L),
                ),
                BackupPreference(
                    "discovery_binding_backfill_version",
                    eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue(50),
                ),
                BackupPreference(
                    "suggestions_use_novelupdates",
                    eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue(true),
                ),
            ),
            backupSourcePreferences = listOf(
                eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences("source_1", emptyList()),
            ),
        )
        val translation = mockk<TranslationConfigurationRestorer>()
        coEvery { translation.restore(any(), any()) } returns null
        val restorer = BackupRestorer(
            context = mockk(relaxed = true),
            notifier = mockk(relaxed = true),
            isSync = false,
            animeCategoriesRestorer = mockk(),
            mangaCategoriesRestorer = mockk(),
            novelCategoriesRestorer = mockk(),
            preferenceRestorer = preferences,
            animeExtensionRepoRestorer = mockk(),
            mangaExtensionRepoRestorer = mockk(),
            novelExtensionRepoRestorer = mockk(),
            animeExtensionStoreRestorer = mockk(),
            mangaExtensionStoreRestorer = mockk(),
            novelExtensionStoreRestorer = mockk(),
            customButtonRestorer = mockk(),
            animeRestorer = mockk(),
            mangaRestorer = mockk(),
            novelRestorer = mockk(),
            animeSeriesRestorer = mockk(),
            mangaSeriesRestorer = mockk(),
            novelSeriesRestorer = mockk(),
            feedRestorer = mockk(),
            reelsFavoritesRestorer = mockk(),
            reelsFollowsRestorer = mockk(),
            discoveryRestorer = mockk(),
            extensionsRestorer = mockk(),
            achievementRestorer = mockk(),
            translationConfigurationRestorer = translation,
            decodeBackup = { _, policy ->
                DecodedBackup(payload, BackupOrigin.TADAMI, policy)
            },
            afterRestoreDiscovery = { calls += it },
        )
        restorer.restoreFromFile(Uri.EMPTY, options())
        calls shouldBe listOf(setOf(DiscoveryMediaType.NOVEL))
    }

    private fun options() = RestoreOptions(
        libraryEntries = false,
        categories = false,
        appSettings = true,
        extensionRepoSettings = false,
        customButtons = false,
        sourceSettings = true,
        extensions = false,
        achievements = false,
        stats = false,
        reelsFavorites = false, reelsFollows = false, discoveryData = false,
    )
}
