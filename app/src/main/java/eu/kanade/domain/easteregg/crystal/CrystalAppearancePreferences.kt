package eu.kanade.domain.easteregg.crystal

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import kotlin.random.Random

/** Optional cosmetics never replace the chosen theme/background; paws are an independent additive layer. */
class CrystalAppearancePreferences(private val store: PreferenceStore) {
    fun pawsEnabled() = store.getBoolean("crystal_paws_enabled", false)
    fun silkEnabled() = store.getBoolean("crystal_silk_enabled", false)
    fun silkVariant() = store.getString("crystal_silk_variant", "moon")
    fun reduceMotion() = store.getBoolean("crystal_reduce_motion", false)
    fun soundEnabled() = store.getBoolean("crystal_sound_enabled", true)

    // Decorative install-local state is intentionally omitted from preference backups.
    fun atmosphereSeed(): Preference<String> =
        store.getString(Preference.appStateKey("crystal_atmosphere_seed_v1"), "")

    fun rainEncountered(): Preference<Boolean> =
        store.getBoolean(Preference.appStateKey("crystal_rain_encountered_v1"), false)

    /** Serialized on the existing DI singleton so concurrent first readers share one uint32 string. */
    @Synchronized
    fun getOrCreateAtmosphereSeed(): String {
        val preference = atmosphereSeed()
        val existing = preference.get()
        if (existing.isNotEmpty()) return existing
        return Random.nextInt().toUInt().toString().also(preference::set)
    }

    companion object {
        val FRAME_KEYS = setOf("crystal_fold", "crystal_point", "crystal_thread")
        val PALETTE_KEYS = setOf("moon", "dusk", "pearl")
    }
}

enum class CrystalCosmetic { FRAME, PAWS, TITLE, BADGE, SILK }
