package eu.kanade.presentation.easteregg.crystal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.domain.easteregg.crystal.CrystalAppearancePreferences
import eu.kanade.domain.easteregg.crystal.CrystalMemorialManager
import eu.kanade.domain.ui.UserProfilePreferences
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.domain.easteregg.crystal.CrystalMemorialState
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal data class CrystalEquippedAppearance(
    val receipt: CrystalMemorialState,
    val frame: CrystalFrameVariant?,
    val paws: Boolean,
    val title: Boolean,
    val badge: Boolean,
    val silk: Boolean,
    val palette: CrystalPalette,
    val reduced: Boolean,
    val sound: Boolean,
) {
    fun equipped(item: CrystalItem) = when (item) {
        CrystalItem.Relic -> receipt.isGranted
        CrystalItem.Frame -> frame != null
        CrystalItem.Paws -> paws
        CrystalItem.Title -> title
        CrystalItem.Badge -> badge
        CrystalItem.Silk -> silk
    }
}

@Composable
internal fun rememberCrystalAppearance(): CrystalEquippedAppearance {
    if (LocalInspectionMode.current) {
        return CrystalEquippedAppearance(
            CrystalMemorialState(), null, false, false, false, false, CrystalPalette.Moon, true, false,
        )
    }
    val manager = remember { Injekt.get<CrystalMemorialManager>() }
    val profile = remember { Injekt.get<UserProfilePreferences>() }
    val prefs = manager.appearance
    val receipt by manager.state.collectAsStateWithLifecycle(initialValue = CrystalMemorialState())
    val frameKey by profile.avatarFrameStyle().collectAsStateWithLifecycle()
    val titleKey by profile.profileTitle().collectAsStateWithLifecycle()
    val badgeKey by profile.homeBadgeStyle().collectAsStateWithLifecycle()
    val paws by prefs.pawsEnabled().collectAsStateWithLifecycle()
    val silk by prefs.silkEnabled().collectAsStateWithLifecycle()
    val palette by prefs.silkVariant().collectAsStateWithLifecycle()
    val reduced by prefs.reduceMotion().collectAsStateWithLifecycle()
    val sound by prefs.soundEnabled().collectAsStateWithLifecycle()
    val owned = receipt.isGranted
    return CrystalEquippedAppearance(
        receipt,
        if (owned &&
            frameKey in CrystalAppearancePreferences.FRAME_KEYS
        ) {
            CrystalFrameVariant.fromKey(frameKey)
        } else {
            null
        },
        owned && paws, owned && titleKey == CrystalMemorialIds.TITLE, owned && badgeKey == CrystalMemorialIds.BADGE,
        owned && silk, if (owned && silk) CrystalPalette.fromKey(palette) else CrystalPalette.Moon, reduced, sound,
    )
}
