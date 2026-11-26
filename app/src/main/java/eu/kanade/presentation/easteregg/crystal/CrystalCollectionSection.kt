package eu.kanade.presentation.easteregg.crystal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.easteregg.crystal.CrystalMemorialManager
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.presentation.components.AuroraAmbientBackground
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraControlContainerColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun CrystalCollectionSection(modifier: Modifier = Modifier) {
    val appearance = rememberCrystalAppearance()
    if (!appearance.receipt.isEligible && !appearance.receipt.isGranted) return
    val colors = AuroraTheme.colors
    val navigator = LocalNavigator.currentOrThrow
    val profile = remember { Injekt.get<UserProfilePreferences>() }
    val avatar by profile.avatarUrl().collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<CrystalItem?>(null) }
    LaunchedEffect(appearance.receipt.isGranted) { if (!appearance.receipt.isGranted) selected = null }
    val openMemory = { navigator.push(CrystalMemorialScreen(replay = appearance.receipt.replayFromCollection())) }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(colors.background)) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(colors.surface),
            )
            CrystalLivingCornerHero(appearance.palette, appearance.reduced, occluded = selected != null)
        }
        Box(Modifier.fillMaxWidth().background(colors.background).padding(top = 14.dp)) {
            if (appearance.receipt.isGranted) {
                CrystalCollectionWarmTrail(
                    appearance.reduced,
                    selected != null,
                    Modifier.matchParentSize(),
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (appearance.receipt.isGranted) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(AYMR.strings.crystal_room_keepsakes),
                            Modifier.weight(1f),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                        Text(
                            stringResource(AYMR.strings.crystal_room_count, 5),
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CrystalInventoryCard(
                            CrystalItem.Frame,
                            appearance,
                            avatar,
                            Modifier.weight(1f).height(426.dp),
                        ) { selected = CrystalItem.Frame }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CrystalInventoryCard(
                                CrystalItem.Paws,
                                appearance,
                                avatar,
                                Modifier.fillMaxWidth().height(218.dp),
                            ) { selected = CrystalItem.Paws }
                            CrystalInventoryCard(
                                CrystalItem.Badge,
                                appearance,
                                avatar,
                                Modifier.fillMaxWidth().height(196.dp),
                            ) { selected = CrystalItem.Badge }
                        }
                    }
                    CrystalRoomTitleCard(appearance) { selected = CrystalItem.Title }
                    CrystalPaletteCard(appearance) { selected = CrystalItem.Silk }
                    val footerShape = RoundedCornerShape(20.dp)
                    val footerAccent = if (colors.isEInk) colors.textPrimary else Color(appearance.palette.gold)
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .crystalCollectionMaterial(footerShape, footerAccent, colors.isEInk)
                            .clickable(role = Role.Button, onClick = openMemory),
                        shape = footerShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (colors.isEInk) {
                                colors.cardBackground
                            } else {
                                lerp(colors.surface, Color(0xFF1F2837), 0.68f)
                            },
                        ),
                        border = BorderStroke(1.dp, footerAccent.copy(alpha = if (colors.isEInk) 1f else 0.22f)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CrystalGlyphCanvas(
                                CrystalGlyph.CatFace,
                                Modifier.size(46.dp),
                                appearance.palette,
                                animate = false,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    stringResource(AYMR.strings.crystal_replay),
                                    color = if (colors.isEInk) colors.textPrimary else Color(0xFFFAF0E1),
                                    fontFamily = CrystalFonts.Serif,
                                    fontSize = 20.sp,
                                )
                                Text(
                                    stringResource(AYMR.strings.crystal_memorial_title),
                                    color = if (colors.isEInk) colors.textSecondary else Color(0xFFC9D4D1),
                                    fontSize = 11.sp,
                                )
                            }
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(footerAccent.copy(alpha = 0.14f), CircleShape)
                                    .border(1.dp, footerAccent.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowForward,
                                    null,
                                    Modifier.size(15.dp),
                                    tint = footerAccent,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(AYMR.strings.crystal_gift_pending),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                    CrystalFelineInvitationButton(
                        stringResource(AYMR.strings.crystal_gift_open),
                        openMemory,
                        palette = appearance.palette,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    selected?.let { item -> CrystalItemDialog(item, appearance) { selected = null } }
}

private data class CrystalRoomMaterial(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
)

@Composable
private fun crystalRoomMaterial(item: CrystalItem, palette: CrystalPalette = CrystalPalette.Moon): CrystalRoomMaterial {
    val colors = AuroraTheme.colors
    if (colors.isEInk) {
        return CrystalRoomMaterial(
            colors.cardBackground,
            colors.textPrimary,
            colors.textSecondary,
            colors.accent,
        )
    }
    val gold = Color(palette.gold)
    val rose = Color(palette.rose)
    val mint = Color(palette.mint)
    val baseWall = colors.surface
    return when (item) {
        CrystalItem.Frame -> CrystalRoomMaterial(
            background = lerp(baseWall, Color(0xFF3A2E39), 0.62f),
            text = Color(0xFFFAF0E1),
            secondary = Color(0xFFD8C9BC),
            accent = gold,
        )
        CrystalItem.Paws -> CrystalRoomMaterial(
            background = lerp(baseWall, Color(0xFF233840), 0.68f),
            text = Color(0xFFF5ECE0),
            secondary = Color(0xFFC5D5D1),
            accent = lerp(gold, rose, 0.45f),
        )
        CrystalItem.Badge -> CrystalRoomMaterial(
            background = lerp(baseWall, Color(0xFF253142), 0.68f),
            text = Color(0xFFF5ECE0),
            secondary = Color(0xFFC7CFD9),
            accent = gold,
        )
        CrystalItem.Title -> CrystalRoomMaterial(
            background = lerp(baseWall, Color(0xFF212B3C), 0.74f),
            text = Color(0xFFF7EFE3),
            secondary = Color(0xFFC3D0D7),
            accent = gold,
        )
        else -> CrystalRoomMaterial(
            background = lerp(baseWall, Color(0xFF1D343B), 0.74f),
            text = Color(0xFFF4EDE2),
            secondary = Color(0xFFC1D4CE),
            accent = lerp(gold, mint, 0.38f),
        )
    }
}

@Composable
private fun crystalRoomTitle(item: CrystalItem): String = when (item) {
    CrystalItem.Frame -> stringResource(AYMR.strings.crystal_room_frame)
    CrystalItem.Paws -> stringResource(AYMR.strings.crystal_room_paws)
    CrystalItem.Badge -> stringResource(AYMR.strings.crystal_room_badge)
    else -> crystalItemTitle(item)
}

@Composable
private fun CrystalInventoryCard(
    item: CrystalItem,
    appearance: CrystalEquippedAppearance,
    avatar: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val material = crystalRoomMaterial(item, appearance.palette)
    val haptics = LocalAppHaptics.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val motion = !appearance.reduced && !colors.isEInk && rememberCrystalMotionPolicy().systemAnimations
    val scale by animateFloatAsState(if (pressed && motion) .985f else 1f, label = "roomKeepsakePress")
    val title = crystalRoomTitle(item)
    val equipped = appearance.equipped(item)
    val stateLabel = stringResource(if (equipped) AYMR.strings.crystal_equipped else AYMR.strings.crystal_owned)
    val shape = when (item) {
        CrystalItem.Frame -> RoundedCornerShape(7.dp, 20.dp, 20.dp, 20.dp)
        CrystalItem.Paws -> RoundedCornerShape(20.dp, 20.dp, 20.dp, 7.dp)
        else -> RoundedCornerShape(18.dp, 7.dp, 18.dp, 18.dp)
    }
    Card(
        modifier = modifier
            .crystalCollectionMaterial(shape, material.accent, colors.isEInk)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactions, indication = null, role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = title + ". " + stateLabel
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = material.background),
        border = BorderStroke(1.dp, material.accent.copy(alpha = if (colors.isEInk) 1f else .16f)),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item == CrystalItem.Frame) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).size(8.dp)
                        .background(if (colors.isEInk) colors.textSecondary else Color(0xFFAA8374), CircleShape),
                )
            }
            if (item == CrystalItem.Badge) {
                CrystalGlyphCanvas(
                    CrystalGlyph.Badge,
                    Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp),
                    appearance.palette,
                    animate = false,
                )
            }
            Column(
                Modifier.fillMaxSize().padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(if (item == CrystalItem.Frame) 9.dp else 6.dp),
            ) {
                val label = when (item) {
                    CrystalItem.Frame -> AYMR.strings.crystal_room_profile_label
                    CrystalItem.Paws -> AYMR.strings.crystal_room_paws_label
                    else -> AYMR.strings.crystal_room_badge_label
                }
                Text(stringResource(label), fontSize = 9.sp, color = material.secondary)
                when (item) {
                    CrystalItem.Frame -> {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(136.dp), contentAlignment = Alignment.Center) {
                            CrystalAvatarPreview(
                                avatar,
                                appearance.frame ?: CrystalFrameVariant.Fold,
                                appearance.palette,
                                !appearance.reduced,
                                Modifier.size(112.dp),
                            )
                        }
                    }
                    CrystalItem.Paws -> Row(
                        Modifier.fillMaxWidth().height(31.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(3) { index ->
                            CrystalGlyphCanvas(
                                CrystalGlyph.Paw,
                                Modifier.size(19.dp).graphicsLayer {
                                    rotationZ = -16f + index * 13f
                                    translationY = (1 - index) * 4f
                                    alpha = .55f + index * .2f
                                },
                                appearance.palette,
                                animate = false,
                            )
                        }
                    }
                    else -> Spacer(Modifier.height(32.dp))
                }
                Text(title, color = material.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    crystalItemDescription(item),
                    color = material.secondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = if (item == CrystalItem.Frame) 4 else 2,
                )
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(AYMR.strings.crystal_customize),
                        Modifier.weight(1f),
                        color = material.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (equipped) {
                        Box(
                            Modifier.size(15.dp).background(material.accent.copy(alpha = .15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Check, null, Modifier.size(10.dp), tint = material.accent) }
                    } else {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            null,
                            Modifier.size(12.dp),
                            tint = material.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrystalRoomTitleCard(appearance: CrystalEquippedAppearance, onClick: () -> Unit) {
    val material = crystalRoomMaterial(CrystalItem.Title, appearance.palette)
    val title = crystalItemTitle(CrystalItem.Title)
    val shape = RoundedCornerShape(19.dp, 5.dp, 19.dp, 19.dp)
    val colors = AuroraTheme.colors
    Card(
        Modifier.fillMaxWidth()
            .heightIn(min = 126.dp)
            .crystalCollectionMaterial(shape, material.accent, colors.isEInk)
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = material.background),
        border = BorderStroke(1.dp, material.accent.copy(alpha = .25f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(AYMR.strings.crystal_room_title_label), color = material.secondary, fontSize = 9.sp)
                Text(title, color = material.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(AYMR.strings.crystal_title_description),
                    color = material.secondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            CrystalTitlePill(appearance.palette, !appearance.reduced)
        }
    }
}

@Composable
private fun CrystalPaletteCard(appearance: CrystalEquippedAppearance, onClick: () -> Unit) {
    val material = crystalRoomMaterial(CrystalItem.Silk, appearance.palette)
    val palette = appearance.palette
    val shape = RoundedCornerShape(22.dp)
    val colors = AuroraTheme.colors
    val stateLabel =
        if (appearance.silk) crystalPaletteTitle(palette) else stringResource(AYMR.strings.crystal_palette_off)
    Card(
        Modifier.fillMaxWidth()
            .heightIn(min = 144.dp)
            .crystalCollectionMaterial(shape, material.accent, colors.isEInk)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = stateLabel },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = material.background),
        border = BorderStroke(1.dp, material.accent.copy(alpha = .18f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(AYMR.strings.crystal_room_silk_label), color = material.secondary, fontSize = 9.sp)
            Text(
                stringResource(AYMR.strings.crystal_room_palette),
                color = material.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(AYMR.strings.crystal_silk_description),
                color = material.secondary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(palette.gold, palette.rose, palette.mint).forEach { color ->
                        Box(
                            Modifier.size(28.dp, 20.dp)
                                .background(Color(color), RoundedCornerShape(5.dp, 8.dp, 5.dp, 5.dp))
                                .border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(5.dp)),
                        )
                    }
                }
                Text(stateLabel, color = material.accent, fontSize = 10.sp, maxLines = 2)
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    null,
                    Modifier.padding(start = 6.dp).size(13.dp),
                    tint = material.accent,
                )
            }
        }
    }
}

