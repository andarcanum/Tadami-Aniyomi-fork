# Ranobe Menu/Settings Parity Implementation Plan

> Progress rule: update checkboxes `[ ] -> [x]` only after implementation + verification for each item.

## 0. Baseline
- [x] Commit existing unrelated working-tree changes as baseline checkpoint.

## 1. Critical parity in existing novel screens
- [x] Add source settings action from novel entry screen toolbar.
- [x] Add migrate action from novel entry screen toolbar.
- [x] Add tracking action parity in novel entry screens (Aurora + standard) with backend support.
- [x] Bring `NovelLibrarySettingsDialog` to parity with anime/manga for display/filter toggles where backend exists.
- [ ] Add/adjust tests for novel entry toolbar actions and novel library settings parity.

## 2. Browse parity (novel extension details + source preferences + migration)
- [x] Add `browse/novel/extension/details/*` screens + screen model parity.
- [x] Wire novel extensions list to open details screen.
- [x] Add `NovelSourcePreferencesScreen` for `ConfigurableNovelSource`.
- [x] Wire novel source toolbar/settings entry points to source preferences screen.
- [x] Add novel migration flow in browse (source tab + screen model + screen/dialogs).
- [ ] Add tests for novel extension details/source prefs/migration navigation.

## 3. More tabs parity (history/downloads/stats/storage)
- [x] Add novel history tab + screen model + UI and include in `HistoriesTab`.
- [x] Add novel download queue tab + screen model + UI and include in `DownloadsTab`.
- [x] Add novel stats tab and include in `StatsTab`.
- [x] Add novel storage tab and include in `StorageTab`.
- [ ] Add/adjust tests for all added novel tabs.

## 4. Deeplink + tracking backend parity
- [x] Add novel deeplink flow (`Activity` + `Screen` + `ScreenModel`).
- [x] Implement full novel tracking backend/domain parity (repo/interactors/models) and UI integration.
- [x] Add tests for novel deeplink and tracking flows.

## 5. Final verification
- [x] Run targeted unit/UI tests for touched modules.
- [x] Run project verification subset for changed features.
- [x] Update this plan with final statuses.

## Remaining blockers
- Test-parity items remain open because dedicated test coverage for newly added novel tabs/flows has not yet been implemented in this pass.
