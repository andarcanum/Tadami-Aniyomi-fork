package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import eu.kanade.tachiyomi.ui.reels.ReelsQualityFailure
import eu.kanade.tachiyomi.ui.reels.ReelsQualityItemState
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

enum class ReelsQualityDialogMode { VIDEO, GLOBAL }

/**
 * Approved UX A: one frost surface for both the current reel and the Telegram-wide default.
 * The dialog only renders available variants; it never invents a resolution or a codec.
 */
@Composable
fun ReelsVideoQualityDialog(
    mode: ReelsQualityDialogMode,
    itemState: ReelsQualityItemState?,
    global: ReelsQualityPreference,
    onSelect: (ReelsQualityPreference) -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val title = when (mode) {
        ReelsQualityDialogMode.VIDEO -> stringResource(AYMR.strings.reels_quality_video)
        ReelsQualityDialogMode.GLOBAL -> stringResource(AYMR.strings.reels_quality_default)
    }
    AuroraFrostDialog(
        onDismiss = onDismiss,
        title = title,
        footer = {
            AuroraFrostCancel(
                label = stringResource(AYMR.strings.reels_quality_cancel),
                onClick = onDismiss,
            )
        },
    ) {
        if (mode == ReelsQualityDialogMode.GLOBAL) {
            Text(
                text = stringResource(AYMR.strings.reels_quality_global_description),
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            GLOBAL_PREFERENCES.forEach { preference ->
                QualityRow(
                    label = globalLabel(preference),
                    selected = preference == global,
                    onClick = { onSelect(preference) },
                )
            }
            return@AuroraFrostDialog
        }

        when {
            itemState == null ||
                itemState.catalogStatus == eu.kanade.tachiyomi.ui.reels.ReelsQualityCatalogStatus.LOADING -> {
                Text(
                    text = stringResource(AYMR.strings.reels_quality_loading),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            itemState.options.isEmpty() -> {
                Text(
                    text = stringResource(AYMR.strings.reels_quality_switch_failed),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            else -> {
                val actual = itemState.actual?.decision?.variant?.id
                val seen = mutableSetOf<String>()
                itemState.options
                    .sortedWith(
                        compareBy<FeedVideoQualityVariant> { if (it.isSource) 1 else 0 }
                            .thenByDescending { it.shortEdge() ?: 0 },
                    )
                    .forEach { variant ->
                        val key = if (variant.isSource) "source" else "res:${variant.shortEdge()}"
                        if (!seen.add(key)) return@forEach
                        val preference = if (variant.isSource) {
                            ReelsQualityPreference.Source
                        } else {
                            variant.shortEdge()?.let { ReelsQualityPreference.Resolution(it) }
                                ?: return@forEach
                        }
                        QualityRow(
                            label = variantLabel(variant),
                            selected = variant.id == actual,
                            onClick = { onSelect(preference) },
                        )
                    }
                if (itemState.usingGlobal.not()) {
                    Spacer(Modifier.height(6.dp))
                    QualityRow(
                        label = stringResource(AYMR.strings.reels_quality_use_default),
                        selected = false,
                        onClick = onUseDefault,
                    )
                }
                if (itemState.catalogStatus == eu.kanade.tachiyomi.ui.reels.ReelsQualityCatalogStatus.SOURCE_ONLY) {
                    Text(
                        text = stringResource(AYMR.strings.reels_quality_source_only),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                failureText(itemState.failure)?.let { text ->
                    Text(
                        text = text,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(
                if (selected) colors.accent.copy(alpha = if (colors.isEInk) 1f else 0.18f) else Color.Transparent,
                shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.18f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = if (selected && colors.isEInk) Color.White else colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Text(
                text = "✓",
                color = if (colors.isEInk) Color.White else colors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun variantLabel(variant: FeedVideoQualityVariant): String {
    if (variant.isSource) {
        val shortEdge = variant.shortEdge()
        return if (shortEdge != null) {
            stringResource(AYMR.strings.reels_quality_source_resolution, "${shortEdge}p")
        } else {
            stringResource(AYMR.strings.reels_quality_missing_source)
        }
    }
    return "${variant.shortEdge() ?: "?"}p"
}

@Composable
private fun globalLabel(preference: ReelsQualityPreference): String = when (preference) {
    ReelsQualityPreference.Source -> stringResource(AYMR.strings.reels_quality_source)
    is ReelsQualityPreference.Resolution -> "${preference.shortEdge}p"
}

@Composable
private fun failureText(failure: ReelsQualityFailure?): String? = when (failure) {
    is ReelsQualityFailure.Retry,
    ReelsQualityFailure.NeedsAuth,
    ReelsQualityFailure.ContextChanged,
    -> stringResource(AYMR.strings.reels_quality_switch_failed)
    ReelsQualityFailure.Unavailable -> stringResource(AYMR.strings.reels_quality_unavailable)
    ReelsQualityFailure.Unsupported -> null
    null -> null
}

private fun FeedVideoQualityVariant.shortEdge(): Int? {
    val width = width ?: return null
    val height = height ?: return null
    if (width <= 0 || height <= 0) return null
    return minOf(width, height)
}

private val GLOBAL_PREFERENCES = listOf(
    ReelsQualityPreference.Resolution(1080),
    ReelsQualityPreference.Resolution(720),
    ReelsQualityPreference.Resolution(480),
    ReelsQualityPreference.Source,
)
