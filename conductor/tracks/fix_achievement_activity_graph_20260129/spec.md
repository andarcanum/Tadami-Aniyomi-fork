# Track Specification: Fix and Refine Achievement Activity Graph

## Overview
This track aims to fix the broken "Years Activity" block in the Achievements screen. Currently, the block appears empty even when user activity exists. Additionally, the component will be refined for better usability by switching from long-press to tap interactions and polishing the UI to match the Aurora Design System.

## Functional Requirements
- **Data Persistence & Retrieval:**
    - Verify `ActivityDataRepository` correctly tracks daily events (chapter reads, episode watches, app opens).
    - Fix any issues in `AchievementScreenModel` that prevent `activityData` from being correctly combined into the Success state.
- **UI Component (`AchievementActivityGraph`):**
    - Ensure the GitHub-style grid accurately represents the last 365 days of data.
    - Fix month label positioning to accurately reflect the start of each month in the horizontal scroll.
    - Implement a color scale (0-4) that utilizes Aurora semantic accent colors.
- **Interaction Model:**
    - Replace `onLongPress` with `onTap` (single tap) for triggering daily activity tooltips.
    - Ensure tooltips are responsive and don't overlap awkwardly during horizontal scrolling.

## Non-Functional Requirements
- **Performance:** Ensure horizontal scrolling of the 365-day grid remains performant on mid-range devices.
- **Visuals:** Use Aurora Design System principles (Material 3 base with custom glassmorphism and neon accents).
- **Localization:** Ensure date formatting in tooltips and month labels supports the user's locale (defaulting to Russian/English as per current strings).

## Acceptance Criteria
- [ ] The activity graph displays non-empty data for users with recorded history.
- [ ] Tapping a specific day cell displays a tooltip with correct details.
- [ ] The graph is horizontally scrollable.
- [ ] UI colors correctly reflect activity levels (higher activity = more intense color).
- [ ] The build passes without linting errors in modified components.

## Out of Scope
- Adding new activity tracking types (e.g., tracking comments or library size changes).
- Modifying the Achievement calculation logic itself (only the activity graph data).
