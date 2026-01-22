# Aurora Manga Screen Fullscreen Redesign - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign the Aurora manga detail screen with fullscreen poster background, glassmorphism cards, and modern immersive UX.

**Architecture:** Component-based approach with 7 new reusable composables. Fixed background poster with scrollable content overlay. State management via LazyListState for scroll-based effects. Preserves all existing functionality while completely overhauling visual presentation.

**Tech Stack:** Jetpack Compose, Coil3 for image loading, Material3 icons, existing AuroraTheme color system

---

## File Structure

**New Files to Create:**
- `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/FullscreenPosterBackground.kt`
- `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaHeroContent.kt`
- `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/GlassmorphismCard.kt`
- `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaActionCard.kt`
- `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaInfoCard.kt`
- `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaChapterCardCompact.kt`
- `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/ChaptersHeader.kt`

**Files to Modify:**
- `app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt` (complete rewrite)

---

## Task 1: Create Aurora Components Directory

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/` (directory)

**Step 1: Create directory structure**

```bash
mkdir -p app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora
```

**Step 2: Verify directory exists**

Run: `ls -la app/src/main/java/eu/kanade/presentation/entries/manga/components/`

Expected: Directory `aurora` is listed

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/
git commit -m "feat(aurora): create aurora components directory structure" --allow-empty
```

---

## Task 2: Implement GlassmorphismCard Component

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/GlassmorphismCard.kt`

**Step 1: Create GlassmorphismCard composable**

```kotlin
package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable glassmorphism card component for Aurora theme.
 *
 * Provides semi-transparent background with gradient border effect
 * to create depth against poster backgrounds.
 */
@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    innerPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(innerPadding)
    ) {
        content()
    }
}
```

**Step 2: Build project to verify compilation**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/GlassmorphismCard.kt
git commit -m "feat(aurora): add glassmorphism card component"
```

---

## Task 3: Implement FullscreenPosterBackground Component

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/FullscreenPosterBackground.kt`

**Step 1: Create FullscreenPosterBackground composable**

```kotlin
package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover

/**
 * Fixed fullscreen poster background with scroll-based dimming and blur effects.
 *
 * @param manga Manga object containing cover information
 * @param scrollOffset Current scroll offset from LazyListState
 */
@Composable
fun FullscreenPosterBackground(
    manga: Manga,
    scrollOffset: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Calculate dim alpha based on scroll (0-400dp range)
    val dimAlpha by animateFloatAsState(
        targetValue = (scrollOffset / 400f).coerceIn(0f, 0.7f),
        animationSpec = spring(stiffness = 200f),
        label = "dimAlpha"
    )

    // Calculate blur amount (0-20dp range)
    val blurAmount = remember(scrollOffset) {
        (scrollOffset / 400f * 20f).coerceIn(0f, 20f).dp
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Poster image
        AsyncImage(
            model = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
                ImageRequest.Builder(context)
                    .data(manga.asMangaCover())
                    .placeholderMemoryCacheKey(manga.thumbnailUrl)
                    .crossfade(true)
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurAmount)
        )

        // Base gradient overlay (always present)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.3f to Color.Black.copy(alpha = 0.1f),
                        0.5f to Color.Black.copy(alpha = 0.4f),
                        0.7f to Color.Black.copy(alpha = 0.7f),
                        1.0f to Color.Black.copy(alpha = 0.9f)
                    )
                )
        )

        // Scroll-based dimming overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dimAlpha))
        )
    }
}
```

**Step 2: Build project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/FullscreenPosterBackground.kt
git commit -m "feat(aurora): add fullscreen poster background with scroll effects"
```

---

## Task 4: Implement MangaHeroContent Component

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaHeroContent.kt`

**Step 1: Create MangaHeroContent composable**

```kotlin
package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Hero content displayed at bottom of first screen over poster gradient.
 *
 * Contains: genre chips, manga title, compact stats, and Continue Reading button.
 */
