# Implementation Plan: Aurora Modern Transitions Refinement

**Phase 1: Core Navigation Logic (Navigator.kt)**
- [x] Task: Добавить Push/Pop направление в modernSharedAxisX в Navigator.kt.
    - [x] Изменить сигнатуру modernSharedAxisX для приема forward: Boolean.
    - [x] Реализовать Push анимацию (Scale 0.95 -> 1.0, FadeIn, Offset 30dp -> 0).
    - [x] Реализовать Pop анимацию (Scale 1.0 -> 0.95, FadeOut, Offset 0 -> -30dp).
    - [x] Применить CubicBezier(0.4, 0.0, 0.2, 1.0) ко всем animationSpec.
- [x] Task: Обновить вызовы modernSharedAxisX в DefaultNavigatorScreenTransition.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Core Navigation Logic' (Protocol in workflow.md) [Commit: 34417f340]

**Phase 2: Tabbed Screen Refinement (HomeScreen.kt)**
- [x] Task: Синхронизировать анимации вкладок в HomeScreen.kt с новыми параметрами.
    - [x] Обновить Modern блок в HomeScreen (анимация scale и fade для вкладок).
    - [x] Убедиться, что направление переключения вкладок (индекс влево/вправо) учитывается.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Tabbed Screen Refinement' (Protocol in workflow.md) [Commit: 11d1c2f5e]

**Phase 3: Quality Control & Regressions**
- [x] Task: Проверить работу «Классического» режима во всех сценариях.
- [x] Task: Финальная проверка плавности (60 FPS) и отсутствия артефактов при быстрой навигации.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Quality Control & Regressions' (Protocol in workflow.md) [Final Build: assembleDebug OK]
