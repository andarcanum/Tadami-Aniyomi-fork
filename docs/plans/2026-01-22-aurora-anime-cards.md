# Aurora Anime Cards Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement Aurora-themed glassmorphism cards for anime screens, matching the existing manga Aurora design with anime-specific adaptations (dubbing selection, episode-specific stats).

**Architecture:** Create a parallel Aurora implementation for anime (`AnimeScreenAurora.kt`) following the same structure as `MangaScreenAurora.kt`. The design uses a fullscreen poster background with scrollable glassmorphism cards containing anime information, actions, and episodes. Hero content appears at the bottom of the first screen and fades on scroll.

**Tech Stack:** Kotlin, Jetpack Compose, existing Aurora theme system, existing glassmorphism components

---

## Task 1: Create AnimeInfoCard Component

**Goal:** Build the main information card showing stats (Rating, Type, Status, Next Update), description, and genre tags.

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeInfoCard.kt`
- Reference: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaInfoCard.kt`
- Reference: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/GlassmorphismCard.kt`

**Step 1: Create AnimeInfoCard file with package and imports**

Create the file with:

```kotlin
package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.SAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.temporal.ChronoUnit
```

**Step 2: Implement AnimeInfoCard composable**

Add the main composable function:

```kotlin
/**
 * Info card containing description, stats, and genre tags for anime.
 * Displays: Rating (placeholder), Type (placeholder), Status, Next Update
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeInfoCard(
    anime: Anime,
    episodeCount: Int,
    nextUpdate: Instant?,
    onTagSearch: (String) -> Unit,
    descriptionExpanded: Boolean,
    genresExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onToggleGenres: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    val nextUpdateDays = remember(nextUpdate) {
        if (nextUpdate != null) {
            val now = Instant.now()
            now.until(nextUpdate, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
        } else {
            null
        }
    }

    // Determine if anime is completed
    val isCompleted = anime.status.toInt() in listOf(
        SAnime.COMPLETED,
        SAnime.PUBLISHING_FINISHED,
        SAnime.CANCELLED,
    )

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 20.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Stats grid - Rating, Type, Status, optionally Next Update
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isCompleted) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
            ) {
                // Rating (placeholder for Shikimori integration)
                StatItem(
                    value = stringResource(MR.strings.not_applicable),
                    label = stringResource(AYMR.strings.aurora_rating),
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                )

                // Type (placeholder for Shikimori integration)
                StatItem(
                    value = stringResource(MR.strings.not_applicable),
                    label = stringResource(AYMR.strings.aurora_type),
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                )

                // Status
                StatItem(
                    value = AnimeStatusFormatter.formatStatus(anime.status),
                    label = stringResource(AYMR.strings.aurora_status),
                    modifier = if (isCompleted) Modifier else Modifier.weight(1f),
                )

                // Next Update - only show if not completed
                if (!isCompleted) {
                    StatItem(
                        value = when (nextUpdateDays) {
                            null -> stringResource(MR.strings.not_applicable)
                            0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
                            else -> "$nextUpdateDays д"
                        },
                        label = "Обновление",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Description
            Column(
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        alignment = Alignment.TopStart,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = anime.description ?: stringResource(AYMR.strings.aurora_no_description),
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if ((anime.description?.length ?: 0) > 200) {
                        Icon(
                            imageVector = if (descriptionExpanded) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { onToggleDescription() },
                        )
                    }
                }
            }

            // Genre tags - collapsible
            if (!anime.genre.isNullOrEmpty()) {
                Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            alignment = Alignment.TopStart,
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            val genresToShow = if (genresExpanded) anime.genre!! else anime.genre!!.take(3)
                            genresToShow.forEach { genre ->
                                // Compact genre chip
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.accent.copy(alpha = 0.15f))
                                        .clickable { onTagSearch(genre) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 11.sp,
                                        color = colors.accent,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        if (anime.genre!!.size > 3) {
                            Icon(
                                imageVector = if (genresExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable { onToggleGenres() },
                            )
                        }
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
        modifier = modifier,
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

**Step 3: Create AnimeStatusFormatter helper object**

Add at the bottom of the same file:

```kotlin
/**
 * Formatter for anime status values.
 */
