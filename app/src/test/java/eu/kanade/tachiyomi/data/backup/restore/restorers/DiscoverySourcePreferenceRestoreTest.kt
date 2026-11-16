package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.discovery.parseKeyCsv
import eu.kanade.tachiyomi.data.discovery.reconcileDiscoverySourcePreferences
import eu.kanade.tachiyomi.data.library.LibraryAutoUpdateSchedulerJob
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DiscoverySourcePreferenceRestoreTest {

    @Test
    fun `restored manual exclusion survives concurrent extension reconciliation`() {
        val backing = PersistingPreferenceStore()
        val capturedOldExclusions = CountDownLatch(1)
        val releaseReconciliation = CountDownLatch(1)
        val restoreStarted = CountDownLatch(1)
        val restoreWroteExclusion = CountDownLatch(1)
        val pauseNextRead = AtomicBoolean(false)
        val excludedKey = "discovery_source_excluded_manga"
        val store = object : PreferenceStore by backing {
            override fun getString(key: String, defaultValue: String): Preference<String> {
                val preference = synchronized(backing) { backing.getString(key, defaultValue) }
                if (key != excludedKey) return preference
                return object : Preference<String> by preference {
                    override fun get(): String {
                        val captured = preference.get()
                        if (pauseNextRead.compareAndSet(true, false)) {
                            capturedOldExclusions.countDown()
                            check(releaseReconciliation.await(10, TimeUnit.SECONDS)) {
                                "The test did not release the reconciliation read"
                            }
                        }
                        return captured
                    }

                    override fun set(value: String) {
                        preference.set(value)
                        if (value == "ext.b") restoreWroteExclusion.countDown()
                    }
                }
            }
        }
        val preferences = DiscoveryPreferences(store).apply {
            discoverySourceMode(DiscoveryMediaType.MANGA).set("manual")
            discoverySourceKnown(DiscoveryMediaType.MANGA).set("ext.a,ext.b")
            discoverySourceExcluded(DiscoveryMediaType.MANGA).set("")
        }
        val context = mockk<Context>(relaxed = true)
        val restorer = PreferenceRestorer(
            context = context,
            getMangaCategories = mockk(),
            getAnimeCategories = mockk(),
            getNovelCategories = mockk(),
            preferenceStore = store,
        )
        val executor = Executors.newFixedThreadPool(2)
        // Only WorkManager scheduling is replaced; the real restore writer and reconciliation run.
        mockkObject(LibraryAutoUpdateSchedulerJob.Companion, BackupCreateJob.Companion)
        justRun { LibraryAutoUpdateSchedulerJob.setupTask(context) }
        justRun { BackupCreateJob.setupTask(context) }
        try {
            pauseNextRead.set(true)
            val reconciliation = executor.submit<Unit> {
                reconcileDiscoverySourcePreferences(
                    preferences,
                    DiscoveryMediaType.MANGA,
                    listOf("ext.a", "ext.b", "ext.c"),
                )
            }
            capturedOldExclusions.await(5, TimeUnit.SECONDS) shouldBe true
            val restoration = executor.submit<Unit> {
                restoreStarted.countDown()
                runBlocking {
                    restorer.restoreApp(
                        preferences = listOf(
                            BackupPreference("discovery_source_mode_manga", StringPreferenceValue("manual")),
                            BackupPreference(excludedKey, StringPreferenceValue("ext.b")),
                            BackupPreference("discovery_source_known_manga", StringPreferenceValue("ext.a,ext.b")),
                        ),
                        backupCategories = null,
                    )
                }
            }
            restoreStarted.await(5, TimeUnit.SECONDS) shouldBe true
            // Old code writes before the paused read is released. A serialized restore waits instead.
            restoreWroteExclusion.await(2, TimeUnit.SECONDS)
            releaseReconciliation.countDown()
            reconciliation.get(10, TimeUnit.SECONDS)
            restoration.get(10, TimeUnit.SECONDS)

            ("ext.b" in parseKeyCsv(preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).get())) shouldBe true
        } finally {
            pauseNextRead.set(false)
            releaseReconciliation.countDown()
            executor.shutdownNow()
            unmockkObject(LibraryAutoUpdateSchedulerJob.Companion, BackupCreateJob.Companion)
        }
    }

    @Test
    fun `restoring an explicit empty exclusion preserves omitted source keys and other media`(): Unit = runBlocking {
        val store = PersistingPreferenceStore()
        val preferences = DiscoveryPreferences(store).apply {
            discoverySourceMode(DiscoveryMediaType.MANGA).set("manual")
            discoverySourceKnown(DiscoveryMediaType.MANGA).set("ext.a,ext.b")
            discoverySourceExcluded(DiscoveryMediaType.MANGA).set("ext.b")
            discoverySourceMode(DiscoveryMediaType.ANIME).set("manual")
            discoverySourceKnown(DiscoveryMediaType.ANIME).set("anime.keep")
            discoverySourceExcluded(DiscoveryMediaType.ANIME).set("anime.off")
        }
        val context = mockk<Context>(relaxed = true)
        val restorer = PreferenceRestorer(context, mockk(), mockk(), mockk(), store)
        mockkObject(LibraryAutoUpdateSchedulerJob.Companion, BackupCreateJob.Companion)
        justRun { LibraryAutoUpdateSchedulerJob.setupTask(context) }
        justRun { BackupCreateJob.setupTask(context) }
        try {
            restorer.restoreApp(
                listOf(BackupPreference("discovery_source_excluded_manga", StringPreferenceValue(""))),
                backupCategories = null,
            )

            preferences.discoverySourceExcluded(DiscoveryMediaType.MANGA).get() shouldBe ""
            preferences.discoverySourceMode(DiscoveryMediaType.MANGA).get() shouldBe "manual"
            preferences.discoverySourceKnown(DiscoveryMediaType.MANGA).get() shouldBe "ext.a,ext.b"
            preferences.discoverySourceMode(DiscoveryMediaType.ANIME).get() shouldBe "manual"
            preferences.discoverySourceKnown(DiscoveryMediaType.ANIME).get() shouldBe "anime.keep"
            preferences.discoverySourceExcluded(DiscoveryMediaType.ANIME).get() shouldBe "anime.off"
        } finally {
            unmockkObject(LibraryAutoUpdateSchedulerJob.Companion, BackupCreateJob.Companion)
        }
    }
}
