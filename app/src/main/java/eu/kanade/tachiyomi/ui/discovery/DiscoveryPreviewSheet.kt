package eu.kanade.tachiyomi.ui.discovery

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.rememberSupportsBlurBehind
import eu.kanade.tachiyomi.data.discovery.DiscoveryCoverRecovery
import eu.kanade.tachiyomi.data.discovery.DiscoveryMeta
import eu.kanade.tachiyomi.ui.home.LocalHomeHazeState
import eu.kanade.tachiyomi.ui.home.discoveryReasonText
import eu.kanade.tachiyomi.ui.home.toHomeHubDiscoveryItem
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

/**
 * Aurora Preview Bottom Sheet (прототип P3, V1): матовый лист с обложкой в ореоли,
 * бейджем совпадения, жанрами, синопсисом (лениво из AniList) и действиями.
 */
@Composable
internal fun DiscoveryPreviewSheet(
    item: DiscoverySuggestion,
    meta: DiscoveryMeta?,
    isMetaLoading: Boolean,
    coverMediaType: DiscoveryMediaType,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onFind: () -> Unit,
    onHide: () -> Unit,
    hazeState: HazeState? = LocalHomeHazeState.current,
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark
    val context = LocalContext.current
    val appHaptics = LocalAppHaptics.current
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, item.coverUrl, item.provider, coverReloadTick) {
        buildAuroraCoverImageRequest(
            context,
            discoveryCoverData(
                coverMediaType,
                item.provider,
                item.coverUrl,
                DiscoveryCoverRecovery.get(coverMediaType, item.cleanTitle),
            ),
        )
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Portrait)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    val homeItem = remember(item) { item.toHomeHubDiscoveryItem() }
    val similarTemplate = stringResource(AYMR.strings.for_you_reason_similar)
    val trendTemplate = stringResource(AYMR.strings.for_you_reason_trending)
    val nextTemplate = stringResource(AYMR.strings.for_you_reason_season_next)
    val rawReason = discoveryReasonText(homeItem, similarTemplate, trendTemplate, nextTemplate)
    val displayReason = when (item.rowType) {
        DiscoveryRowType.TASTE -> stringResource(AYMR.strings.for_you_badge_taste)
        else -> rawReason
    }
    val tasteGenres = if (item.rowType == DiscoveryRowType.TASTE) {
        item.reason?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    } else {
        emptyList()
    }
    val metaGenres = meta?.genres.orEmpty()
    val allGenres = (tasteGenres + metaGenres)
        .map { it.trim().replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
        .filter { it.isNotBlank() }
        .distinct()

    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val supportsBlurBehind = rememberSupportsBlurBehind(colors.isEInk)

    DisposableEffect(window, supportsBlurBehind) {
        val w = window
        if (w != null && supportsBlurBehind) {
            w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            w.attributes = w.attributes.apply { blurBehindRadius = 40 }
        }
        onDispose {
            if (w != null && supportsBlurBehind) {
                w.attributes = w.attributes.apply { blurBehindRadius = 0 }
                w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(
            alpha = when {
                colors.isEInk -> 0.50f
                supportsBlurBehind -> 0.25f
                isDark -> 0.45f
                else -> 0.30f
            },
        ),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
        shape = sheetShape,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sheetShape)
                .then(
                    if (hazeState != null && !colors.isEInk) {
                        Modifier.hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style = HazeBlurStyle {
                                backgroundColor(colors.background)
                                colorEffects(
                                    listOf(
                                        HazeColorEffect.tint(
                                            if (isDark) {
                                                Color(0xFF141824).copy(alpha = 0.76f)
                                            } else {
                                                Color.White.copy(alpha = 0.82f)
                                            },
                                        ),
                                    ),
                                )
                                blurRadius(28.dp)
                                noiseFactor(0.10f)
                            },
                        )
                    } else {
                        Modifier.background(
                            when {
                                colors.isEInk -> colors.surface
                                isDark -> Color(0xFF141824).copy(alpha = 0.94f)
                                else -> Color.White.copy(alpha = 0.95f)
                            },
                        )
                    },
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.28f else 0.40f),
                            Color.White.copy(alpha = 0.04f),
                        ),
                    ),
                    shape = sheetShape,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Внутренний drag handle внутри стеклянного листа
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = if (isDark) 0.28f else 0.38f)),
                )

                // Чистая обложка с радиальным ореолом акцента (без чипов на постере)
                Box(
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(190.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(colors.accent.copy(alpha = 0.35f), Color.Transparent),
                                ),
                            ),
                    )
                    Box(
                        Modifier
                            .width(135.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.cardBackground)
                            .border(
                                1.dp,
                                Color.White.copy(alpha = if (isDark) 0.14f else 0.20f),
                                RoundedCornerShape(18.dp),
                            ),
                    ) {
                        AsyncImage(
                            model = coverRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = rememberAuroraPosterColorFilter(),
                            modifier = Modifier.matchParentSize(),
                            error = fallbackPainter,
                            fallback = fallbackPainter,
                        )
                    }
                }

                // Заголовок и альтернативное название
                Text(
                    text = item.title,
                    color = colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (!meta?.altTitle.isNullOrBlank()) {
                    Text(
                        text = meta.altTitle,
                        color = colors.textSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
                    )
                }

                // Сигнальный чип причины рекомендации (в стиле FeedTabs)
                if (displayReason != null) {
                    val chipShape = RoundedCornerShape(50)
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(chipShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.accent.copy(alpha = if (isDark) 0.28f else 0.20f),
                                        colors.accent.copy(alpha = if (isDark) 0.10f else 0.06f),
                                    ),
                                ),
                                chipShape,
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isDark) 0.85f else 0.95f),
                                        colors.accent.copy(alpha = 0.35f),
                                    ),
                                ),
                                chipShape,
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = displayReason,
                            color = if (colors.isEInk) {
                                colors.textOnAccent
                            } else if (isDark) {
                                colors.textPrimary
                            } else {
                                colors.accent
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Жанровые чипы (в стиле второстепенных чипов ленты «Для вас»)
                if (allGenres.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val genreShape = RoundedCornerShape(50)
                        val tint = if (isDark) Color.White else Color.Black
                        allGenres.take(4).forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(genreShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                tint.copy(alpha = if (isDark) 0.08f else 0.05f),
                                                tint.copy(alpha = if (isDark) 0.02f else 0.01f),
                                            ),
                                        ),
                                        genreShape,
                                    )
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                tint.copy(alpha = if (isDark) 0.22f else 0.18f),
                                                tint.copy(alpha = if (isDark) 0.05f else 0.04f),
                                            ),
                                        ),
                                        genreShape,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = genre,
                                    color = colors.textSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // Блок описания / синопсиса
                when {
                    isMetaLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background((if (isDark) Color.White else Color.Black).copy(alpha = 0.04f))
                                .border(
                                    1.dp,
                                    (if (isDark) Color.White else Color.Black).copy(alpha = 0.06f),
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accent,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(AYMR.strings.for_you_meta_loading),
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    meta?.description != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background((if (isDark) Color.White else Color.Black).copy(alpha = 0.05f))
                                .border(
                                    1.dp,
                                    (if (isDark) Color.White else Color.Black).copy(alpha = 0.07f),
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable {
                                    appHaptics.tap()
                                    isDescriptionExpanded = !isDescriptionExpanded
                                }
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                        ) {
                            Text(
                                text = meta.description,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                maxLines = if (isDescriptionExpanded) 25 else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Кнопки действий: Вариант 1 — Невесомые стеклянные капсулы (Airy Glass Pills)
                val pillShape = RoundedCornerShape(50)
                val tint = if (isDark) Color.White else Color.Black

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Primary: «В библиотеку» (Airy Glass Pill)
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .height(42.dp)
                            .clip(pillShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.accent.copy(alpha = if (isDark) 0.26f else 0.22f),
                                        colors.accent.copy(alpha = if (isDark) 0.10f else 0.06f),
                                    ),
                                ),
                                pillShape,
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.70f),
                                        colors.accent.copy(alpha = 0.35f),
                                    ),
                                ),
                                pillShape,
                            )
                            .clickable {
                                appHaptics.tap()
                                onAdd()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = if (isDark) colors.textPrimary else colors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = stringResource(AYMR.strings.for_you_add_library),
                                color = if (isDark) colors.textPrimary else colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Secondary: «В источниках» (Airy Glass Pill)
                    Box(
                        modifier = Modifier
                            .weight(1.05f)
                            .height(42.dp)
                            .clip(pillShape)
                            .background(
                                tint.copy(alpha = if (isDark) 0.06f else 0.08f),
                                pillShape,
                            )
                            .border(
                                1.dp,
                                tint.copy(alpha = if (isDark) 0.14f else 0.20f),
                                pillShape,
                            )
                            .clickable {
                                appHaptics.tap()
                                onFind()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = stringResource(AYMR.strings.for_you_sheet_find),
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Tertiary: «Скрыть» (Airy Circle Button)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                tint.copy(alpha = if (isDark) 0.04f else 0.06f),
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                tint.copy(alpha = if (isDark) 0.10f else 0.15f),
                                CircleShape,
                            )
                            .clickable {
                                appHaptics.tap()
                                onHide()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = stringResource(AYMR.strings.for_you_hide),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