object AnimeStatusFormatter {
    fun formatStatus(status: Long): String {
        return when (status.toInt()) {
            SAnime.ONGOING -> "Онгоинг"
            SAnime.COMPLETED -> "Завершён"
            SAnime.LICENSED -> "Лицензирован"
            SAnime.PUBLISHING_FINISHED -> "Выпуск завершён"
            SAnime.CANCELLED -> "Отменён"
            SAnime.ON_HIATUS -> "На паузе"
            else -> "Неизвестно"
        }
    }
}
```

**Step 4: Add missing string resources**

Check if these strings exist in `i18n-aniyomi` module. If not, add placeholders in code comments:

```kotlin
// TODO: Add to i18n-aniyomi/src/commonMain/resources/MR/strings.xml:
// <string name="aurora_rating">Рейтинг</string>
// <string name="aurora_type">Тип</string>
// <string name="aurora_status">Статус</string>
// <string name="aurora_no_description">Описание отсутствует</string>
```

**Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeInfoCard.kt
git commit -m "feat(aurora): add AnimeInfoCard component with stats, description, and genres

- Implement glassmorphism card for anime info
- Add expandable description (5 lines default)
- Add collapsible genre tags (3 tags default)
- Show Rating (N/A), Type (N/A), Status, Next Update stats
- Prepare for Shikimori integration (placeholders for rating/type)"
```

---

## Task 2: Create AnimeActionCard Component

**Goal:** Build the action buttons card with Favorite, Webview, Tracking, and Share buttons.

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeActionCard.kt`
- Reference: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaActionCard.kt`

**Step 1: Create AnimeActionCard file with implementation**

Create the file:

```kotlin
package eu.kanade.presentation.entries.anime.components.aurora

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
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Action buttons card with favorite, webview, tracking, and share options.
 * Compact design matching manga action card style.
 */
@Composable
fun AnimeActionCard(
    anime: Anime,
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
        verticalPadding = 6.dp,
        innerPadding = 12.dp,
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            // Favorite button
            ActionButton(
                icon = {
                    Icon(
                        if (anime.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = if (anime.favorite) {
                    stringResource(MR.strings.in_library)
                } else {
                    stringResource(MR.strings.add_to_library)
                },
                onClick = onAddToLibraryClicked,
                modifier = Modifier.weight(1f),
            )

            // Webview button
            if (onWebViewClicked != null) {
                ActionButton(
                    icon = {
                        Icon(
                            Icons.Outlined.Public,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = "Webview",
                    onClick = onWebViewClicked,
                    modifier = Modifier.weight(1f),
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
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = if (trackingCount == 0) {
                        stringResource(MR.strings.manga_tracking_tab)
                    } else {
                        pluralStringResource(MR.plurals.num_trackers, count = trackingCount, trackingCount)
                    },
                    onClick = onTrackingClicked,
                    modifier = Modifier.weight(1f),
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
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = stringResource(MR.strings.action_share),
                    onClick = onShareClicked,
                    modifier = Modifier.weight(1f),
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
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.textPrimary.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 12.sp,
        )
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeActionCard.kt
git commit -m "feat(aurora): add AnimeActionCard component

- Implement 4-button action card (Favorite, Webview, Tracking, Share)
- Match manga action card design with glassmorphism style
- Use circular icon backgrounds with compact labels"
```

---

## Task 3: Create AnimeHeroContent Component

**Goal:** Build the hero content section that appears at the bottom of the first screen with anime title, basic info, and action buttons (Continue/Start + Dubbing selector).

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeHeroContent.kt`
- Reference: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaHeroContent.kt`

**Step 1: Find and examine MangaHeroContent**

