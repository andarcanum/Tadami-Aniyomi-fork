# Aurora Manga Screen - User Guide

## Overview

The Aurora manga detail screen features a modern, immersive design inspired by streaming platforms like Netflix and Disney+. It provides a cinematic first impression with smooth transitions and intuitive navigation.

### Key Features

- **Fullscreen poster background** - Immersive manga cover that fills the entire first screen
- **Minimalist first-screen experience** - Clean, distraction-free hero content
- **Glassmorphism card design** - Semi-transparent cards with blur effects
- **Smooth scroll animations** - Natural transitions as you explore content
- **Intelligent information hierarchy** - Most important actions front and center

---

## Screen Layout

### First Screen (Hero View)

When you first open a manga, you'll see:

1. **Fullscreen Poster** - The manga cover fills the entire screen with a gradient overlay
2. **Genre Chips** (top) - Up to 3 main genres displayed as compact tags
3. **Manga Title** - Large, bold title prominently displayed
4. **Quick Stats Row** - Rating ⭐, Status, and Chapter Count
5. **Continue Reading Button** - Large primary action button to start/resume

### Scrollable Content

Scroll down to reveal:

1. **Info Card**
   - Rating, Status, Next Update statistics
   - Expandable description
   - All genre tags (clickable)

2. **Action Buttons Card**
   - ❤️ Favorite/Library toggle
   - 🌐 Webview (open in browser)
   - 🔄 Tracking services
   - ↗️ Share

3. **Chapters Section**
   - Compact chapter cards
   - Download indicators
   - Progress tracking
   - "Show More/Less" button (if > 5 chapters)

---

## Navigation

### Opening a Manga

Tap any manga from your library or browse results to open the Aurora detail screen.

### First Impression

- The screen opens with a fullscreen poster
- Hero content is positioned at the bottom for easy thumb access
- Most important action (Continue Reading) is immediately visible

### Scrolling

- **Scroll down** to reveal more information
- The poster gradually **blurs and dims** as you scroll
- **Hero content fades out** smoothly (disappears at 70% scroll)
- **FAB appears** when hero content is hidden

---

## Interactions

### Primary Actions

#### Continue Reading Button
- **Tap** to start reading from your last position (or Chapter 1 if new)
- Provides **haptic feedback** when pressed
- Available in two places:
  - Hero content (first screen)
  - Floating Action Button (after scrolling)

### Secondary Actions

#### Favorite/Library
- **Tap** to add/remove manga from your library
- **Filled heart** = In library
- **Outline heart** = Not in library

#### Webview
- **Tap** to open manga in browser/web view
- Useful for checking source website

#### Tracking
- **Tap** to manage tracking services (AniList, MyAnimeList, etc.)
- Shows number of active trackers
- **Sync icon** if not tracking
- **Check icon** if tracking

#### Share
- **Tap** to share manga with others
- Opens system share sheet

### Info Card Actions

#### Description
- **Tap arrow icon** (right side) to expand/collapse
- Only appears if description is long (> 200 chars)
- Expands downward smoothly

#### Genres
- **Tap genre chip** to search for similar manga
- **Tap arrow icon** to show all genres (if > 3)
- Initially shows 3 most relevant genres

### Chapter Actions

#### Read Chapter
- **Tap chapter card** to open reader

#### Chapter Options
- **Long press** (manual testing required) to enter selection mode
- Download, mark as read, bookmark, etc.

#### Show More/Less
- **Tap button** to expand/collapse chapter list
- Initially shows 5 chapters
- Button shows total count: "Показать все X глав"

---

## Visual Effects

### Poster Background

The fullscreen poster creates an immersive experience:

1. **Initial State** (0% scroll)
   - Sharp, clear poster
   - Gradient overlay from transparent (top) to dark (bottom)
   - Hero content fully visible

2. **Scrolling** (0-70%)
   - Poster progressively **blurs** (up to 20dp)
   - Additional **dimming** overlay increases (up to 70% opacity)
   - Hero content **fades out** proportionally

3. **Scrolled Away** (>70%)
   - Poster remains blurred and dimmed
   - Hero content hidden
   - FAB appears for quick access to Continue Reading

