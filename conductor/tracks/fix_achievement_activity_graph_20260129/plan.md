# Implementation Plan: Fix and Refine Achievement Activity Graph

## Phase 1: Data Infrastructure & Tracking Fix [checkpoint: 544376b]
Goal: Ensure activity data is correctly recorded and retrieved.

- [x] Task: Create unit tests for `ActivityDataRepository` to verify day-to-day activity aggregation.
    - [x] Target: `ActivityDataRepositoryTest.kt`
    - [x] Test Cases: Storage of reading events, watching events, and retrieval of the 365-day list.
- [x] Task: Fix `ActivityDataRepositoryImpl` to correctly fetch data.
    - [x] Investigate if `prefs.getInt` is the correct source or if it should query the History database.
    - [x] Ensure all `ActivityType` values (READING, WATCHING, APP_OPEN) are supported.
- [x] Task: Verify event publishing in Repositories.
    - [x] Check `MangaHistoryRepositoryImpl` and `AnimeHistoryRepositoryImpl` to ensure they call `activityDataRepository.logActivity()`.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Data Infrastructure' (Protocol in workflow.md)

## Phase 2: ScreenModel Data Flow
Goal: Ensure the UI state receives the activity data from the repository.

- [x] Task: Create unit tests for `AchievementScreenModel` focusing on the `Success` state composition.
    - [x] Verify `activityData` is not empty when the repository returns data.
- [x] Task: Fix `AchievementScreenModel` init block.
    - [x] Ensure `combine` logic correctly handles the flow from `activityDataRepository.getActivityData(365)`.
- [x] Task: Conductor - User Manual Verification 'Phase 2: ScreenModel Flow' (Protocol in workflow.md)

## Phase 3: UI Component & Interaction Refinement
Goal: Polish the graph component and update interaction to single-tap.

- [x] Task: Update `DayCell` interaction.
    - [x] Replace `onLongPress` with a simple `onTap`/`onClick` to toggle the tooltip.
    - [x] Ensure tapping outside or on another cell clears the previous tooltip.
- [x] Task: Fix `MonthLabelsRow` positioning.
    - [x] Refine the `calculateMonthLabels` logic so labels align precisely with the start of each month in the grid.
- [x] Task: Refine `calculateCellColor`.
    - [x] Use `AuroraTheme.colors.accent` with varied alpha steps or a dedicated color ramp that matches the Aurora palette.
- [x] Task: Conductor - User Manual Verification 'Phase 3: UI & Interaction' (Protocol in workflow.md)

## Phase 4: Final Verification & Polish
- [x] Task: Audit UI performance during horizontal scroll with 365 cells.
- [x] Task: Final Manual Verification on Device.
- [x] Task: Conductor - User Manual Verification 'Phase 4: Final Polish' (Protocol in workflow.md)
