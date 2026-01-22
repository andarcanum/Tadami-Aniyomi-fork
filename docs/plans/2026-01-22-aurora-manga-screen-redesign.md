# Aurora Manga Screen Redesign - Design Document

**Date:** 2026-01-22
**Status:** Design Complete, Ready for Implementation
**Target:** `MangaScreenAurora.kt` complete visual overhaul

---

## Overview

Complete redesign of the Aurora manga detail screen with a focus on modern, immersive UX featuring:
- Fullscreen poster background with gradient overlay
- Glassmorphism card design for content sections
- Minimalist first-screen experience
- Optimized chapter list with compact thumbnails
- Smooth animations and transitions

---

## Design Goals

1. **Visual Appeal**: Modern, eye-catching design that showcases manga artwork
2. **Usability**: Easy access to primary actions (Continue Reading, Add to Library)
3. **Information Hierarchy**: Progressive disclosure - essential info first, details after scroll
4. **Performance**: Smooth scrolling with optimized rendering
5. **Consistency**: Maintains Aurora theme identity

---

## Part 1: First Screen (Before Scroll)

### Fullscreen Poster Background

**Layout:**
- Poster fills 100% of screen height
- Fixed position (does not scroll with content)
- Acts as immersive background for entire screen

**Gradient Overlay:**
- Vertical gradient from transparent (top) to dark (bottom)
- Gradient stops:
  ```kotlin
  Brush.verticalGradient(
      0.0f to Color.Transparent,
      0.3f to Color.Black.copy(alpha = 0.1f),
      0.5f to Color.Black.copy(alpha = 0.4f),
      0.7f to Color.Black.copy(alpha = 0.7f),
      1.0f to Color.Black.copy(alpha = 0.9f)
  )
  ```
- Ensures text readability at bottom of screen

### Header Bar

**Position:** Top of screen with status bar padding

**Elements:**
- Back button (left): Icon with `colors.accent.copy(alpha = 0.3f)` circular background
- Action buttons (right): Filter, Download, Menu
- All buttons: Semi-transparent backgrounds with blur effect
- Size: 44.dp each
- Background: `CircleShape` with `colors.accent.copy(alpha = 0.2f)`

### Hero Content (Bottom of First Screen)

**Container:**
- Column aligned to bottom
- Padding: `horizontal = 20.dp`, `bottom = 40.dp`
- All elements in vertical stack

**1. Genre Tags:**
- Horizontal row of 2-3 genre chips
- Style: Pill-shaped (RoundedCornerShape(12.dp))
- Size: `fontSize = 11.sp`, `padding = 6.dp horizontal × 3.dp vertical`
- Background: `colors.accent.copy(alpha = 0.25f)`
- Text color: `colors.accent`
- Spacing: 8.dp between chips

**2. Manga Title:**
- `fontSize = 36.sp`
- `fontWeight = FontWeight.Black`
- `color = Color.White`
- `lineHeight = 40.sp`
- Max lines: 3 with `TextOverflow.Ellipsis`
- Spacing below: 8.dp

**3. Compact Statistics Row:**
- Single line with separator dots
- Format: "⭐ 4.9  •  Ongoing  •  245 chapters"
- `fontSize = 13.sp`
- `color = Color.White.copy(alpha = 0.85f)`
- Icon size: 14.dp
- Spacing: 12.dp below

**4. Continue Reading Button:**
- Width: `fillMaxWidth()` (with container padding)
- Height: 60.dp
- Background: `colors.accent` (solid or gradient)
- Shape: `RoundedCornerShape(16.dp)`
- Content:
  - PlayArrow icon (28.dp) + "Continue Reading" text
  - `fontSize = 18.sp`, `fontWeight = FontWeight.Bold`
  - Icon and text centered in Row
- Elevation/Shadow: Subtle shadow for depth
- Ripple effect on press

---

## Part 2: Scroll Behavior

### Poster Parallax Effect

**Fixed Background:**
- Poster remains in fixed position (does not scroll)
- Content scrolls over the poster