@Composable
fun MangaHeroContent(
    manga: Manga,
    chapterCount: Int,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Genre chips (top 3)
        if (!manga.genre.isNullOrEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                manga.genre!!.take(3).forEach { genre ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent.copy(alpha = 0.25f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = genre,
                            color = colors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Manga title
        Text(
            text = manga.title,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            lineHeight = 40.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        // Compact statistics row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Rating (placeholder - using 4.9)
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFFACC15),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "4.9",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "•",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )

            // Status
            Text(
                text = manga.status.toString(),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "•",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )

            // Chapter count
            Text(
                text = pluralStringResource(
                    MR.plurals.num_chapters,
                    count = chapterCount,
                    chapterCount
                ),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Continue Reading button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.accent)
                .clickable(onClick = onContinueReading),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = colors.textOnAccent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(MR.strings.action_resume),
                    color = colors.textOnAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}
```

**Step 2: Build project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaHeroContent.kt
git commit -m "feat(aurora): add hero content component for first screen"
```

---

## Task 5: Implement MangaActionCard Component

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaActionCard.kt`

**Step 1: Create MangaActionCard composable**

```kotlin
package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Action buttons card with favorite, webview, tracking, and share options.
 */
@Composable
fun MangaActionCard(
    manga: Manga,
    trackingCount: Int,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            // Favorite button
            ActionButton(
                icon = {
                    Icon(
                        if (manga.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = if (manga.favorite)
                    stringResource(MR.strings.in_library)
                    else stringResource(MR.strings.add_to_library),
                onClick = onAddToLibraryClicked,
                modifier = Modifier.weight(1f)
            )

            // Webview button
            if (onWebViewClicked != null) {
                ActionButton(
                    icon = {
                        Icon(
                            Icons.Outlined.Public,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    label = "Source",
                    onClick = onWebViewClicked,
                    modifier = Modifier.weight(1f)
                )
            }

            // Tracking button
            if (onTrackingClicked != null) {
                ActionButton(
                    icon = {
                        Icon(
                            if (trackingCount == 0) Icons.Outlined.Sync else Icons.Outlined.Done,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    label = if (trackingCount == 0)
                        stringResource(MR.strings.manga_tracking_tab)
                        else pluralStringResource(MR.plurals.num_trackers, count = trackingCount, trackingCount),
                    onClick = onTrackingClicked,
                    modifier = Modifier.weight(1f)
                )
            }

            // Share button
            if (onShareClicked != null) {
                ActionButton(
                    icon = {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    label = stringResource(MR.strings.action_share),
                    onClick = onShareClicked,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(colors.accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 13.sp
        )
    }
}
```

**Step 2: Build project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaActionCard.kt
git commit -m "feat(aurora): add action buttons card component"
```

---

## Task 6: Implement MangaInfoCard Component

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaInfoCard.kt`

**Step 1: Create MangaInfoCard composable**

```kotlin
package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Info card containing description, stats, and genre tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaInfoCard(
    manga: Manga,
    chapterCount: Int,
    nextUpdate: Instant?,
    onTagSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }

    val nextUpdateDays = remember(nextUpdate) {
        if (nextUpdate != null) {
            val now = Instant.now()
            now.until(nextUpdate, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
        } else {
            null
        }
    }

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 20.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats grid (2x2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Rating
                StatItem(
                    value = "4.9",
                    label = stringResource(AYMR.strings.aurora_rating),
                    modifier = Modifier.weight(1f)
                )

                // Status
                StatItem(
                    value = manga.status.toString(),
                    label = stringResource(AYMR.strings.aurora_status),
                    modifier = Modifier.weight(1f)
                )

                // Chapters
                StatItem(
                    value = chapterCount.toString(),
                    label = pluralStringResource(MR.plurals.num_chapters, count = chapterCount, chapterCount),
                    modifier = Modifier.weight(1f)
                )

                // Next Update
                StatItem(
                    value = when (nextUpdateDays) {
                        null -> stringResource(MR.strings.not_applicable)
                        0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
                        else -> "$nextUpdateDays"
                    },
                    label = "Next Update",
                    modifier = Modifier.weight(1f)
                )
            }

            // Description
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Text(
                    text = manga.description ?: stringResource(AYMR.strings.aurora_no_description),
                    color = colors.textPrimary.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis
                )

                if ((manga.description?.length ?: 0) > 200) {
                    TextButton(
                        onClick = { descriptionExpanded = !descriptionExpanded }
                    ) {
                        Text(
                            text = if (descriptionExpanded)
                                stringResource(MR.strings.manga_info_collapse)
                                else stringResource(MR.strings.manga_info_expand),
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Genre tags
            if (!manga.genre.isNullOrEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    manga.genre!!.forEach { genre ->
                        SuggestionChip(
                            onClick = { onTagSearch(genre) },
                            label = {
                                Text(
                                    text = genre,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

**Step 2: Build project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaInfoCard.kt
git commit -m "feat(aurora): add info card with stats, description, and tags"
```

---

## Task 7: Implement ChaptersHeader Component

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/ChaptersHeader.kt`

**Step 1: Create ChaptersHeader composable**

```kotlin
package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Header for the chapters section with title and count badge.
 */
@Composable
fun ChaptersHeader(
    chapterCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(AYMR.strings.aurora_chapters_header),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accent.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = chapterCount.toString(),
                color = colors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
```

**Step 2: Build project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/ChaptersHeader.kt
git commit -m "feat(aurora): add chapters header component"
```

---

## Task 8: Implement MangaChapterCardCompact Component

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaChapterCardCompact.kt`

**Step 1: Create MangaChapterCardCompact composable**

```kotlin
package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.items.chapter.model.Chapter

/**
 * Compact chapter card with 40x40 thumbnail and minimal design.
 */
@Composable
fun MangaChapterCardCompact(
    manga: Manga,
    item: ChapterList.Item,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val chapter = item.chapter

    // Adjust opacity for read chapters
    val contentAlpha = if (chapter.read) 0.6f else 1f

    GlassmorphismCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        verticalPadding = 4.dp,
        innerPadding = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChapterClicked(chapter) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 40x40 thumbnail
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                AsyncImage(
                    model = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
                        ImageRequest.Builder(context)
                            .data(manga.asMangaCover())
                            .placeholderMemoryCacheKey(manga.thumbnailUrl)
                            .crossfade(true)
                            .size(40)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp),
                    alpha = contentAlpha
                )

                // Dark overlay for read chapters
                if (chapter.read) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    )
                }
            }

            // Chapter info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = chapter.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Meta info row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = colors.textSecondary.copy(alpha = contentAlpha),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Chapter ${chapter.chapterNumber.toInt()}",
                        fontSize = 12.sp,
                        color = colors.textSecondary.copy(alpha = contentAlpha)
                    )
                }

                // Progress bar for read chapters
                if (chapter.read) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.divider)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(colors.accent)
                        )
                    }
                }
            }

            // Actions column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Download indicator
                if (onDownloadChapter != null) {
                    ChapterDownloadIndicator(
                        enabled = true,
                        downloadStateProvider = { item.downloadState },
                        downloadProgressProvider = { item.downloadProgress },
                        onClick = { onDownloadChapter(listOf(item), it) },
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Read checkmark
                if (chapter.read) {
                    Icon(
                        Icons.Outlined.Done,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
```

**Step 2: Build project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaChapterCardCompact.kt
git commit -m "feat(aurora): add compact chapter card component"
```

---

## Task 9: Rewrite MangaScreenAurora Main Composable

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt`

**Step 1: Backup current implementation**

```bash
cp app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt.backup
git add app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt.backup
git commit -m "backup: save current MangaScreenAurora implementation"
```

**Step 2: Rewrite MangaScreenAuroraImpl composable**

Replace the entire content of `MangaScreenAurora.kt` with:

```kotlin
package eu.kanade.presentation.entries.manga

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.aurora.ChaptersHeader
import eu.kanade.presentation.entries.manga.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.manga.components.aurora.MangaActionCard
import eu.kanade.presentation.entries.manga.components.aurora.MangaChapterCardCompact
import eu.kanade.presentation.entries.manga.components.aurora.MangaHeroContent
import eu.kanade.presentation.entries.manga.components.aurora.MangaInfoCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaScreenAuroraImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onCoverClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSettingsClicked: (() -> Unit)?,
) {
    val manga = state.manga
    val chapters = state.chapterListItems
    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fixed background poster
        FullscreenPosterBackground(
            manga = manga,
            scrollOffset = scrollOffset
        )

        // Scrollable content
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = screenHeight,
                bottom = 100.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            // Action buttons card
            item {
                Spacer(modifier = Modifier.height(16.dp))
                MangaActionCard(
                    manga = manga,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onShareClicked = onShareClicked
                )
            }

            // Info card
            item {
                Spacer(modifier = Modifier.height(12.dp))
                MangaInfoCard(
                    manga = manga,
                    chapterCount = chapters.size,
                    nextUpdate = nextUpdate,
                    onTagSearch = onTagSearch
                )
            }

            // Chapters header
            item {
                Spacer(modifier = Modifier.height(20.dp))
                ChaptersHeader(chapterCount = chapters.size)
            }

            // Chapter list
            items(
                items = chapters,
                key = { it.chapter.id },
                contentType = { "chapter" }
            ) { item ->
                if (item is ChapterList.Item) {
                    MangaChapterCardCompact(
                        manga = manga,
                        item = item,
                        onChapterClicked = onChapterClicked,
                        onDownloadChapter = onDownloadChapter
                    )
                }
            }
        }

        // Hero content (fixed at bottom of first screen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 0.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            MangaHeroContent(
                manga = manga,
                chapterCount = chapters.size,
                onContinueReading = onContinueReading
            )
        }

        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                onClick = navigateUp,
                modifier = Modifier
                    .size(44.dp)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.accent
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Filter button
            IconButton(
                onClick = onFilterButtonClicked,
                modifier = Modifier.size(44.dp)
            ) {
                val filterTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f)
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = filterTint
                )
            }

            // Download menu
            if (onDownloadActionClicked != null) {
                var downloadExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { downloadExpanded = !downloadExpanded },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = colors.accent
                        )
                    }
                    DropdownMenu(
                        expanded = downloadExpanded,
                        onDismissRequest = { downloadExpanded = false }
                    ) {
                        EntryDownloadDropdownMenu(
                            expanded = true,
                            onDismissRequest = { downloadExpanded = false },
                            onDownloadClicked = { onDownloadActionClicked.invoke(it) },
                            isManga = true
                        )
                    }
                }
            }

            // More menu
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showMenu = !showMenu },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = colors.accent
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { androidx.compose.material3.Text(text = stringResource(MR.strings.action_webview_refresh)) },
                        onClick = {
                            onRefresh()
                            showMenu = false
                        },
                    )
                    if (onShareClicked != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { androidx.compose.material3.Text(text = stringResource(MR.strings.action_share)) },
                            onClick = {
                                onShareClicked()
                                showMenu = false
                            },
                        )
                    }
                    if (onSettingsClicked != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { androidx.compose.material3.Text(text = "Settings") },
                            onClick = {
                                onSettingsClicked()
                                showMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}
```

**Step 3: Build project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL (may have warnings about unused parameters - that's ok)

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt
git commit -m "feat(aurora): rewrite manga screen with fullscreen poster design"
```

---

## Task 10: Test Basic Functionality

**Files:**
- Test: Run the app and navigate to a manga detail screen

**Step 1: Build and install debug APK**

Run: `./gradlew installDebug`

Expected: BUILD SUCCESSFUL, APK installed on device/emulator

**Step 2: Manual testing checklist**

Test the following:
1. Open app and navigate to any manga
2. Verify fullscreen poster appears
3. Verify hero content (title, genres, button) visible at bottom
4. Tap "Continue Reading" button - should start reading
5. Scroll down - poster should dim and blur
6. Verify action card appears with 4 buttons
7. Verify info card with description and tags
8. Verify chapters header with count
9. Verify chapter list with compact cards
10. Tap a chapter - should open reader
11. Test back button
12. Test filter, download, and more menus

**Step 3: Document test results**

Create: `docs/testing/aurora-manga-screen-test-results.md`

```markdown
# Aurora Manga Screen Test Results

Date: 2026-01-22

## Test Environment
- Device: [Your device]
- Android Version: [Version]
- Build: Debug

## Test Results

### Visual Elements
- [ ] Fullscreen poster displays correctly
- [ ] Hero content visible at bottom
- [ ] Glassmorphism cards render properly
- [ ] Text is readable on all backgrounds

### Interactions
- [ ] Continue Reading button works
- [ ] Scroll triggers poster dimming/blur
- [ ] Action buttons respond to taps
- [ ] Chapter cards are clickable
- [ ] Menus open correctly

### Performance
- [ ] Scroll is smooth (60fps)
- [ ] No visible lag or stuttering
- [ ] Images load quickly

### Issues Found
[List any issues]

### Notes
[Any additional observations]
```

**Step 4: Commit test results**

```bash
git add docs/testing/aurora-manga-screen-test-results.md
git commit -m "test: add manual testing results for aurora manga screen"
```

---

## Task 11: Polish and Refinements

**Files:**
- Modify: Various aurora component files as needed

**Step 1: Add haptic feedback to Continue Reading button**

In `MangaHeroContent.kt`, update the Continue Reading button:

```kotlin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// Inside MangaHeroContent composable:
val haptic = LocalHapticFeedback.current

// Update button onClick:
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(colors.accent)
        .clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onContinueReading()
        },
    contentAlignment = Alignment.Center
) {
    // ... rest of button content
}
```

**Step 2: Add ripple effect to action buttons**

In `MangaActionCard.kt`, import ripple:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.ripple.rememberRipple

// Update ActionButton:
Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
        .clickable(
            onClick = onClick,
            indication = rememberRipple(
                bounded = true,
                color = colors.accent.copy(alpha = 0.2f)
            ),
            interactionSource = remember { MutableInteractionSource() }
        )
        .padding(horizontal = 4.dp)
) {
    // ... rest of content
}
```

**Step 3: Build and test**

Run: `./gradlew assembleDebug && ./gradlew installDebug`

Expected: Enhanced interactions work smoothly

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaHeroContent.kt
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaActionCard.kt
git commit -m "polish(aurora): add haptic feedback and ripple effects"
```

---

## Task 12: Add Animation Polish

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/FullscreenPosterBackground.kt`

**Step 1: Improve scroll animation timing**

Update the `animateFloatAsState` in `FullscreenPosterBackground.kt`:

```kotlin
import androidx.compose.animation.core.Spring

// Update dimAlpha animation:
val dimAlpha by animateFloatAsState(
    targetValue = (scrollOffset / 400f).coerceIn(0f, 0.7f),
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    ),
    label = "dimAlpha"
)
```

**Step 2: Build project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 3: Test scroll animation**

Verify that poster dimming is smooth and natural

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/FullscreenPosterBackground.kt
git commit -m "polish(aurora): improve scroll animation timing"
```

---

## Task 13: Handle Edge Cases

**Files:**
- Modify: Multiple component files

**Step 1: Handle missing manga data gracefully**

In `MangaHeroContent.kt`, add null checks:

```kotlin
// Update genre chips section:
if (!manga.genre.isNullOrEmpty() && manga.genre!!.isNotEmpty()) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        manga.genre!!.take(3).forEach { genre ->
            if (genre.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = genre,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
```

**Step 2: Handle empty chapter list**

In `MangaScreenAurora.kt`, add empty state for chapters:

```kotlin
// After chapters header item, add:
if (chapters.isEmpty()) {
    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(MR.strings.no_chapters_error),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
```

**Step 3: Build and test**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaHeroContent.kt
git add app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt
git commit -m "fix(aurora): handle edge cases for missing data"
```

---

## Task 14: Final Testing and Documentation

**Files:**
- Update: `docs/testing/aurora-manga-screen-test-results.md`
- Create: `docs/aurora-manga-screen-user-guide.md`

**Step 1: Run full regression testing**

Test all functionality end-to-end:
1. Various manga with different data (with/without genres, descriptions, etc.)
2. Different screen sizes (phone, tablet)
3. Different orientations (portrait, landscape)
4. Dark/light themes
5. Scroll performance
6. All button actions
7. Chapter downloads
8. Selection mode

**Step 2: Update test results document**

```bash
# Edit docs/testing/aurora-manga-screen-test-results.md with final results
git add docs/testing/aurora-manga-screen-test-results.md
git commit -m "test: update final test results for aurora manga screen"
```

**Step 3: Create user-facing documentation**

Create `docs/aurora-manga-screen-user-guide.md`:

```markdown
# Aurora Manga Screen - User Guide

## Overview

The Aurora manga detail screen features a modern, immersive design with:
- Fullscreen poster background
- Minimalist first-screen experience
- Glassmorphism card design
- Smooth scroll animations

## Navigation

### First Screen
- **Manga Title**: Displayed prominently at bottom
- **Genre Tags**: Up to 3 genre chips shown
- **Quick Stats**: Rating, status, chapter count
- **Continue Reading**: Large primary button to start/resume reading

### Scrollable Content
- **Action Buttons**: Favorite, Source, Tracking, Share
- **Info Card**: Detailed statistics, expandable description, all genre tags
- **Chapters**: Compact list with thumbnails, progress indicators

## Interactions

- **Tap Continue Reading**: Starts reading from last chapter
- **Scroll Down**: Reveals more information and chapter list
- **Tap Chapter**: Opens reader
- **Tap Genre Tag**: Searches for similar manga
- **Tap Action Button**: Performs respective action
- **Long Press Chapter**: Enters selection mode

## Visual Effects

- Poster dims and blurs as you scroll
- Glassmorphism cards create depth
- Smooth animations throughout
```

**Step 4: Commit documentation**

```bash
git add docs/aurora-manga-screen-user-guide.md
git commit -m "docs: add user guide for aurora manga screen"
```

---

## Task 15: Cleanup and Final Commit

**Files:**
- Remove: `app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt.backup`
- Update: `CHANGELOG.md` (if exists)

**Step 1: Remove backup file**

```bash
git rm app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt.backup
```

**Step 2: Run final build**

```bash
./gradlew clean
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL with no errors

**Step 3: Run linting**

```bash
./gradlew lintDebug
```

Expected: No critical issues

**Step 4: Run code formatting**

```bash
./gradlew spotlessApply
```

**Step 5: Final commit**

```bash
git add .
git commit -m "feat(aurora): complete fullscreen manga screen redesign

- Implement fullscreen poster background with scroll effects
- Add glassmorphism card components
- Create minimalist hero content layout
- Redesign chapter list with compact cards
- Add smooth animations and transitions
- Preserve all existing functionality

Closes #[issue-number] (if applicable)"
```

---

## Success Criteria

✅ **Functionality**
- All existing features work (favorite, tracking, download, etc.)
- Chapter reading works
- Navigation works
- Menus and dialogs work

✅ **Visual Design**
- Fullscreen poster displays correctly
- Glassmorphism cards render properly
- Text is readable on all backgrounds
- Animations are smooth

✅ **Performance**
- Scroll maintains 60fps
- Images load efficiently
- No memory leaks
- No crashes

✅ **Code Quality**
- Build succeeds without errors
- Lint passes without critical issues
- Code is well-documented
- Components are reusable

---

## Rollback Plan

If issues are discovered:

```bash
# Revert to backup
git checkout HEAD~15 -- app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt

# Or restore from backup file
cp app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt.backup \
   app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt
```

---

## Future Enhancements

After implementation is stable, consider:
- Add customization options (blur amount, gradient intensity)
- Implement chapter thumbnail previews (if source provides)
- Add reading statistics section
- Implement similar manga recommendations
- Add community features (reviews, comments)

---

**End of Implementation Plan**
