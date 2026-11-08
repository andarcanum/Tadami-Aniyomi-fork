package eu.kanade.presentation.easteregg.crystal

import androidx.compose.runtime.Composable
import eu.kanade.domain.easteregg.crystal.CrystalCosmetic
import tachiyomi.domain.easteregg.crystal.CrystalMemorialIds
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

internal enum class CrystalItem(val unlockId: String, val glyph: CrystalGlyph, val cosmetic: CrystalCosmetic?) {
    Relic(CrystalMemorialIds.RELIC, CrystalGlyph.Relic, null),
    Frame(CrystalMemorialIds.FRAME, CrystalGlyph.Frame, CrystalCosmetic.FRAME),
    Paws(CrystalMemorialIds.PAWS, CrystalGlyph.Paw, CrystalCosmetic.PAWS),
    Title(CrystalMemorialIds.TITLE, CrystalGlyph.Title, CrystalCosmetic.TITLE),
    Badge(CrystalMemorialIds.BADGE, CrystalGlyph.Badge, CrystalCosmetic.BADGE),
    Silk(CrystalMemorialIds.SILK, CrystalGlyph.Palette, CrystalCosmetic.SILK),
}

@Composable
internal fun crystalItemTitle(item: CrystalItem): String = stringResource(
    when (item) {
        CrystalItem.Relic -> AYMR.strings.unlockable_special_crystal_relic
        CrystalItem.Frame -> AYMR.strings.unlockable_avatar_frame_crystal_ears
        CrystalItem.Paws -> AYMR.strings.unlockable_special_background_crystal_warm_paws
        CrystalItem.Title -> AYMR.strings.unlockable_title_crystal_light_keeper
        CrystalItem.Badge -> AYMR.strings.unlockable_home_badge_crystal_memory_paw
        CrystalItem.Silk -> AYMR.strings.unlockable_profile_crystal_moon_silk
    },
)

@Composable
internal fun crystalItemDescription(item: CrystalItem): String = stringResource(
    when (item) {
        CrystalItem.Relic -> AYMR.strings.crystal_relic_description
        CrystalItem.Frame -> AYMR.strings.crystal_frame_description
        CrystalItem.Paws -> AYMR.strings.crystal_paws_description
        CrystalItem.Title -> AYMR.strings.crystal_title_description
        CrystalItem.Badge -> AYMR.strings.crystal_badge_description
        CrystalItem.Silk -> AYMR.strings.crystal_silk_description
    },
)

@Composable
internal fun crystalFrameTitle(variant: CrystalFrameVariant) = stringResource(
    when (variant) {
        CrystalFrameVariant.Fold -> AYMR.strings.crystal_variant_fold
        CrystalFrameVariant.Point -> AYMR.strings.crystal_variant_point
        CrystalFrameVariant.Thread -> AYMR.strings.crystal_variant_thread
    },
)

@Composable
internal fun crystalPaletteTitle(palette: CrystalPalette) = stringResource(
    when (palette) {
        CrystalPalette.Moon -> AYMR.strings.crystal_palette_moon
        CrystalPalette.Dusk -> AYMR.strings.crystal_palette_dusk
        CrystalPalette.Pearl -> AYMR.strings.crystal_palette_pearl
    },
)