@Composable
private fun CrystalItemDialog(item: CrystalItem, appearance: CrystalEquippedAppearance, onDismiss: () -> Unit) {
    val manager = remember { Injekt.get<CrystalMemorialManager>() }
    val profile = remember { Injekt.get<UserProfilePreferences>() }
    val avatar by profile.avatarUrl().collectAsStateWithLifecycle()
    val backgrounds = remember { Injekt.get<UiPreferences>() }
    val baseStyle by backgrounds.specialBackgroundStyle().collectAsStateWithLifecycle()
    val baseAnimated by backgrounds.animatedAuroraBackground().collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = AuroraTheme.colors
    var frame by remember(item) { mutableStateOf(appearance.frame ?: CrystalFrameVariant.Fold) }
    var palette by remember(item) { mutableStateOf(CrystalPalette.fromKey(manager.appearance.silkVariant().get())) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val previewPalette = if (item == CrystalItem.Silk) palette else appearance.palette
    fun apply(enabled: Boolean) {
        if (busy) return
        val kind = item.cosmetic ?: return
        busy = true
        failed = false
        val variant = when (item) {
            CrystalItem.Frame -> frame.key
            CrystalItem.Silk -> palette.key
            else -> null
        }
        scope.launch {
            try {
                if (manager.setEquipped(kind, enabled, variant)) onDismiss() else failed = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
    val action = stringResource(
        when (item) {
            CrystalItem.Frame -> AYMR.strings.crystal_use_frame
            CrystalItem.Silk -> AYMR.strings.crystal_use_palette
            CrystalItem.Paws -> AYMR.strings.crystal_use_paws
            else -> AYMR.strings.crystal_apply
        },
    )
    AuroraFrostDialog(
        onDismiss = onDismiss,
        title = if (item == CrystalItem.Silk) {
            stringResource(AYMR.strings.crystal_palette_title)
        } else {
            crystalItemTitle(item)
        },
        closeLabel = stringResource(AYMR.strings.crystal_cancel),
        footer = {
            AuroraFrostConfirm(action, { apply(true) }, enabled = !busy, modifier = Modifier.weight(1f))
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(AYMR.strings.crystal_preview), fontSize = 10.sp, color = colors.textSecondary)
            if (item == CrystalItem.Silk) {
                CrystalPalettePreview(previewPalette, appearance.frame ?: CrystalFrameVariant.Fold)
            } else {
                val shape = RoundedCornerShape(22.dp)
                Box(
                    Modifier.fillMaxWidth().then(
                        if (item == CrystalItem.Paws) {
                            Modifier.height(220.dp).clip(shape)
                                .background(resolveAuroraControlContainerColor(colors))
                                .border(1.dp, resolveAuroraMoreCardBorderColor(colors), shape)
                        } else {
                            Modifier.heightIn(min = 156.dp)
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (item) {
                        CrystalItem.Frame -> CrystalAvatarPreview(
                            avatar,
                            frame,
                            previewPalette,
                            !appearance.reduced,
                            Modifier.size(132.dp),
                        )
                        CrystalItem.Paws -> AuroraAmbientBackground(
                            baseAnimated && !colors.isEInk,
                            baseStyle,
                            Modifier.fillMaxSize(),
                        ) {
                            CrystalPawsBackground(Modifier.matchParentSize(), previewPalette, !appearance.reduced)
                        }
                        CrystalItem.Title -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CrystalGlyphCanvas(
                                CrystalGlyph.Title,
                                Modifier.size(96.dp),
                                previewPalette,
                                animate = !appearance.reduced,
                            )
                            CrystalTitlePill(previewPalette, !appearance.reduced)
                        }
                        else -> CrystalGlyphCanvas(
                            item.glyph,
                            Modifier.size(132.dp),
                            previewPalette,
                            animate = !appearance.reduced,
                        )
                    }
                }
            }
            Text(
                if (item == CrystalItem.Frame) {
                    stringResource(AYMR.strings.crystal_frame_variants_hint)
                } else {
                    crystalItemDescription(item)
                },
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
            if (item == CrystalItem.Frame) {
                Text(
                    stringResource(AYMR.strings.crystal_kind_frame),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CrystalFrameVariant.entries.forEach { variant ->
                        CrystalChoiceCard(
                            crystalFrameTitle(variant),
                            frame == variant,
                            !busy,
                            { frame = variant },
                            Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            CrystalAvatarPreview(avatar, variant, previewPalette, false, Modifier.fillMaxSize())
                        }
                    }
                }
                CrystalScopeNote(stringResource(AYMR.strings.crystal_frame_scope))
            }
            if (item == CrystalItem.Silk) {
                Text(
                    stringResource(AYMR.strings.crystal_palette_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CrystalPalette.entries.forEach { variant ->
                        CrystalChoiceCard(
                            crystalPaletteTitle(variant),
                            palette == variant,
                            !busy,
                            { palette = variant },
                            Modifier.weight(1f).fillMaxHeight(),
                        ) { CrystalPaletteSwatches(variant) }
                    }
                }
                CrystalScopeNote(stringResource(AYMR.strings.crystal_palette_scope))
                if (colors.isEInk) CrystalScopeNote(stringResource(AYMR.strings.crystal_palette_eink_hint))
            }
            if (appearance.equipped(item)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CrystalStateBadge(stringResource(AYMR.strings.crystal_equipped), true)
                    Spacer(Modifier.weight(1f))
                    CrystalSecondaryButton(
                        stringResource(
                            when (item) {
                                CrystalItem.Frame -> AYMR.strings.crystal_remove_frame
                                CrystalItem.Silk -> AYMR.strings.crystal_remove_palette
                                CrystalItem.Paws -> AYMR.strings.crystal_remove_paws
                                else -> AYMR.strings.crystal_remove
                            },
                        ),
                        { apply(false) },
                        enabled = !busy,
                    )
                }
            }
            if (failed) Text(stringResource(AYMR.strings.crystal_save_failed), color = colors.error, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CrystalScopeNote(text: String) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        Modifier.fillMaxWidth().background(resolveAuroraControlContainerColor(colors), shape)
            .border(1.dp, resolveAuroraMoreCardBorderColor(colors), shape).padding(12.dp),
        color = colors.textSecondary,
        fontSize = 10.sp,
        lineHeight = 17.sp,
    )
}
