package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.ui.reels.MixSourceStatus
import eu.kanade.tachiyomi.ui.reels.ReelsMultiSourceCoordinator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentHashMapOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReelsSourcePickerSheet(
    onDismissRequest: () -> Unit,
    sources: ImmutableList<AnimeSource>,
    currentSourceId: Long,
    onSelectSource: (Long) -> Unit,
    icons: ImmutableMap<Long, ImageBitmap> = persistentHashMapOf(),
    allSourcesId: Long = SourcePreferences.REELS_ALL_SOURCES_ID,
    excludedMixSourceIds: Set<String> = emptySet(),
    mixBurstSize: Int = 2,
    mixUnseenOnly: Boolean = true,
    mixSourceStatuses: Map<Long, MixSourceStatus> = emptyMap(),
    onToggleMixSourceExcluded: (Long) -> Unit = {},
    onSetMixBurstSize: (Int) -> Unit = {},
    onToggleMixUnseenOnly: () -> Unit = {},
) {
    var showMixConfig by remember(currentSourceId, allSourcesId) {
        mutableStateOf(currentSourceId == allSourcesId)
    }
    val isEInk = AuroraTheme.colors.isEInk
    val accent = AuroraTheme.colors.accent
    val unselectedBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isEInk) 1f else 0.45f)
    val selectedBg = if (isEInk) Color.Transparent else accent.copy(alpha = 0.15f)
    val selectedBorder = if (isEInk) MaterialTheme.colorScheme.onSurface else accent.copy(alpha = 0.45f)
    val showMixEntry = sources.size > 1
    val activeMixCount = remember(sources, excludedMixSourceIds) {
        sources.count { it.id.toString() !in excludedMixSourceIds }
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(MR.strings.reels_sources_section_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showMixEntry) {
                    item(key = "mix_all_sources") {
                        val isMixSelected = currentSourceId == allSourcesId
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isMixSelected) selectedBg else Color.Transparent)
                                    .border(
                                        width = if (isMixSelected && isEInk) 2.dp else 1.dp,
                                        color = if (isMixSelected) selectedBorder else unselectedBorder,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable {
                                        onSelectSource(allSourcesId)
                                        onDismissRequest()
                                    }
                                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                if (isEInk) {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                } else {
                                                    accent.copy(alpha = 0.2f)
                                                },
                                                RoundedCornerShape(9.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Shuffle,
                                            contentDescription = null,
                                            tint = if (isEInk) MaterialTheme.colorScheme.onSurface else accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(MR.strings.reels_mix_title),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMixSelected &&
                                                !isEInk
                                            ) {
                                                accent
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(
                                                MR.strings.reels_mix_subtitle,
                                                activeMixCount,
                                                sources.size,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (isMixSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = stringResource(MR.strings.reels_selected),
                                            tint = if (isEInk) MaterialTheme.colorScheme.onSurface else accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    val configChipBg = when {
                                        showMixConfig && isEInk -> MaterialTheme.colorScheme.onSurface
                                        showMixConfig && !isEInk -> accent.copy(alpha = 0.2f)
                                        isEInk -> Color.Transparent
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                    }
                                    val configChipBorder = if (showMixConfig) selectedBorder else unselectedBorder
                                    val configChipFg = when {
                                        showMixConfig && isEInk -> MaterialTheme.colorScheme.surface
                                        showMixConfig && !isEInk -> accent
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(configChipBg)
                                            .border(1.dp, configChipBorder, RoundedCornerShape(8.dp))
                                            .clickable { showMixConfig = !showMixConfig }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Tune,
                                            contentDescription = stringResource(MR.strings.reels_mix_customize),
                                            tint = configChipFg,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Text(
                                            text = stringResource(MR.strings.reels_mix_configure_short),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = configChipFg,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = showMixConfig) {
                                MixConfigPanel(
                                    mixBurstSize = mixBurstSize,
                                    mixUnseenOnly = mixUnseenOnly,
                                    isEInk = isEInk,
                                    accent = accent,
                                    selectedBorder = selectedBorder,
                                    unselectedBorder = unselectedBorder,
                                    onSetMixBurstSize = onSetMixBurstSize,
                                    onToggleMixUnseenOnly = onToggleMixUnseenOnly,
                                )
                            }
                        }
                    }
                }

                items(sources, key = { it.id }) { source ->
                    val isSelected = source.id == currentSourceId
                    val isMixIncluded = source.id.toString() !in excludedMixSourceIds
                    val statusNote = when (mixSourceStatuses[source.id]) {
                        MixSourceStatus.NEEDS_AUTH -> stringResource(MR.strings.reels_mix_status_needs_auth)
                        MixSourceStatus.COOLDOWN -> stringResource(MR.strings.reels_mix_status_cooldown)
                        MixSourceStatus.CIRCUIT_OPEN -> stringResource(MR.strings.reels_mix_status_unavailable)
                        MixSourceStatus.EXHAUSTED -> stringResource(MR.strings.reels_mix_status_exhausted)
                        else -> null
                    }
                    val rowBg = if (isSelected && !showMixConfig) selectedBg else Color.Transparent
                    val rowBorder = if (isSelected && !showMixConfig) selectedBorder else unselectedBorder

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowBg, RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected && !showMixConfig && isEInk) 2.dp else 1.dp,
                                color = rowBorder,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                onSelectSource(source.id)
                                onDismissRequest()
                            }
                            .padding(
                                start = 14.dp,
                                end = if (showMixConfig && showMixEntry) 6.dp else 14.dp,
                                top = 10.dp,
                                bottom = 10.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (showMixConfig && showMixEntry && !isMixIncluded) 0.55f else 1f),
                        ) {
                            val icon = icons[source.id]
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp)),
                                )
                            } else {
                                val circleBg = if (isSelected && !showMixConfig && !isEInk) {
                                    accent
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(circleBg, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val initial = source.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                                    Text(
                                        text = initial,
                                        color = if (isSelected && !showMixConfig && !isEInk) {
                                            Color.Black
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                val textColor = if (isSelected && !showMixConfig && !isEInk) {
                                    accent
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected &&
                                        !showMixConfig
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (statusNote != null) {
                                    Text(
                                        text = statusNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        if (showMixConfig && showMixEntry) {
                            Checkbox(
                                checked = isMixIncluded,
                                onCheckedChange = { onToggleMixSourceExcluded(source.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = if (isEInk) MaterialTheme.colorScheme.onSurface else accent,
                                    checkmarkColor = if (isEInk) MaterialTheme.colorScheme.surface else Color.Black,
                                ),
                            )
                        } else if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(MR.strings.reels_selected),
                                tint = if (isEInk) MaterialTheme.colorScheme.onSurface else accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MixConfigPanel(
    mixBurstSize: Int,
    mixUnseenOnly: Boolean,
    isEInk: Boolean,
    accent: Color,
    selectedBorder: Color,
    unselectedBorder: Color,
    onSetMixBurstSize: (Int) -> Unit,
    onToggleMixUnseenOnly: () -> Unit,
) {
    val containerColor = if (isEInk) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    }
    val burstRange = ReelsMultiSourceCoordinator.MIN_BURST_SIZE..ReelsMultiSourceCoordinator.MAX_BURST_SIZE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, unselectedBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(MR.strings.reels_mix_burst_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                burstRange.forEach { count ->
                    val active = mixBurstSize == count
                    val chipBg = when {
                        active && isEInk -> MaterialTheme.colorScheme.onSurface
                        active -> accent
                        isEInk -> Color.Transparent
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val chipText = when {
                        active && isEInk -> MaterialTheme.colorScheme.surface
                        active -> Color.Black
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBg)
                            .border(
                                width = 1.dp,
                                color = if (active) selectedBorder else unselectedBorder,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { onSetMixBurstSize(count) }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = chipText,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleMixUnseenOnly)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(MR.strings.reels_mix_unseen_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = mixUnseenOnly,
                onCheckedChange = { onToggleMixUnseenOnly() },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(MR.strings.reels_mix_sources_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(MR.strings.reels_mix_sources_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
