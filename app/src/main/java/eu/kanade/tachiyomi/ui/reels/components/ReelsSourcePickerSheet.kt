package eu.kanade.tachiyomi.ui.reels.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.AnimeSource
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
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                items(sources, key = { it.id }) { source ->
                    val isSelected = source.id == currentSourceId
                    val bg = if (isSelected) {
                        AuroraTheme.colors.accent.copy(alpha = 0.15f)
                    } else {
                        Color.Transparent
                    }
                    val border = if (isSelected) {
                        AuroraTheme.colors.accent.copy(alpha = 0.4f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg, RoundedCornerShape(12.dp))
                            .border(1.dp, border, RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectSource(source.id)
                                onDismissRequest()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                val circleBg = if (isSelected) {
                                    AuroraTheme.colors.accent
                                } else {
                                    Color.White.copy(alpha = 0.15f)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(circleBg, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val initial = source.name.firstOrNull()?.uppercaseChar()?.toString() ?: "S"
                                    Text(
                                        text = initial,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                            }

                            val textColor = if (isSelected) {
                                AuroraTheme.colors.accent
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor,
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(MR.strings.reels_selected),
                                tint = AuroraTheme.colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
