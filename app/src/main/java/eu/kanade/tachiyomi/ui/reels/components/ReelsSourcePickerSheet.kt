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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    mixSourceStatuses: Map<Long, MixSourceStatus> = emptyMap(),
    onToggleMixSourceExcluded: (Long) -> Unit = {},
    onSetMixBurstSize: (Int) -> Unit = {},
) {
    var showMixConfig by remember { mutableStateOf(false) }
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

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isMixSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = stringResource(MR.strings.reels_selected),
                                            tint = if (isEInk) MaterialTheme.colorScheme.onSurface else accent,
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .size(20.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { showMixConfig = !showMixConfig },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Tune,
                                            contentDescription = stringResource(MR.strings.reels_mix_customize),
                                            tint = if (showMixConfig && !isEInk) {
                                                accent
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = showMixConfig) {
                                MixConfigPanel(
                                    mixBurstSize = mixBurstSize,
                                    isEInk = isEInk,
                                    accent = accent,
                                    selectedBorder = selectedBorder,
                                    unselectedBorder = unselectedBorder,
                                    onSetMixBurstSize = onSetMixBurstSize,
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
                                if (showMixConfig && showMixEntry) {
                                    onToggleMixSourceExcluded(source.id)
                                } else {
                                    onSelectSource(source.id)
                                    onDismissRequest()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
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
    isEInk: Boolean,
    accent: Color,
    selectedBorder: Color,
    unselectedBorder: Color,
    onSetMixBurstSize: (Int) -> Unit,
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
        Text(
            text = stringResource(MR.strings.reels_mix_sources_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