### Glassmorphism Cards

Info and Action cards feature:
- Semi-transparent white background (12% opacity)
- Backdrop blur effect
- Subtle border (15% white opacity)
- Creates depth and layering

### Animations

All animations use physics-based spring timing for natural feel:
- **Description/Genre expansion** - Medium bouncy spring
- **Hero content fade** - Linear alpha transition
- **Poster blur/dim** - No-bounce spring, low stiffness
- **Button ripples** - Material 3 default ripple

---

## Stats Explained

### Rating
- Parsed from manga description
- Format: "8.0" (1 decimal place)
- Shows "Н/Д" if not available
- Includes ⭐ star icon in hero view

### Status
- **Выходит** - Ongoing (new chapters releasing)
- **Завершено** - Completed
- **Платно** - Licensed (requires payment)
- **Публикация завершена** - Publishing finished
- **Отменено** - Cancelled
- **Перерыв** - On hiatus
- **Неизвестно** - Unknown

### Next Update (Обновление)
- Only shown for ongoing manga
- Format: "X д" (X days until next chapter)
- Shows "Soon" if < 1 day
- Shows "Н/Д" if unknown
- **Hidden** for completed/finished/cancelled manga

### Chapter Count
- Total number of available chapters
- Updates as new chapters are added

---

## Tips & Tricks

### Quick Reading
- Use the **Continue Reading** button on first screen for fastest access
- After scrolling, use the **FAB** (Floating Action Button) in bottom-right

### Discovering Similar Manga
- **Tap any genre chip** to search for manga with that genre
- Great for finding new series in your favorite categories

### Managing Your Library
- **Tap the heart** to quickly add/remove from library
- Filled heart means it's already in your collection

### Tracking Progress
- Set up **tracking services** to sync your progress across platforms
- The counter shows how many services you're syncing with

### Reading Description
- Long descriptions are **collapsed by default**
- Tap the **arrow icon** to read the full synopsis
- Same for genres - tap arrow to see all tags

### Chapter Navigation
- First **5 chapters** shown by default
- Tap **"Показать все X глав"** to see full list
- Helps keep the screen uncluttered for series with hundreds of chapters

---

## Accessibility

### Visual Hierarchy
- **Largest text** - Manga title (36sp)
- **Medium text** - Section headers, button labels
- **Small text** - Stats, genre chips, chapter info

### Color System
- **Accent color** - Interactive elements, important info
- **White** - Primary text (title, description)
- **White 85%** - Secondary text (stats)
- **White 70%** - Tertiary text (hints, placeholders)

### Touch Targets
- All buttons meet minimum 44dp touch target size
- Generous padding around clickable elements
- Haptic feedback confirms button presses

---

## Technical Details

### Performance
- **Poster quality** - Loads high-resolution image immediately
- **Blur performance** - GPU-accelerated blur effect
- **Animation smoothness** - 60fps spring animations
- **Lazy loading** - Chapters load efficiently in scrollable list

### Caching
- Poster images cached for fast loading
- High-quality version prioritized over thumbnail

### Compatibility
- Supports all manga with standard metadata
- Gracefully handles missing data (genres, descriptions, etc.)
- Works in portrait mode (landscape support TBD)

---

## Troubleshooting

### Poster Not Loading
- Check internet connection
- Verify manga has valid cover URL
- Try refreshing the manga metadata

### Rating Shows "Н/Д"
- Some manga don't have ratings in their descriptions
- Rating is parsed from source metadata
- Pattern expected: "X.X (голосов: XXXX)"

### Chapters Not Showing
- Verify manga has chapters in database
- Try refreshing manga/chapters
- Check source extension is working

### Description Not Expanding
- Only long descriptions (> 200 chars) have expand arrow
- Short descriptions show fully by default

---

## Feedback & Support

This is the Aurora UI implementation for Tadami (Aniyomi fork).

For issues or suggestions:
1. Check GitHub issues for known problems
2. Submit new issue with detailed description
3. Include screenshots if reporting visual bugs
4. Specify device model and Android version

---

**Enjoy your immersive manga reading experience! 📚✨**