**Progressive Darkening:**
```kotlin
val scrollOffset = lazyListState.firstVisibleItemScrollOffset
val dimAlpha = (scrollOffset / 400f).coerceIn(0f, 0.7f)
val blurAmount = (scrollOffset / 400f * 20f).coerceIn(0f, 20f).dp

// Apply over poster
Box(Modifier.background(Color.Black.copy(alpha = dimAlpha)))
AsyncImage(Modifier.blur(blurAmount))
```

**Effect:**
- First 400.dp of scroll: Poster darkens from 0% to 70% opacity overlay
- Simultaneously: Blur increases from 0.dp to 20.dp
- Creates depth and focus on content
- Smooth spring animation

### Content Scroll

**LazyColumn Setup:**
- `contentPadding.top = screenHeight` (first screen is "empty")
- Content begins after first screen
- Scrolls over fixed poster background

---

## Part 3: Scrollable Content Sections

### Universal Card Styling (Glassmorphism)

**Base Style for All Cards:**
```kotlin
Modifier
    .padding(horizontal = 16.dp)
    .clip(RoundedCornerShape(20.dp))
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
        shape = RoundedCornerShape(20.dp)
    )
```

**Spacing Between Cards:**
- 12.dp between action and info cards
- 20.dp between info card and chapter section

---

### Section 1: Action Buttons Card

**Position:** First card after scroll (16.dp from top)

**Layout:**
- Row with `SpaceEvenly` arrangement
- Padding inside card: 16.dp
- 4 buttons in horizontal row

**Button Structure (Each):**
```
Column(horizontalAlignment = CenterHorizontally) {
    Box(
        size = 48.dp,
        shape = CircleShape,
        background = colors.accent.copy(alpha = 0.15f)
    ) {
        Icon(size = 32.dp, tint = colors.accent)
    }
    Spacer(8.dp)
    Text(fontSize = 11.sp, color = colors.textPrimary)
}
```

**Four Buttons:**
1. **Favorite/In Library**
   - Icon: `Icons.Filled.Favorite` / `FavoriteBorder`
   - Text: "In Library" / "Add"
   - Action: `onAddToLibraryClicked()`

2. **Webview/Source**
   - Icon: `Icons.Outlined.Public`
   - Text: "Source"
   - Action: `onWebViewClicked()`

3. **Tracking**
   - Icon: `Icons.Outlined.Sync` / `Done`
   - Text: "Tracking" or tracker count
   - Badge: Show number if trackingCount > 0
   - Action: `onTrackingClicked()`

4. **Share**
   - Icon: `Icons.Default.Share`
   - Text: "Share"
   - Action: `onShareClicked()`

**Interaction:**
- Ripple effect with `colors.accent.copy(alpha = 0.2f)`
- Haptic feedback on important actions
- Scale animation on press (0.95f)

---

### Section 2: Info & Description Card

**Position:** 12.dp below action card

**Padding Inside:** 20.dp

**Contents (Vertical Stack):**

**1. Section Header (Optional):**
- "About" or "Synopsis"
- `fontSize = 16.sp`, `fontWeight = FontWeight.Bold`
- `color = colors.textPrimary`
- Spacing: 12.dp below

**2. Detailed Statistics Grid:**
- 2 columns × 2 rows
- Elements: Rating, Status, Chapters, Next Update

**Grid Item Structure:**
```kotlin
Column(horizontalAlignment = CenterHorizontally) {
    Text(
        text = "4.9", // value
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary
    )
    Text(
        text = "RATING", // label
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = colors.textSecondary
    )
}
```

- Vertical dividers between columns
- Spacing: 16.dp between rows

**3. Description Text:**
- `fontSize = 14.sp`, `lineHeight = 22.sp`
- `color = colors.textPrimary.copy(alpha = 0.9f)`
- Default: 5 lines max with `TextOverflow.Ellipsis`
- Expandable via state variable

**4. Read More Button:**
- Text button below description
- "Read more" / "Show less" based on state
- `fontSize = 12.sp`, `fontWeight = FontWeight.SemiBold`
- `color = colors.accent`
- Click toggles `descriptionExpanded` state