```bash
# Check if file exists
ls app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaHeroContent.kt
```

If the file doesn't exist, we need to check what's actually used in MangaScreenAurora.kt:

```bash
grep -A 20 "MangaHeroContent" app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt
```

**Step 2: Create AnimeHeroContent based on manga implementation**

Create the file:

```kotlin
package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Hero content displayed at the bottom of the first screen.
 * Shows anime title, basic info, Continue/Start button, and Dubbing selector.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeHeroContent(
    anime: Anime,
    episodeCount: Int,
    hasUnseenEpisodes: Boolean,
    onContinueWatching: () -> Unit,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f),
                    ),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Anime Title
        Text(
            text = anime.title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Genres preview (first 3)
        if (!anime.genre.isNullOrEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                anime.genre!!.take(3).forEach { genre ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = genre,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Status and episode count
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = AnimeStatusFormatter.formatStatus(anime.status),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "•",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = "$episodeCount эп.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Continue/Start button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent)
                    .clickable { onContinueWatching() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.textOnAccent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (hasUnseenEpisodes) {
                            stringResource(MR.strings.action_resume)
                        } else {
                            stringResource(MR.strings.action_start)
                        },
                        color = colors.textOnAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Dubbing selector button (if available)
            if (onDubbingClicked != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selectedDubbing?.isNotBlank() == true) {
                                colors.accent.copy(alpha = 0.3f)
                            } else {
                                Color.White.copy(alpha = 0.15f)
                            },
                        )
                        .clickable { onDubbingClicked() }
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (selectedDubbing?.isNotBlank() == true) {
                                colors.accent
                            } else {
                                Color.White.copy(alpha = 0.8f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = selectedDubbing?.takeIf { it.isNotBlank() } ?: stringResource(MR.strings.label_dubbing),
                            color = if (selectedDubbing?.isNotBlank() == true) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.8f)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeHeroContent.kt
git commit -m "feat(aurora): add AnimeHeroContent component

- Display anime title, genres preview, status, episode count
- Add Continue/Start watching button with play icon
- Add Dubbing selector button (anime-specific feature)
- Use gradient background for readability over poster"
```

---

## Task 4: Create Helper Components

**Goal:** Create supporting components needed for the anime Aurora screen.

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/EpisodesHeader.kt`
- Create: `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/FullscreenPosterBackground.kt` (if not reusable from manga)
- Reference: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/ChaptersHeader.kt`
- Reference: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/FullscreenPosterBackground.kt`

**Step 1: Check if FullscreenPosterBackground is reusable**

```bash
# Check manga implementation
cat app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/FullscreenPosterBackground.kt | head -50
```

If it's generic enough (uses Anime/Manga as generic type), we can reuse it. Otherwise, create anime-specific version.

**Step 2: Create EpisodesHeader component**

Create file:

```kotlin
package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Header for episodes section in Aurora theme.
 */
@Composable
fun EpisodesHeader(
    episodeCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(AYMR.strings.episodes) + " ($episodeCount)",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Divider(
            color = Color.White.copy(alpha = 0.2f),
            thickness = 1.dp,
        )
    }
}
```

**Step 3: Create or adapt FullscreenPosterBackground for anime**

If manga version is not reusable, create:

```kotlin
package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.data.coil.useBackground
import tachiyomi.domain.entries.anime.model.Anime

/**
 * Fullscreen poster background with parallax blur effect.
 * Darkens as user scrolls down.
 */
@Composable
fun FullscreenPosterBackground(
    anime: Anime,
    scrollOffset: Int,
    firstVisibleItemIndex: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Calculate alpha based on scroll: fade out after scrolling starts
    val backgroundAlpha = remember(scrollOffset, firstVisibleItemIndex) {
        if (firstVisibleItemIndex > 0) {
            0.3f
        } else {
            (1f - (scrollOffset / 1000f).coerceIn(0f, 0.7f))
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(anime)
            .useBackground(true)
            .crossfade(true)
            .placeholderMemoryCacheKey(anime.thumbnailUrl)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .graphicsLayer { alpha = backgroundAlpha }
            .blur(8.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.9f),
                    ),
                ),
            ),
    )
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/EpisodesHeader.kt
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/FullscreenPosterBackground.kt
git commit -m "feat(aurora): add helper components for anime screen

