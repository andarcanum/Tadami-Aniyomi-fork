# Implementation Plan: Seamless & Lightweight Modern Transitions

**Phase 1: Navigator Refinement (Navigator.kt)**
- [x] Task: Удалить масштабирование (Scale) и задержку входа из Navigator.kt.
    - [x] Установить MODERN_ENTER_DURATION = 300 и MODERN_EXIT_DURATION = 300.
    - [x] Установить MODERN_ENTER_DELAY = 0.
    - [x] Удалить scaleIn и scaleOut из modernSharedAxisX.
    - [x] В modernSharedAxisX установить targetContentZIndex = 1f для входящего экрана.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Navigator Refinement' (Protocol in workflow.md) [Commit: 1e07a6e28]

**Phase 2: HomeScreen Refinement (HomeScreen.kt)**
- [x] Task: Синхронизировать переходы вкладок в HomeScreen.kt.
    - [x] Установить TAB_MODERN_ENTER_DURATION = 300 и TAB_MODERN_EXIT_DURATION = 300.
    - [x] Удалить scaleIn и scaleOut.
    - [x] Установить targetContentZIndex = 1f для входящей вкладки.
- [x] Task: Conductor - User Manual Verification 'Phase 2: HomeScreen Refinement' (Protocol in workflow.md) [Commit: 1e07a6e28]

**Phase 3: Final Verification**
- [x] Task: Убедиться в отсутствии просветов при быстрой навигации.
- [x] Task: Проверить плавность перехода при переключении между соседними вкладками.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Final Verification' (Protocol in workflow.md) [AssembleDebug: OK]
