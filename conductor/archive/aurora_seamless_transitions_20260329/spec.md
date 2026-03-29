# Track Specification: Seamless & Lightweight Modern Transitions

**1. Overview**
Создание бесшовного и легкого интерфейса за счет удаления масштабирования и устранения визуальных просветов между экранами.

**2. Functional Requirements**
- **No Scale:** Полное удаление scaleIn и scaleOut.
- **Zero Gap (Seamless):**
    - Удаление MODERN_ENTER_DELAY (задержки входа).
    - Уравнение длительности (300ms для обоих этапов), чтобы они работали синхронно.
    - Использование targetContentZIndex = 1f для входящего экрана (он будет перекрывать уходящий, закрывая собой фон).
- **Subtle Motion:** Сохранение мягкого горизонтального смещения (X-axis).