**5. Genre Tags (Full List):**
- FlowRow with all genres (not limited to 3)
- Each tag: `SuggestionChip` style
- `fontSize = 12.sp`
- Clickable: Calls `onTagSearch(genre)`
- Spacing: 8.dp horizontal, 8.dp vertical

**Animations:**
- Description expansion: `Modifier.animateContentSize()` with spring
- Smooth transition for "Read more" button

---

### Section 3: Chapters Header

**Position:** 20.dp below info card (NOT in a card)

**Layout:**
- Row with `SpaceBetween` arrangement
- Padding: `horizontal = 20.dp`

**Left Side:**
- Text: "Chapters"
- `fontSize = 24.sp`
- `fontWeight = FontWeight.Bold`
- `color = Color.White`

**Right Side:**
- Text: Chapter count (e.g., "245")
- `fontSize = 14.sp`
- `fontWeight = FontWeight.SemiBold`
- `color = colors.accent`
- Background: Badge with `colors.accent.copy(alpha = 0.2f)`
- Padding: 6.dp horizontal × 4.dp vertical
- Shape: `RoundedCornerShape(8.dp)`

---

### Section 4: Chapter List (Individual Cards)

**Layout:**
- Each chapter is a separate card
- Spacing between cards: 8.dp
- Padding: `horizontal = 16.dp`

**Card Style:**
- Same glassmorphism as other cards
- `RoundedCornerShape(16.dp)` (slightly smaller than info cards)
- Padding inside: 12.dp

**Chapter Card Content (Row):**

**1. Thumbnail (40×40.dp):**
- Small square thumbnail on left
- Size: 40.dp × 40.dp
- Shape: `RoundedCornerShape(8.dp)`
- Content: Manga cover (reused from main cover)
- ContentScale: Crop
- If chapter.read: Overlay with semi-transparent dark layer

**2. Chapter Number Badge (Alternative to thumbnail):**
- If thumbnail is too heavy, can replace with:
- Circular badge 44.dp diameter
- Background: `colors.accent.copy(alpha = 0.15f)`
- Text: Chapter number only (e.g., "23")
- `fontSize = 16.sp`, `fontWeight = FontWeight.Bold`

**3. Chapter Info (Center, weight = 1f):**

Column with:
```kotlin
// Chapter name
Text(
    text = chapter.name,
    fontSize = 15.sp,
    fontWeight = FontWeight.SemiBold,
    color = colors.textPrimary,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)

Spacer(4.dp)

// Meta info
Row(verticalAlignment = CenterVertically) {
    Icon(Icons.Outlined.Schedule, size = 12.dp, color = colors.textSecondary)
    Spacer(4.dp)
    Text(
        text = "2 days ago",
        fontSize = 12.sp,
        color = colors.textSecondary
    )
}

// Progress bar (if read)
if (chapter.read) {
    Spacer(6.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.divider)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(1f) // 100% for completed
                .height(3.dp)
                .background(colors.accent)
        )
    }
}
```

**4. Actions (Right Side):**

Column (vertical stack):
```kotlin
// Download indicator
ChapterDownloadIndicator(
    enabled = true,
    downloadStateProvider = { item.downloadState },
    downloadProgressProvider = { item.downloadProgress },
    onClick = { onDownloadChapter(listOf(item), it) },
    modifier = Modifier.size(20.dp)
)

// Read checkmark (if read)
if (chapter.read) {
    Icon(
        Icons.Outlined.Done,
        tint = colors.accent,
        modifier = Modifier.size(18.dp)
    )
}
```

**Visual States:**

- **Unread:**
  - Full opacity
  - No progress bar
  - Accent colors at full brightness

- **Read:**
  - Text alpha: 0.6f
  - Progress bar: 100% filled
  - Checkmark visible

- **Downloading:**
  - Progress indicator replaces download icon
  - Animated progress

- **Downloaded:**
  - Download icon with accent color
  - No animation

