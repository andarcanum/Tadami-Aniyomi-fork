package eu.kanade.tachiyomi.data.updater

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.interactor.AppUpdateVersionComparator
import tachiyomi.domain.release.service.AppUpdatePreferences
import java.nio.file.Path

class AppUpdateFileManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var manager: AppUpdateFileManager
    private lateinit var preferences: AppUpdatePreferences

    @BeforeEach
    fun setUp() {
        val cacheDir = tempDir.toFile()
        val context = mockk<Context>()
        every { context.externalCacheDir } returns cacheDir
        every { context.cacheDir } returns cacheDir

        preferences = AppUpdatePreferences(MutablePreferenceStore())
        manager = AppUpdateFileManager(context = context, preferences = preferences)
    }

    @Test
    fun `deletes cached apk after installed stable version catches up`() {
        manager.apkFile().writeText("apk")
        manager.recordDownloadedVersion("v0.34")
        preferences.downloadedAppUpdateVersion().get() shouldBe "v0.34"
        AppUpdateVersionComparator.hasInstalledOrNewer(
            isPreview = false,
            installedCommitCount = 0,
            installedVersionName = "0.34",
            targetVersionTag = "v0.34",
        ) shouldBe true

        manager.cleanupIfInstalledVersionReached(
            isPreview = false,
            installedCommitCount = 0,
            installedVersionName = "0.34",
        )

        manager.apkFile().exists() shouldBe false
        preferences.downloadedAppUpdateVersion().get() shouldBe ""
    }

    @Test
    fun `keeps cached apk when installed version is still behind`() {
        manager.apkFile().writeText("apk")
        manager.recordDownloadedVersion("v0.34")

        manager.cleanupIfInstalledVersionReached(
            isPreview = false,
            installedCommitCount = 0,
            installedVersionName = "0.33",
        )

        manager.apkFile().exists() shouldBe true
        preferences.downloadedAppUpdateVersion().get() shouldBe "v0.34"
    }

    @Test
    fun `clears downloaded version even when cached apk is already gone`() {
        manager.recordDownloadedVersion("v0.34")

        manager.cleanupIfInstalledVersionReached(
            isPreview = false,
            installedCommitCount = 0,
            installedVersionName = "0.34",
        )

        manager.apkFile().exists() shouldBe false
        preferences.downloadedAppUpdateVersion().get() shouldBe ""
    }

    private class MutablePreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, Any?>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            MutablePreference(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            MutablePreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            MutablePreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            MutablePreference(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            MutablePreference(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            MutablePreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = MutablePreference(key, defaultValue)

        override fun getAll(): Map<String, *> = values.toMap()

        private inner class MutablePreference<T>(
            private val keyName: String,
            private val default: T,
        ) : Preference<T> {
            private val state = MutableStateFlow(get())

            override fun key(): String = keyName

            @Suppress("UNCHECKED_CAST")
            override fun get(): T = values[keyName] as? T ?: default

            override fun set(value: T) {
                values[keyName] = value
                state.value = value
            }

            override fun isSet(): Boolean = values.containsKey(keyName)

            override fun delete() {
                values.remove(keyName)
                state.value = default
            }

            override fun defaultValue(): T = default

            override fun changes(): Flow<T> = state

            override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
        }
    }
}