- Add EpisodesHeader with count and divider
- Add FullscreenPosterBackground with parallax blur effect
- Both components follow Aurora glassmorphism theme"
```

---

## Task 5: Create AnimeScreenAurora Main Implementation

**Goal:** Create the main Aurora screen implementation for anime, following the manga structure.

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreenAurora.kt`
- Reference: `app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt` (add Aurora check)

**Step 1: Create AnimeScreenAurora.kt skeleton**

Create file with package and imports:

```kotlin
package eu.kanade.presentation.entries.anime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.aurora.AnimeActionCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeHeroContent
import eu.kanade.presentation.entries.anime.components.aurora.AnimeInfoCard
import eu.kanade.presentation.entries.anime.components.aurora.EpisodesHeader
import eu.kanade.presentation.entries.anime.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
```

**Step 2: Implement AnimeScreenAuroraImpl function signature**

Add the main composable:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeScreenAuroraImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onCoverClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSettingsClicked: (() -> Unit)?,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
) {
    val anime = state.anime
    val episodes = state.episodeListItems
    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }

    // State for episodes expansion
    var episodesExpanded by remember { mutableStateOf(false) }
    val episodesToShow = if (episodesExpanded) episodes else episodes.take(5)

    // State for description and genres expansion
    var descriptionExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }

    // Check if there are unseen episodes
    val hasUnseenEpisodes = remember(episodes) {
        episodes.any { (it as? EpisodeList.Item)?.episode?.seen == false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fixed background poster
        FullscreenPosterBackground(
            anime = anime,
            scrollOffset = scrollOffset,
            firstVisibleItemIndex = firstVisibleItemIndex,
        )

        // Scrollable content
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = screenHeight,
                bottom = 100.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Info card (description and stats)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                AnimeInfoCard(
                    anime = anime,
                    episodeCount = episodes.size,
                    nextUpdate = nextUpdate,
                    onTagSearch = onTagSearch,
                    descriptionExpanded = descriptionExpanded,
                    genresExpanded = genresExpanded,
                    onToggleDescription = { descriptionExpanded = !descriptionExpanded },
                    onToggleGenres = { genresExpanded = !genresExpanded },
                )
            }

            // Action buttons card
            item {
                Spacer(modifier = Modifier.height(12.dp))
                AnimeActionCard(
                    anime = anime,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onShareClicked = onShareClicked,
                )
            }

            // Episodes header
            item {
                Spacer(modifier = Modifier.height(20.dp))
                EpisodesHeader(episodeCount = episodes.size)
            }

            // Empty state for episodes
            if (episodes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(MR.strings.no_chapters_error),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Episode list (compact cards - to be implemented in next task)
            items(
                items = episodesToShow,
                key = { (it as? EpisodeList.Item)?.episode?.id ?: it.hashCode() },
                contentType = { "episode" },
            ) { item ->
                if (item is EpisodeList.Item) {
                    // TODO: Create AnimeEpisodeCardCompact in next task
                    // Placeholder for now
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .clickable { onEpisodeClicked(item.episode, false) }
                            .padding(12.dp),
                    ) {
                        Text(
                            text = item.episode.name,
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Show More button if there are more than 5 episodes
            if (episodes.size > 5) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.12f),
                                            Color.White.copy(alpha = 0.08f),
                                        ),
                                    ),
                                )
                                .clickable { episodesExpanded = !episodesExpanded }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = if (episodesExpanded) {
                                    "Показать меньше"
                                } else {
                                    "Показать все ${episodes.size} эпизодов"
                                },
                                color = colors.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Hero content (fixed at bottom of first screen) - fades out on scroll
        val heroThreshold = (screenHeight.value * 0.7f).toInt()
        if (firstVisibleItemIndex == 0 && scrollOffset < heroThreshold) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                val heroAlpha = (1f - (scrollOffset / heroThreshold.toFloat())).coerceIn(0f, 1f)

                Box(modifier = Modifier.graphicsLayer { alpha = heroAlpha }) {
                    AnimeHeroContent(
                        anime = anime,
                        episodeCount = episodes.size,
                        hasUnseenEpisodes = hasUnseenEpisodes,
                        onContinueWatching = onContinueWatching,
                        onDubbingClicked = onDubbingClicked,
                        selectedDubbing = selectedDubbing,
                    )
                }
            }
        }

        // Floating Play button (shows after Hero Content is hidden)
        val showFab = firstVisibleItemIndex > 0 || scrollOffset > heroThreshold
        if (showFab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp, bottom = 20.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = onContinueWatching,
                    containerColor = colors.accent,
                    contentColor = colors.textOnAccent,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Back button
            IconButton(
                onClick = navigateUp,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accent.copy(alpha = 0.2f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.accent,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Filter button
            IconButton(
                onClick = onFilterButtonClicked,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accent.copy(alpha = 0.2f), CircleShape),
            ) {
                val filterTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f)
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = filterTint,
                )
            }

            // Download menu
            if (onDownloadActionClicked != null) {
                var downloadExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { downloadExpanded = !downloadExpanded },
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.accent.copy(alpha = 0.2f), CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = colors.accent,
                        )
                    }
                    DropdownMenu(
                        expanded = downloadExpanded,
                        onDismissRequest = { downloadExpanded = false },
                    ) {
                        EntryDownloadDropdownMenu(
                            expanded = true,
                            onDismissRequest = { downloadExpanded = false },
                            onDownloadClicked = { onDownloadActionClicked.invoke(it) },
                            isManga = false,
                        )
                    }
                }
            }

            // More menu
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showMenu = !showMenu },
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.accent.copy(alpha = 0.2f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = colors.accent,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            androidx.compose.material3.Text(text = stringResource(MR.strings.action_webview_refresh))
                        },
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

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreenAurora.kt
git commit -m "feat(aurora): add AnimeScreenAurora main implementation

