package eu.kanade.presentation.easteregg.crystal

import eu.kanade.domain.easteregg.crystal.CrystalAppearancePreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CrystalAtmospherePreferencesTest {
    private val strings = ConcurrentHashMap<String, InMemoryPreference<String>>()
    private val booleans = ConcurrentHashMap<String, InMemoryPreference<Boolean>>()
    private val store = mockk<PreferenceStore> {
        every { getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String>()
            strings.computeIfAbsent(key) { spyk(InMemoryPreference(key, null, default)) }
        }
        every { getBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Boolean>()
            booleans.computeIfAbsent(key) { InMemoryPreference(key, null, default) }
        }
    }
    private val appearance = CrystalAppearancePreferences(store)

    @Test
    fun atmosphereStateIsVersionedInstallLocalAndExcludedByTheBackupFilter() {
        check(appearance.atmosphereSeed().key() == Preference.appStateKey("crystal_atmosphere_seed_v1"))
        check(appearance.rainEncountered().key() == Preference.appStateKey("crystal_rain_encountered_v1"))
        check(Preference.isAppState(appearance.atmosphereSeed().key()))
        check(Preference.isAppState(appearance.rainEncountered().key()))
        check(appearance.atmosphereSeed().get().isEmpty())
        check(!appearance.rainEncountered().get())
    }

    @Test
    fun existingSeedIsPreservedIncludingUnsigned32BitBoundaryValues() {
        for (seed in listOf("0", "2147483648", "4294967295")) {
            appearance.atmosphereSeed().set(seed)
            check(appearance.getOrCreateAtmosphereSeed() == seed)
            check(appearance.atmosphereSeed().get() == seed)
        }
    }

    @Test
    fun concurrentFirstReadersInitializeExactlyOneUnsignedSeedOnTheSingleton() {
        val preference = appearance.atmosphereSeed()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = (0 until 24).map {
                pool.submit<String> {
                    check(start.await(5, TimeUnit.SECONDS))
                    appearance.getOrCreateAtmosphereSeed()
                }
            }
            start.countDown()
            val seeds = results.map { it.get(5, TimeUnit.SECONDS) }.toSet()
            check(seeds.size == 1)
            val seed = seeds.single()
            check(seed.toLong() in 0L..4_294_967_295L)
            check(preference.get() == seed)
            check(appearance.getOrCreateAtmosphereSeed() == seed)
            verify(exactly = 1) { preference.set(any()) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun decorativeFindingAndSeedInitializationDoNotChangeUserCosmeticsOrSettings() {
        appearance.pawsEnabled().set(true)
        appearance.silkEnabled().set(true)
        appearance.silkVariant().set("pearl")
        appearance.reduceMotion().set(true)
        appearance.soundEnabled().set(false)
        appearance.getOrCreateAtmosphereSeed()
        appearance.rainEncountered().set(true)
        check(appearance.pawsEnabled().get())
        check(appearance.silkEnabled().get())
        check(appearance.silkVariant().get() == "pearl")
        check(appearance.reduceMotion().get())
        check(!appearance.soundEnabled().get())
        check(appearance.rainEncountered().get())
        check(strings.keys == setOf("crystal_silk_variant", appearance.atmosphereSeed().key()))
        check(
            booleans.keys == setOf(
                "crystal_paws_enabled",
                "crystal_silk_enabled",
                "crystal_reduce_motion",
                "crystal_sound_enabled",
                appearance.rainEncountered().key(),
            ),
        )
    }
}
