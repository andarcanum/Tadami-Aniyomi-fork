package tachiyomi.data.achievement

import android.content.SharedPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.model.UserProfile
import tachiyomi.domain.achievement.repository.UserProfileRepository

class UnlockableManagerTest {

    private val stubRepo = object : UserProfileRepository {
        override fun getProfile(userId: String) = kotlinx.coroutines.flow.flowOf(UserProfile.createDefault())
        override suspend fun getProfileSync(userId: String) = UserProfile.createDefault()
        override suspend fun saveProfile(profile: UserProfile) {}
        override suspend fun updateXP(userId: String, totalXP: Int, currentXP: Int, level: Int, xpToNextLevel: Int) {}
        override suspend fun addTitle(userId: String, title: String) {}
        override suspend fun addBadge(userId: String, badge: String) {}
        override suspend fun removeBadge(userId: String, badge: String) {}
        override suspend fun addTheme(userId: String, themeId: String) {}
        override suspend fun removeTheme(userId: String, themeId: String) {}
        override suspend fun updateAchievementCounts(userId: String, unlocked: Int, total: Int) {}
        override suspend fun deleteProfile(userId: String) {}
    }
    private val stubProfileManager = UserProfileManager(stubRepo)

    @Test
    fun `unlockAchievementRewards unlocks all persisted reward ids`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs, stubProfileManager)

        val achievement = Achievement(
            id = "secret_crybaby",
            type = AchievementType.SECRET,
            category = AchievementCategory.SECRET,
            title = "Crybaby",
            rewards = listOf(
                Reward(
                    type = RewardType.SPECIAL,
                    id = "special_background_petal_storm",
                    title = "Petal Storm",
                ),
            ),
        )

        manager.unlockAchievementRewards(achievement)

        manager.isUnlockableUnlocked("special_background_petal_storm") shouldBe true
    }

    @Test
    fun `canonical reward ids from achievement are unlocked`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs, stubProfileManager)

        val achievement = Achievement(
            id = "secret_hall_unlocked",
            type = AchievementType.SECRET,
            category = AchievementCategory.SECRET,
            title = "S-rank Hall",
            rewards = listOf(
                Reward(
                    type = RewardType.THEME,
                    id = "theme_SAKURA_NOIR",
                    title = "Sakura Noir",
                ),
            ),
        )

        manager.unlockAchievementRewards(achievement)

        manager.isUnlockableUnlocked("theme_SAKURA_NOIR") shouldBe true
    }

    @Test
    fun `rewards from achievement object are unlocked for secret_onepiece`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs, stubProfileManager)

        val achievement = Achievement(
            id = "secret_onepiece",
            type = AchievementType.SECRET,
            category = AchievementCategory.SECRET,
            title = "Pirate King",
            rewards = listOf(
                Reward(
                    type = RewardType.THEME,
                    id = "theme_ONYX_GOLD",
                    title = "Onyx Gold",
                ),
                Reward(
                    type = RewardType.SPECIAL,
                    id = "avatar_frame_prismatic",
                    title = "Prismatic Frame",
                ),
            ),
        )

        manager.unlockAchievementRewards(achievement)

        manager.isUnlockableUnlocked("theme_ONYX_GOLD") shouldBe true
        manager.isUnlockableUnlocked("avatar_frame_prismatic") shouldBe true
    }

    @Test
    fun `achievement without rewards does not unlock unrelated items`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs, stubProfileManager)

        val achievement = Achievement(
            id = "secret_goku",
            type = AchievementType.SECRET,
            category = AchievementCategory.SECRET,
            title = "Goku",
        )

        manager.unlockAchievementRewards(achievement)

        manager.isUnlockableUnlocked("avatar_frame_hologram") shouldBe false
    }

    @Test
    fun `persisted reward ids are unlocked for secret_goku`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs, stubProfileManager)

        val achievement = Achievement(
            id = "secret_goku",
            type = AchievementType.SECRET,
            category = AchievementCategory.SECRET,
            title = "Goku",
            rewards = listOf(
                Reward(type = RewardType.AURA, id = "aura_matrix", title = "Matrix Aura"),
                Reward(type = RewardType.THEME, id = "theme_NEBULA_TIDE", title = "Nebula Tide"),
            ),
        )

        manager.unlockAchievementRewards(achievement)

        manager.isUnlockableUnlocked("aura_matrix") shouldBe true
        manager.isUnlockableUnlocked("theme_NEBULA_TIDE") shouldBe true
    }

    @Test
    fun `getUnlockableNameRes returns correct StringResource reference`() {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs, stubProfileManager)

        val goldThemeRes = manager.getUnlockableNameRes("theme_achievement_gold")
        goldThemeRes shouldBe tachiyomi.i18n.MR.strings.unlockable_theme_achievement_gold

        val invalidRes = manager.getUnlockableNameRes("nonexistent_reward")
        invalidRes shouldBe null
    }
}

/**
 * Tiny in-memory [SharedPreferences] implementation used for unit tests.
 * Implements only the methods exercised by [UnlockableManager].
 */
internal class InMemorySharedPreferences : SharedPreferences {
    private val backing = linkedMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = backing.toMap()

    override fun getString(key: String, defValue: String?): String? =
        backing[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST")
        (backing[key] as? Set<String>)
            ?: defValues

    override fun getInt(key: String, defValue: Int): Int =
        (backing[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (backing[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (backing[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (backing[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = backing.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }

    private inner class InMemoryEditor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply {
            removals += key
            pending.remove(key)
        }
        override fun clear() = apply {
            clearAll = true
            pending.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val changed = mutableSetOf<String>()
            if (clearAll) {
                backing.keys.forEach { changed += it }
                backing.clear()
            }
            removals.forEach {
                if (backing.remove(it) != null) changed += it
            }
            pending.forEach { (k, v) ->
                if (backing[k] != v) {
                    changed += k
                }
                backing[k] = v
            }
            changed.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) }
            }
        }
    }
}