**Interactions:**
- Click anywhere on card: Open chapter (`onChapterClicked`)
- Long press: Enter selection mode
- Swipe: Mark as read / Download (existing `onChapterSwipe`)

---

### Section 5: Bottom Padding

**Final Element:**
- Spacer with height: 100.dp
- Prevents last chapter from being cut off at screen bottom
- Provides breathing room

---

## Part 4: Animations & Technical Details

### Animations

**1. Poster Dimming on Scroll:**
```kotlin
val scrollOffset by remember {
    derivedStateOf { lazyListState.firstVisibleItemScrollOffset }
}

val dimAlpha by animateFloatAsState(
    targetValue = (scrollOffset / 400f).coerceIn(0f, 0.7f),
    animationSpec = spring(stiffness = Spring.StiffnessLow)
)

val blurAmount = (scrollOffset / 400f * 20f).coerceIn(0f, 20f).dp
```

**2. Card Entrance Animations:**
- Each card fades in with `fadeIn()` + `slideInVertically()`
- Cascade effect: 50ms delay between cards
- Use `AnimatedVisibility` or `Modifier.graphicsLayer`

**3. Description Expansion:**
```kotlin
Text(
    modifier = Modifier.animateContentSize(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
)
```

**4. Button Press Feedback:**
- Ripple: `colors.accent.copy(alpha = 0.2f)`
- Scale: 0.95f on press, spring back
- Haptic feedback for primary actions (Continue Reading, Favorite)

**5. Chapter State Transitions:**
- Download progress: Smooth linear animation
- Read state change: `animateFloatAsState()` for alpha
- Progress bar fill: Animated with spring

### Performance Optimizations

**1. Lazy Loading:**
```kotlin
LazyColumn(
    state = lazyListState,
    contentPadding = PaddingValues(top = screenHeight, bottom = 100.dp)
) {
    items(
        items = chapters,
        key = { it.chapter.id },
        contentType = { "chapter" }
    ) { item ->
        ChapterCard(item)
    }
}
```

**2. Image Loading:**
```kotlin
AsyncImage(
    model = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
        ImageRequest.Builder(context)
            .data(manga.asMangaCover())
            .placeholderMemoryCacheKey(manga.thumbnailUrl)
            .crossfade(true)
            .size(40.dp.value.toInt()) // For chapter thumbnails
            .build()
    }
)
```

**3. Recomposition Optimization:**
- Use `remember` for ImageRequest objects
- Use `derivedStateOf` for calculated values (scroll offset)
- Stable keys for LazyColumn items
- Avoid unnecessary lambda allocations

### Responsive Design

**Tablet UI (isTabletUi = true):**
- Max content width: 600.dp, centered
- Poster can show more detail
- Consider two-column layout for some cards

**Landscape Orientation:**
- Split screen: Poster left (50%), content right (50%)
- No scroll needed for primary info
- Chapter list still scrollable

**Accessibility:**
- All touch targets minimum 48.dp
- Text contrast meets WCAG AA standards
- Semantic descriptions for screen readers
- Support for system font scaling

### Component Structure

**New Components to Create:**

1. **`MangaScreenAuroraFullscreen`**
   - Main container composable
   - Manages scroll state and poster effects

2. **`FullscreenPosterBackground`**
   - Fixed background with blur/dim effects
   - Responds to scroll offset

3. **`MangaHeroContent`**
   - First screen content (title, button, stats)
   - Positioned over poster gradient

4. **`GlassmorphismCard`**
   - Reusable card with glass effect
   - Takes content as lambda

5. **`MangaActionCard`**
   - Four-button action row
   - Handles all action callbacks

6. **`MangaInfoCard`**
   - Stats grid + description + genres
   - Expandable description logic

7. **`MangaChapterCardCompact`**
   - Individual chapter card
   - 40×40 thumbnail variant
   - All chapter states and interactions

---

## Implementation Checklist

### Phase 1: Foundation
- [ ] Create new file structure for components
- [ ] Set up scroll state management
- [ ] Implement fixed poster background
- [ ] Add dimming/blur effects on scroll

