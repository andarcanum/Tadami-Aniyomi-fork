package eu.kanade.presentation.easteregg.crystal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraControlContainerColor
import eu.kanade.presentation.theme.resolveAuroraControlSelectedContentColor
import eu.kanade.presentation.theme.resolveAuroraSelectionBorderColor
import eu.kanade.presentation.theme.resolveAuroraSelectionContainerColor
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun CrystalTreasuryEntry(appearance: CrystalEquippedAppearance, onClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val ink = if (colors.isEInk) colors.textPrimary else Color(0xFFF7E6CE)
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(CRYSTAL_LIVING_PREVIEW_ASPECT_RATIO).clip(shape)
            .background(resolveAuroraMoreCardContainerColor(colors))
            .border(1.dp, resolveAuroraMoreCardBorderColor(colors), shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        CrystalLivingCornerPreview(appearance.palette, Modifier.fillMaxSize())
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 16.dp)
                .width(maxWidth * .44f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(AYMR.strings.crystal_living_title),
                color = ink,
                fontFamily = CrystalFonts.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 23.sp,
                lineHeight = 24.sp,
                maxLines = 2,
            )
            Box(
                Modifier
                    .width(30.dp)
                    .height(1.2.dp)
                    .background(ink.copy(alpha = if (colors.isEInk) .65f else .48f), RoundedCornerShape(2.dp)),
            )
            Text(
                stringResource(AYMR.strings.crystal_room_entry_hint),
                color = ink.copy(alpha = .85f),
                fontSize = 11.sp,
                lineHeight = 14.5.sp,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 11.dp)
                .background(
                    if (colors.isEInk) colors.surface else Color(0xFF0F1F28).copy(alpha = 0.62f),
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (colors.isEInk) colors.textPrimary else ink.copy(alpha = 0.26f),
                    CircleShape,
                )
                .padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(AYMR.strings.crystal_room_entry_action),
                color = ink,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                null,
                Modifier.size(13.dp),
                tint = ink,
            )
        }
    }
}

@Composable
internal fun CrystalAvatarPreview(
    avatar: String,
    frame: CrystalFrameVariant,
    palette: CrystalPalette,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val photo = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
        if (avatar.isNotBlank()) {
            AsyncImage(avatar, null, photo, contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.AccountCircle, null, photo, tint = colors.textSecondary)
        }
        CrystalAvatarFrame(frame, palette, Modifier.matchParentSize(), animate)
    }
}

@Composable
internal fun CrystalPalettePreview(palette: CrystalPalette, frame: CrystalFrameVariant, modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    val profile = remember { Injekt.get<UserProfilePreferences>() }
    val avatar by profile.avatarUrl().collectAsStateWithLifecycle()
    val name by profile.name().collectAsStateWithLifecycle()
    val defaultName = stringResource(AYMR.strings.treasury_default_user_name)
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.fillMaxWidth().heightIn(min = 180.dp).clip(shape)
            .background(resolveAuroraControlContainerColor(colors))
            .border(1.dp, resolveAuroraMoreCardBorderColor(colors), shape),
        contentAlignment = Alignment.Center,
    ) {
        CrystalSilkAtmosphere(palette, animate = false, modifier = Modifier.matchParentSize())
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CrystalAvatarPreview(avatar, frame, palette, false, Modifier.size(92.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        name.trim().ifEmpty { defaultName },
                        Modifier.weight(1f, fill = false),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CrystalGlyphCanvas(CrystalGlyph.Badge, Modifier.size(25.dp), palette, animate = false)
                }
                CrystalTitlePill(palette, animate = false)
                Text(
                    stringResource(AYMR.strings.crystal_preview_profile),
                    fontSize = 10.sp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** Shared visual choice, used by both frame geometry and palette swatches. */
@Composable
internal fun CrystalChoiceCard(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val border = if (selected) {
        if (colors.isEInk) resolveAuroraSelectionBorderColor(colors) else colors.accent.copy(alpha = 0.45f)
    } else {
        resolveAuroraMoreCardBorderColor(colors)
    }
    val contentColor = if (selected) {
        resolveAuroraControlSelectedContentColor(colors)
    } else {
        colors.textPrimary
    }
    Box(
        modifier.heightIn(min = 112.dp).clip(shape)
            .background(
                if (selected) {
                    resolveAuroraSelectionContainerColor(colors)
                } else {
                    resolveAuroraControlContainerColor(colors)
                },
            )
            .border(1.dp, border, shape)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(58.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) { preview() }
            Text(
                label,
                color = contentColor,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(15.dp)
                    .background(if (colors.isEInk) colors.textPrimary else colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(10.dp), tint = colors.textOnAccent)
            }
        }
    }
}

@Composable
internal fun CrystalPaletteSwatches(palette: CrystalPalette, modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    Row(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(palette.gold, palette.rose, palette.mint).forEach { tone ->
            Box(
                Modifier.size(16.dp).background(
                    if (colors.isEInk) colors.textSecondary else androidx.compose.ui.graphics.Color(tone),
                    CircleShape,
                ),
            )
        }
    }
}

@Composable
internal fun CrystalStateBadge(label: String, equipped: Boolean, modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    Row(
        modifier.background(
            if (equipped) resolveAuroraSelectionContainerColor(colors) else resolveAuroraControlContainerColor(colors),
            RoundedCornerShape(50),
        ).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (equipped) Icon(Icons.Default.Check, null, Modifier.size(11.dp), tint = colors.accent)
        Text(label, fontSize = 9.sp, color = if (equipped && !colors.isEInk) colors.accent else colors.textSecondary)
    }
}

@Composable
internal fun CrystalSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AuroraTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (colors.isEInk) colors.textPrimary else colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = resolveAuroraControlContainerColor(colors),
            contentColor = colors.textPrimary,
        ),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