- Implement fullscreen poster with parallax scroll
- Add AnimeInfoCard and AnimeActionCard integration
- Add hero content with fade-out animation
- Add floating FAB when hero is hidden
- Add top toolbar with back, filter, download, more buttons
- Use placeholder for episode cards (to be implemented)"
```

---

## Task 6: Integrate AnimeScreenAurora into AnimeScreen

**Goal:** Add theme check to route to Aurora implementation when Aurora theme is active.

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt`

**Step 1: Find the appropriate location to add theme check**

Read the file and find where the main `AnimeScreen` composable is called (similar to manga at line ~125).

**Step 2: Add theme check and Aurora routing**

Add after imports, before the main composable:

```kotlin
import eu.kanade.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
```

Then in the `AnimeScreen` composable function, add theme check similar to manga (after line ~125 in current code):

```kotlin
@Composable
fun AnimeScreen(
    state: AnimeScreenModel.State.Success,
    // ... existing parameters
) {
    // Add this block at the beginning
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme by uiPreferences.appTheme().collectAsState()

    if (theme.isAuroraStyle && !isTabletUi) {
        AnimeScreenAuroraImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            isTabletUi = isTabletUi,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            navigateUp = navigateUp,
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditFetchIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onDubbingClicked = onDubbingClicked,
            selectedDubbing = selectedDubbing,
        )
        return
    }

    // ... rest of existing implementation
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt
git commit -m "feat(aurora): integrate AnimeScreenAurora into routing

- Add theme check to route to Aurora implementation
- Use AnimeScreenAuroraImpl when Aurora theme is active
- Fallback to classic implementation otherwise"
```

