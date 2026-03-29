# Implementation Plan: Aurora Modern Transitions Refinement

**Phase 1: Core Navigation Logic (Navigator.kt)**
- [ ] Task: Добавить Push/Pop направление в modernSharedAxisX в Navigator.kt.
    - [ ] Изменить сигнатуру modernSharedAxisX для приема forward: Boolean.
    - [ ] Реализовать Push анимацию (Scale 0.95 -> 1.0, FadeIn, Offset 30dp -> 0).
    - [ ] Реализовать Pop анимацию (Scale 1.0 -> 0.95, FadeOut, Offset 0 -> -30dp).
    - [ ] Применить CubicBezier(0.4, 0.0, 0.2, 1.0) ко всем animationSpec.
- [ ] Task: Обновить вызовы modernSharedAxisX в DefaultNavigatorScreenTransition.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Core Navigation Logic' (Protocol in workflow.md)

**Phase 2: Tabbed Screen Refinement (HomeScreen.kt)**
- [ ] Task: Синхронизировать анимации вкладок в HomeScreen.kt с новыми параметрами.
    - [ ] Обновить Modern блок в HomeScreen (анимация scale и fade для вкладок).
    - [ ] Убедиться, что направление переключения вкладок (индекс влево/вправо) учитывается.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Tabbed Screen Refinement' (Protocol in workflow.md)

**Phase 3: Quality Control & Regressions**
- [ ] Task: Проверить работу «Классического» режима во всех сценариях.
- [ ] Task: Финальная проверка плавности (60 FPS) и отсутствия артефактов при быстрой навигации.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Quality Control & Regressions' (Protocol in workflow.md)
