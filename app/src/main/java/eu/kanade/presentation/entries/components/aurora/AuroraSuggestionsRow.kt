package eu.kanade.presentation.entries.components.aurora

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionState
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val CardWidth = 100.dp
private val CardHeight = 150.dp // 2:3 ratio
private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun AuroraSuggestionsRow(
    state: SuggestionState,
    onSuggestionClick: (SuggestionItem) -> Unit,
    onOpenSuggestions: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is SuggestionState.Idle || state is SuggestionState.Disabled) return
    if (state is SuggestionState.Success && state.items.isEmpty()) return

    val colors = AuroraTheme.colors

    val scrimBrush = if (!colors.isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.02f),
                Color.Transparent,
            ),
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (scrimBrush != null) Modifier.background(scrimBrush) else Modifier,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AuroraCoverSectionHeader(
                title = stringResource(MR.strings.suggestions_similar_titles),
                icon = Icons.Default.AutoAwesome,
                showChevron = true,
                onChevronClick = onOpenSuggestions,
                trailingContent = {
                    if (state is SuggestionState.Error) {
                        Text(
                            text = stringResource(MR.strings.action_retry),
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRetryClick)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .background(
                                    if (colors.isDark) {
                                        Color.White.copy(alpha = 0.1f)
                                    } else {
                                        colors.accent.copy(alpha = 0.15f)
                                    },
                                ),
                        )
                    }
                },
            )

            when (state) {
                is SuggestionState.Loading -> AuroraSuggestionsShimmer()
                is SuggestionState.Success -> AuroraSuggestionsContent(
                    items = state.items,
                    onSuggestionClick = onSuggestionClick,
                )
                is SuggestionState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(
                                if (colors.isDark) {
                                    Color.White.copy(alpha = 0.05f)
                                } else {
                                    Color.Black.copy(
                                        alpha = 0.05f,
                                    )
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .padding(12.dp),
                    ) {
                        Text(
                            text = state.message ?: stringResource(MR.strings.suggestions_empty_state),
                            color = if (colors.isDark) Color.White.copy(alpha = 0.6f) else colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
                is SuggestionState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = state.message,
                            color = if (colors.isDark) Color.White.copy(alpha = 0.8f) else colors.textPrimary,
                            fontSize = 13.sp,
                        )
                    }
                }
                else -> Unit
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AuroraSuggestionsContent(
    items: List<SuggestionItem>,
    onSuggestionClick: (SuggestionItem) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.providerId ?: item.providerUrl }) { index, item ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(item.providerId ?: item.providerUrl) {
                delay(index * 50L) // stagger reveal
                visible = true
            }
            AuroraSuggestionCard(
                item = item,
                visible = visible,
                onClick = { onSuggestionClick(item) },
            )
        }
    }
}

@Composable
private fun AuroraSuggestionCard(
    item: SuggestionItem,
    visible: Boolean,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "card_alpha",
    )
    Box(
        modifier = Modifier
            .width(CardWidth)
            .graphicsLayer { this.alpha = alpha }
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = getCoverModel(item),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(CardWidth, CardHeight),
        )
        // Title gradient overlay
        Box(
            modifier = Modifier
                .size(CardWidth, CardHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        startY = 60f,
                    ),
                ),
        )
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

private fun getCoverModel(item: SuggestionItem): Any? {
    val url = item.thumbnailUrl ?: return null
    if (item.mediaType != eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.NOVEL) return url

    if (eu.kanade.tachiyomi.source.novel.NovelPluginImage.isSupported(url)) {
        return eu.kanade.tachiyomi.source.novel.NovelPluginImage(url)
    }

    val sourceId = item.providerId?.substringBefore(":")?.toLongOrNull() ?: -1L
    return tachiyomi.domain.entries.novel.model.NovelCover(
        novelId = -1L,
        sourceId = sourceId,
        isNovelFavorite = false,
        url = url,
        lastModified = 0L,
    )
}

@Composable
private fun AuroraSuggestionsShimmer() {
    val colors = AuroraTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer_alpha",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(5) {
            Box(
                modifier = Modifier
                    .width(CardWidth)
                    .height(CardHeight)
                    .clip(CardShape)
                    .background(
                        if (colors.isDark) {
                            Color.White.copy(alpha = shimmerAlpha)
                        } else {
                            Color.Black.copy(
                                alpha =
                                shimmerAlpha * 0.12f,
                            )
                        },
                    ),
            )
        }
    }
}