### Phase 2: First Screen
- [ ] Hero content layout (title, stats, button)
- [ ] Genre chips
- [ ] Continue Reading button with gradient
- [ ] Gradient overlay on poster

### Phase 3: Glassmorphism Cards
- [ ] Create reusable GlassmorphismCard component
- [ ] Implement base styling (blur, border, gradients)
- [ ] Test on different backgrounds

### Phase 4: Action Card
- [ ] Four-button layout
- [ ] Icon + text structure for each button
- [ ] Wire up callbacks
- [ ] Add ripple and press animations

### Phase 5: Info Card
- [ ] Stats grid (2×2)
- [ ] Description with expand/collapse
- [ ] Genre tags with search functionality
- [ ] Animate content size changes

### Phase 6: Chapter List
- [ ] Chapter header (title + count)
- [ ] Individual chapter cards
- [ ] 40×40 thumbnail implementation
- [ ] Progress bar for read chapters
- [ ] Download indicator integration
- [ ] State-based styling (read/unread)

### Phase 7: Animations
- [ ] Scroll-based poster effects
- [ ] Card entrance animations
- [ ] Button press feedback
- [ ] Description expansion animation
- [ ] Chapter state transitions

### Phase 8: Polish
- [ ] Responsive design (tablet/landscape)
- [ ] Accessibility improvements
- [ ] Performance optimization
- [ ] Testing across devices

### Phase 9: Integration
- [ ] Replace current MangaScreenAurora usage
- [ ] Handle all existing callbacks
- [ ] Preserve existing functionality
- [ ] Test with real data

---

## Design Rationale

### Why Fullscreen Poster?
- **Immersion**: Creates cinematic, engaging first impression
- **Visual Impact**: Showcases manga artwork prominently
- **Modern UX**: Aligns with streaming apps (Netflix, Disney+)
- **Brand Identity**: Reinforces Aurora's focus on visual appeal

### Why Separate Cards for Content?
- **Scannable**: Easy to distinguish different sections
- **Flexible**: Can reorder or hide sections easily
- **Glassmorphism**: Each card has depth against poster background
- **Progressive Disclosure**: User scrolls to see more detail

### Why Compact Chapter List?
- **Efficiency**: More chapters visible at once
- **Reduced Redundancy**: Don't need full manga cover for each chapter
- **Performance**: Smaller images = faster rendering
- **Focus**: Emphasis on chapter info, not visuals

### Why Minimize First Screen?
- **Clarity**: Single clear CTA (Continue Reading)
- **Speed**: User can start reading immediately
- **Elegance**: Less clutter = more impact
- **Discovery**: Encourages scroll to explore details

---

## Future Enhancements

### Potential Additions (Not in Initial Scope)

1. **Chapter Thumbnails from Source**
   - If source provides chapter-specific artwork
   - Replace manga cover with actual chapter preview

2. **Reading Statistics**
   - Time spent reading
   - Reading streak
   - Chapters per day graph

3. **Character Showcase**
   - If metadata available
   - Horizontal scroll of character cards

4. **Similar Manga Recommendations**
   - At bottom of chapter list
   - Based on genres/tags

5. **Community Features**
   - Comments/reviews section
   - Rating breakdown

6. **Customization Options**
   - Toggle poster blur amount
   - Choose gradient intensity
   - Compact vs. detailed chapter cards

---

## Success Metrics

### How to Measure Success

1. **User Engagement**
   - Time spent on manga screen
   - Scroll depth (do users explore beyond first screen?)
   - Click-through rate on Continue Reading button

2. **Performance**
   - Scroll FPS (target: 60fps)
   - First render time
   - Memory usage

3. **Usability**
   - Reduced taps to common actions
   - User feedback/ratings
   - Crash rate

4. **Visual Quality**
   - Screenshot shares (users showing off UI)
   - Positive feedback on design
   - Differentiation from other manga apps

---

## References

- Current implementation: `app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreenAurora.kt`
- Aurora theme: `eu.kanade.presentation.theme.AuroraTheme`
- Existing components: `eu.kanade.presentation.entries.manga.components/`

---

**End of Design Document**
