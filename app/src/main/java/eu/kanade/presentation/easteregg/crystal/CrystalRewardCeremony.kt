package eu.kanade.presentation.easteregg.crystal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.delay
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@Composable
internal fun CrystalRewardCeremony(
    firstGrant: Boolean,
    reduced: Boolean,
    onDismiss: () -> Unit,
    onTreasury: () -> Unit,
) {
    CrystalArtTheme {
        val colors = AuroraTheme.colors
        val policy = rememberCrystalMotionPolicy()
        val motion = !reduced && !colors.isEInk && policy.systemAnimations
        val counter = remember(firstGrant, motion) { Animatable(if (firstGrant && motion) 0f else 999f) }
        LaunchedEffect(firstGrant, motion) {
            if (firstGrant && motion) {
                delay(150)
                counter.animateTo(999f, tween(1100))
            }
        }
        val earned = stringResource(if (firstGrant) AYMR.strings.crystal_earned else AYMR.strings.crystal_owned)
        AuroraFrostDialog(
            onDismiss = onDismiss,
            title = stringResource(AYMR.strings.crystal_ceremony_title),
            bottomAligned = true,
            closeLabel = stringResource(AYMR.strings.crystal_cancel),
            surfaceOverlay = remember(colors.accent) {
                Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.12f), Color.Transparent))
            },
            headerContent = {
                Text(
                    stringResource(AYMR.strings.crystal_memorial_title).uppercase(),
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            footer = {
                CrystalFelineInvitationButton(
                    label = stringResource(AYMR.strings.crystal_room_invite),
                    onClick = onTreasury,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CrystalGlyphCanvas(CrystalGlyph.Relic, Modifier.size(132.dp), animate = motion)
                Text(
                    stringResource(AYMR.strings.crystal_ceremony_title),
                    fontFamily = CrystalFonts.Serif,
                    fontSize = 30.sp,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(
                        if (firstGrant) {
                            AYMR.strings.crystal_ceremony_description
                        } else {
                            AYMR.strings.crystal_ceremony_repeat
                        },
                    ),
                    Modifier.padding(top = 6.dp, bottom = 16.dp),
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                CrystalItem.entries.chunked(3).forEachIndexed { row, items ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items.forEachIndexed { column, item ->
                            val reveal = remember(item, motion) { Animatable(if (motion) 0f else 1f) }
                            LaunchedEffect(item, motion) {
                                if (motion) {
                                    delay((row * 3 + column) * 65L)
                                    reveal.animateTo(1f, tween(460))
                                }
                            }
                            Column(
                                Modifier.weight(1f).heightIn(min = 100.dp)
                                    .graphicsLayer {
                                        alpha = reveal.value
                                        translationY = (1 - reveal.value) * 14.dp.toPx()
                                    }
                                    .background(
                                        if (colors.isEInk) colors.surface else colors.cardBackground.copy(alpha = 0.6f),
                                        RoundedCornerShape(17.dp),
                                    )
                                    .border(
                                        1.dp,
                                        if (colors.isEInk) colors.textPrimary else colors.accent.copy(alpha = 0.24f),
                                        RoundedCornerShape(17.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CrystalGlyphCanvas(item.glyph, Modifier.size(42.dp), animate = motion)
                                Text(
                                    crystalItemTitle(item),
                                    Modifier.padding(top = 7.dp),
                                    color = colors.textPrimary,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(earned, Modifier.weight(1f), color = colors.textSecondary, fontSize = 12.sp)
                    Text(
                        (if (firstGrant) "+" else "") + counter.value.roundToInt(),
                        Modifier.clearAndSetSemantics { contentDescription = earned + ": 999" },
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 27.sp,
                    )
                }
            }
        }
    }
}