---

## Task 7: Test Basic Aurora Anime Screen

**Goal:** Build and manually test the Aurora anime screen to verify basic functionality.

**Step 1: Build the app**

```bash
./gradlew assembleDebug
```

Expected: Build succeeds without errors

**Step 2: Install on device/emulator**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Step 3: Manual testing checklist**

1. Open app and ensure Aurora theme is enabled
2. Navigate to any anime from a source
3. Verify:
   - [ ] Fullscreen poster background loads
   - [ ] Hero content appears at bottom (title, genres, Continue button, Dubbing button if available)
   - [ ] Scrolling fades out hero content
   - [ ] AnimeInfoCard appears with stats (N/A placeholders visible)
   - [ ] Description is collapsible (if > 200 chars)
   - [ ] Genre tags are collapsible (if > 3 genres)
   - [ ] AnimeActionCard shows 4 buttons
   - [ ] Episodes header appears
   - [ ] Episode placeholders are clickable
   - [ ] FAB appears when scrolled past hero
   - [ ] Top toolbar buttons work (back, filter, download menu, more menu)

**Step 4: Document any issues**

Create file `docs/plans/2026-01-22-aurora-anime-cards-testing-notes.md` with findings.

**Step 5: Commit test results**

```bash
git add docs/plans/2026-01-22-aurora-anime-cards-testing-notes.md
git commit -m "test(aurora): document initial Aurora anime screen testing results"
```

---

## Task 8: Create AnimeEpisodeCardCompact Component

**Goal:** Create the compact episode card component for the episode list, matching the manga chapter card style.

**Files:**
- Create: `app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeEpisodeCardCompact.kt`
- Reference: `app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaChapterCardCompact.kt`

**Step 1: Examine MangaChapterCardCompact structure**

```bash
cat app/src/main/java/eu/kanade/presentation/entries/manga/components/aurora/MangaChapterCardCompact.kt | head -100
```

**Step 2: Create AnimeEpisodeCardCompact**

Create file:

```kotlin
package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.manga.components.aurora.GlassmorphismCard
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

/**
 * Compact episode card for Aurora theme episode list.
 */
@Composable
fun AnimeEpisodeCardCompact(
    anime: Anime,
    item: EpisodeList.Item,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val episode = item.episode

    // Determine episode display name
    val episodeTitle = if (anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
        "Episode ${episode.episodeNumber}"
    } else {
        episode.name
    }

    GlassmorphismCard(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalPadding = 0.dp,
        innerPadding = 12.dp,
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEpisodeClicked(episode, false) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Episode info
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Seen/Unseen indicator
                Icon(
                    imageVector = if (episode.seen) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (episode.seen) {
                        colors.accent.copy(alpha = 0.6f)
                    } else {
                        Color.White.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(20.dp),
                )

                // Episode title and scanlator
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = episodeTitle,
                        color = if (episode.seen) {
                            colors.textPrimary.copy(alpha = 0.5f)
                        } else {
                            colors.textPrimary
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(if (episode.seen) 0.6f else 1f),
                    )

                    // Scanlator/source name
                    if (episode.scanlator?.isNotBlank() == true) {
                        Text(
                            text = episode.scanlator!!,
                            color = colors.textSecondary.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Right: Download indicator (if episode is downloaded)
            if (episode.downloaded) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(colors.accent.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
```

**Step 3: Replace placeholder in AnimeScreenAurora**

In `AnimeScreenAurora.kt`, replace the episode placeholder:

```kotlin
// Replace this:
// TODO: Create AnimeEpisodeCardCompact in next task
Box(...) { ... }

// With this:
AnimeEpisodeCardCompact(
    anime = anime,
    item = item,
    onEpisodeClicked = { episode, alt -> onEpisodeClicked(episode, alt) },
    onDownloadEpisode = onDownloadEpisode,
)
```

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/entries/anime/components/aurora/AnimeEpisodeCardCompact.kt
git add app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreenAurora.kt
git commit -m "feat(aurora): add AnimeEpisodeCardCompact component

- Create compact episode card with glassmorphism style
- Show seen/unseen indicator, episode title, scanlator
- Show download indicator for downloaded episodes
- Integrate into AnimeScreenAurora episode list"
```

---

## Task 9: Polish and Edge Cases

**Goal:** Handle edge cases and polish the implementation.

**Step 1: Handle missing data gracefully**

Verify in each component:
- Empty genre list
- Missing description
- No episodes
- Missing poster/background image

**Step 2: Add proper resource strings**

Check and add missing strings to `i18n-aniyomi` module if they don't exist:

```bash
# Check current strings
grep -r "aurora_rating\|aurora_type\|aurora_status" i18n-aniyomi/
```

If missing, add to appropriate locale files.

**Step 3: Accessibility improvements**

Add content descriptions where missing:
- Icon buttons need contentDescription
- Clickable elements need semantic labels

**Step 4: Commit polish changes**

```bash
git add .
git commit -m "polish(aurora): handle edge cases and improve accessibility

- Add proper handling for missing data (genres, description, episodes)
- Add missing string resources
- Improve content descriptions for accessibility"
```

---

## Task 10: Final Testing and Documentation

**Goal:** Comprehensive testing and documentation of the Aurora anime implementation.

**Step 1: Build release variant**

```bash
./gradlew assembleRelease
```

Expected: Build succeeds

**Step 2: Comprehensive manual testing**

Test all scenarios:
1. Anime with all data present
2. Anime with missing description
3. Anime with no genres
4. Anime with 1-2 genres (no collapse)
5. Ongoing anime (4 stats)
6. Completed anime (3 stats)
7. Anime with no episodes
8. Anime with 1-4 episodes (no "Show More")
9. Anime with >5 episodes (test collapse)
10. Dubbing selection (if available)
11. All action buttons (favorite, webview, tracking, share)
12. Scroll behavior (hero fade, FAB appearance)
13. Theme switching (Aurora <-> Classic)
14. Tablet mode (should use classic)

**Step 3: Create comparison screenshots**

Take screenshots of:
- Manga Aurora screen
- Anime Aurora screen
- Side-by-side comparison

Save to `docs/plans/aurora-anime-screenshots/`

**Step 4: Update documentation**

Create `docs/aurora-theme.md` documenting:
- Aurora theme architecture
- How to add new Aurora screens
- Component structure
- Shikimori integration plan (placeholder)

**Step 5: Final commit**

```bash
git add docs/
git commit -m "docs(aurora): add comprehensive Aurora anime documentation

- Add testing checklist with all scenarios
- Add comparison screenshots
- Document Aurora theme architecture
- Add Shikimori integration placeholder notes"
```

---

## Future Tasks (Not in This Plan)

These are follow-up tasks that should be done separately:

1. **Shikimori Integration** - Fetch real rating, type, studio, season/year data
2. **Episode Cards Enhancement** - Add more metadata (duration, air date, filler marker)
3. **Performance Optimization** - LazyColumn improvements, image caching
4. **Animations** - Smooth transitions, hero animations
5. **Tablet Layout** - Create Aurora tablet variant
6. **Testing** - Unit tests for formatters, component tests

---

## Summary

This plan creates a complete Aurora-themed anime screen implementation with:
- **AnimeInfoCard** - Stats, description, genres with placeholders for Shikimori
- **AnimeActionCard** - 4 action buttons (favorite, webview, tracking, share)
- **AnimeHeroContent** - Title, info, Continue/Start button, Dubbing selector
- **AnimeEpisodeCardCompact** - Compact episode cards in glassmorphism style
- **AnimeScreenAurora** - Main screen with fullscreen poster, scroll effects, FAB

All components follow the established manga Aurora patterns while adding anime-specific features like dubbing selection.
