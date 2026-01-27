# План реализации системы достижений для Aniyomi

## Обзор

Создание комплексной системы достижений (achievements) для приложения Aniyomi, которая награждает пользователей за просмотр аниме и чтение манги. Система включает разнообразные типы достижений, комбинированный UI с сеткой и категориями, систему наград (значки + очки + разблокировки), и ретроактивный расчёт для существующих пользователей.

**Дата создания:** 2025-01-26
**Версия:** 1.0
**Статус:** Черновик плана

---

## Требования пользователя

### Функциональные требования

1. **Типы достижений:**
   - Количество: прочтено X глав, просмотрено Y серий
   - События: первое добавление, первое завершение, первая загрузка
   - Разнообразие: 5 разных жанров, 10 разных источников
   - Временные: чтение подряд N дней
   - Комбо-достижения: комбинированные аниме+манга

2. **Хранение и синхронизация:**
   - Только внутренние достижения (без синхронизации с MAL/Anilist)
   - Включены в бэкап приложения
   - Ретроактивный расчёт для существующих пользователей

3. **UI/UX:**
   - Сетка с категориями (MVP)
   - Категории: Аниме / Манга / Общие / Секретные
   - Timeline можно добавить в будущих версиях

4. **Система наград:**
   - Значки (badges) за достижения
   - Очки (points) для рейтинга
   - Разблокировки: темы, значки профиля, display preferences
   - Комбинация всех типов наград

### Нефункциональные требования

- Производительность: <300ms загрузка экрана, <500ms срабатывание триггера
- Масштабируемость: поддержка 50-100 типов достижений
- Расширяемость: добавление нового достижения <30 минут (data-driven)
- Надёжность: выживание при крашах, целостность данных

---

## Архитектура

### Структура модулей

```
domain/
  src/main/java/.../achievement/
    model/
      Achievement.kt                    # Определение достижения
      AchievementProgress.kt             # Прогресс пользователя
      AchievementType.kt                 # Enum: QUANTITY, EVENT, DIVERSITY, etc.
      AchievementCategory.kt             # Enum: ANIME, MANGA, BOTH, SECRET
      AchievementReward.kt               # Награда (значок, очки, разблокировка)
    repository/
      AchievementRepository.kt           # Интерфейс репозитория

data/
  src/main/java/.../achievement/
    database/
      AchievementsTable.sq              # SqlDelight схема: определения достижений
      AchievementProgressTable.sq       # SqlDelight схема: прогресс
      AchievementEventsTable.sq         # SqlDelight схема: история событий
    handler/
      AchievementHandler.kt              # Логика проверки и разблокировки
      AchievementEventBus.kt             # Flow-based event система
      AchievementCalculator.kt           # Ретроактивный расчёт
    repository/
      AchievementRepositoryImpl.kt       # Реализация репозитория

presentation/
  src/main/java/.../achievement/
    ui/
      AchievementScreen.kt               # Главный экран (Compose)
      AchievementGrid.kt                 # Grid компонент
      AchievementCard.kt                 # Карточка достижения
      AchievementDetailDialog.kt         # Детали достижения
    screenmodel/
      AchievementScreenModel.kt          # State management

app/
  src/main/assets/achievements/
    achievements.json                    # Data-driven определения
```

### База данных

**Отдельная БД:** `achievements.db` (не в существующих anime/manga DB)

```sql
-- Таблица определений достижений
CREATE TABLE achievements (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,                  -- 'quantity', 'event', 'diversity', 'streak'
    category TEXT NOT NULL,              -- 'anime', 'manga', 'both', 'secret'
    threshold INTEGER,
    points INTEGER NOT NULL DEFAULT 0,
    title TEXT NOT NULL,
    description TEXT,
    badge_icon TEXT,                     -- Имя векторной иконки
    is_hidden INTEGER DEFAULT 0,         -- Секретные достижения
    is_secret INTEGER DEFAULT 0,         -- Показывать только после разблокировки
    unlockable_id TEXT,                  -- Что разблокируется (опционально)
    version INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL
);

-- Таблица прогресса пользователя
CREATE TABLE achievement_progress (
    achievement_id TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    max_progress INTEGER NOT NULL DEFAULT 100,  -- Для прогресс-бара
    is_unlocked INTEGER DEFAULT 0,
    unlocked_at INTEGER,
    last_updated INTEGER NOT NULL,
    PRIMARY KEY (achievement_id),
    FOREIGN KEY (achievement_id) REFERENCES achievements(id) ON DELETE CASCADE
);

-- Таблица истории событий (для аудита)
CREATE TABLE achievement_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    achievement_id TEXT NOT NULL,
    event_type TEXT NOT NULL,            -- 'triggered', 'progress_updated', 'unlocked'
    event_data TEXT,                     -- JSON
    timestamp INTEGER NOT NULL
);

-- Индексы для производительности
CREATE INDEX achievement_progress_unlocked ON achievement_progress(is_unlocked);
CREATE INDEX achievement_events_type ON achievement_events(event_type);
CREATE INDEX achievement_events_timestamp ON achievement_events(timestamp);
```

### Data-Driven определения достижений

**Файл:** `app/src/main/assets/achievements/achievements.json`

```json
{
  "version": 1,
  "achievements": [
    {
      "id": "first_chapter",
      "type": "event",
      "category": "manga",
      "threshold": 1,
      "points": 10,
      "title": "Первые шаги",
      "description": "Прочитайте свою первую главу",
      "badge_icon": "ic_badge_first_chapter",
      "is_hidden": false,
      "is_secret": false
    },
    {
      "id": "read_100_chapters",
      "type": "quantity",
      "category": "manga",
      "threshold": 100,
      "points": 100,
      "title": "Любитель манги",
      "description": "Прочитайте 100 глав",
      "badge_icon": "ic_badge_100_chapters",
      "is_hidden": false,
      "is_secret": false
    },
    {
      "id": "genre_explorer",
      "type": "diversity",
      "category": "both",
      "threshold": 5,
      "points": 150,
      "title": "Исследователь жанров",
      "description": "Читайте 5 разных жанров",
      "badge_icon": "ic_badge_genres",
      "is_hidden": false,
      "is_secret": false
    },
    {
      "id": "content_master",
      "type": "quantity",
      "category": "both",
      "threshold": 1000,
      "points": 500,
      "title": "Мастер контента",
      "description": "Прочитайте/посмотрите 1000 единиц контента",
      "badge_icon": "ic_badge_master",
      "unlockable_id": "theme_master",
      "is_hidden": false,
      "is_secret": false
    }
  ]
}
```

---

## План реализации

### Фаза 1: Foundation (Основа)

**Задачи:**

1. **Создание структуры домена**
   - [ ] Создать пакет `domain/achievement/model/`
   - [ ] Создать модели: `Achievement`, `AchievementProgress`, `AchievementType`, `AchievementCategory`, `AchievementReward`
   - [ ] Создать интерфейс `AchievementRepository` в `domain/achievement/repository/`

2. **Настройка базы данных**
   - [ ] Создать SqlDelight схемы в `data/achievement/database/`
   - [ ] Создать `achievements.sq` с таблицами: `achievements`, `achievement_progress`, `achievement_events`
   - [ ] Настроить генерацию кода SqlDelight
   - [ ] Создать `AchievementsDatabase` класс для инициализации отдельной БД

3. **Реализация репозитория**
   - [ ] Создать `AchievementRepositoryImpl` в `data/achievement/repository/`
   - [ ] Реализовать базовые CRUD операции
   - [ ] Добавить Flow-based методы для реактивных обновлений
   - [ ] Написать юнит-тесты для репозитория

4. **Data-driven загрузка**
   - [ ] Создать `achievements.json` в `app/src/main/assets/achievements/`
   - [ ] Создать `AchievementLoader` для парсинга JSON и заполнения БД
   - [ ] Добавить логику миграции версий определений

**Критерии завершения:**
- База данных создаётся без ошибок
- Репозиторий может сохранять и читать достижения
- JSON определения загружаются в БД при запуске

---

### Фаза 2: Event System (Система событий)

**Задачи:**

1. **Создание Event Bus**
   - [ ] Создать `AchievementEventBus` на основе Kotlin Flow
   - [ ] Определить типы событий: `ChapterReadEvent`, `EpisodeWatchedEvent`, `LibraryAddedEvent`, `CategoryAddedEvent`
   - [ ] Создать sealed class `AchievementEvent`

2. **Интеграция с существующим кодом**
   - [ ] Добавить публикации событий в `MangaHistoryRepository` при чтении главы
   - [ ] Добавить публикации событий в `AnimeHistoryRepository` при просмотре серии
   - [ ] Добавить публикации событий в `MangaRepository` при добавлении в библиотеку
   - [ ] Добавить публикации событий в `AnimeRepository` при добавлении в библиотеку

3. **Achievement Handler**
   - [ ] Создать `AchievementHandler` для обработки событий
   - [ ] Реализовать логику проверки условий достижений
   - [ ] Добавить систему приоритетов для batch обработки
   - [ ] Создать worker queue для асинхронной проверки

**Критерии завершения:**
- События корректно публикуются из существующих кодов
- AchievementHandler получает и обрабатывает события
- Проверка достижений работает асинхронно без блокировки UI

---

### Фаза 3: Achievement Logic (Логика достижений)

**Задачи:**

1. **Quantity Checker**
   - [ ] Создать `QuantityAchievementChecker`
   - [ ] Реализовать проверку "X глав прочитано"
   - [ ] Реализовать проверку "Y серий просмотрено"
   - [ ] Оптимизировать запросы для больших библиотек

2. **Event Checker**
   - [ ] Создать `EventAchievementChecker`
   - [ ] Реализовать проверку одноразовых событий
   - [ ] Добавить отслеживание "первых" действий

3. **Diversity Checker**
   - [ ] Создать `DiversityAchievementChecker`
   - [ ] Реализовать проверку "N разных жанров"
   - [ ] Реализовать проверку "N разных источников"
   - [ ] Добавить кеширование метрик разнообразия

4. **Streak Checker**
   - [ ] Создать `StreakAchievementChecker`
   - [ ] Реализовать проверку "N дней подряд"
   - [ ] Добавить отслеживание активности по дням

5. **Retroactive Calculator**
   - [ ] Создать `AchievementCalculator` для ретроактивного расчёта
   - [ ] Реализовать сканирование истории при первом запуске
   - [ ] Добавить progress indicator для долгих расчётов
   - [ ] Сохранять результаты в achievement_progress

**Критерии завершения:**
- Все типы достижений корректно проверяются
- Ретроактивный расчёт работает для существующих пользователей
- Система не создаёт значительной нагрузки на CPU/БД

---

### Фаза 4: UI Implementation (Интерфейс)

**Задачи:**

1. **Базовый экран**
   - [ ] Создать `AchievementScreen.kt` с Jetpack Compose
   - [ ] Создать `AchievementScreenModel.kt` с StateFlow
   - [ ] Добавить навигацию из `MoreScreenAurora.kt`

2. **Grid Layout**
   - [ ] Создать `AchievementGrid.kt` с LazyVerticalGrid
   - [ ] Реализовать категории (TabRow): Аниме / Манга / Общие / Секретные
   - [ ] Добавить фильтры: Все / Разблокированные / Заблокированные

3. **Карточки**
   - [ ] Создать `AchievementCard.kt` в Aurora стиле
   - [ ] Добавить иконку, название, описание, прогресс-бар
   - [ ] Добавить визуальное различие между разблокированными/заблокированными
   - [ ] Добавить анимацию разблокировки

4. **Детали**
   - [ ] Создать `AchievementDetailDialog.kt`
   - [ ] Показывать полную информацию, дату разблокировки, награды
   - [ ] Добавить кнопку "Поделиться" (опционально)

5. **Уведомления**
   - [ ] Создать систему toast/banner уведомлений
   - [ ] Батчинг: "3 достижения разблокировано!" вместо 3 отдельных
   - [ ] Анимация появления

**Критерии завершения:**
- Экран достижений открывается из More меню
- Grid корректно отображается с категориями
- Карточки соответствуют Aurora дизайну
- Уведомления не спамят

---

### Фаза 5: Reward System (Система наград)

**Задачи:**

1. **Points System**
   - [ ] Создать `UserPoints` модель для хранения очков
   - [ ] Добавить таблицу `user_stats` в achievements.db
   - [ ] Создать `PointsManager` для управления очками
   - [ ] Отображать общий счёт на экране достижений

2. **Badge System**
   - [ ] Создать векторные иконки для всех достижений
   - [ ] Добавить preview иконок в деталях достижения
   - [ ] Создать экран "Badge Collection" (опционально)

3. **Unlockables**
   - [ ] Определить что можно разблокировать (начать с простого)
   - [ ] Создать `UnlockablesManager`
   - [ ] Добавить разблокировку:
     - [ ] Специальная тема "Achievement Master"
     - [ ] Бейдж на профиле
     - [ ] Display preference (показывать статистику на главной)
   - [ ] Интегрировать с существующей системой настроек

**Критерии завершения:**
- Очки корректно начисляются
- Значки отображаются на карточках
- Разблокировки работают и сохраняются

---

### Фаза 6: Polish & Testing (Отладка и тестирование)

**Задачи:**

1. **Производительность**
   - [ ] Профилировать загрузку экрана (цель <300ms)
   - [ ] Оптимизировать запросы к БД
   - [ ] Добавить пагинацию для больших списков
   - [ ] Кеширование состояний достижений

2. **Интеграционное тестирование**
   - [ ] Тест ретроактивного расчёта
   - [ ] Тест пакетных операций
   - [ ] Тест восстановления из бэкапа
   - [ ] Тест очистки истории

3. **UI тестирование**
   - [ ] Проверить на больших библиотеках (1000+ элементов)
   - [ ] Проверить на секретных достижениях
   - [ ] Проверить на разных размерах экранов
   - [ ] Проверить при перевороте экрана

4. **Документация**
   - [ ] Добавить комментарии в код
   - [ ] Создать гайд по добавлению новых достижений
   - [ ] Обновить AGENTS.md (если применимо)

**Критерии завершения:**
- Все тесты проходят
- Производительность соответствует требованиям
- Код задокументирован

---

## Примеры достижений

### Количество (Quantity)
- "Новичок" - 10 глав/серий
- "Любитель" - 100 глав/серий
- "Эксперт" - 500 глав/серий
- "Мастер" - 1000 глав/серий
- "Легенда" - 5000 глав/серий

### События (Event)
- "Первые шаги" - первая прочитанная глава/просмотренная серия
- "Коллекционер" - добавлено первое в библиотеку
- "Загрузчик" - первая скачанная глава/серия
- "Завершатель" - первая завершённая серия/манга
- "Марафонец" - 10 глав/серий за один день

### Разнообразие (Diversity)
- "Исследователь жанров" - 5 разных жанров
- "Гурман" - 10 разных источников
- "Полиглот" - контент на 3+ языках
- "Охотник за редкостями" - 5 редких тегов

### Комбо (Both Anime + Manga)
- "Всесторонний зритель" - 100 аниме + 100 манги в библиотеке
- "Контент-пожиратель" - 1000 глав+серий всего
- "Верный фанат" - активен 30 дней подряд

### Секретные (Secret)
- "Тёмная сторона" - найти скрытый исходник
- "Перфекционист" - 100% прогресс в 10 тайтлах
- (解锁方式 скрыт до разблокировки)

---

## Риски и митигация

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|-----------|
| Ретроактивный расчёт занимает слишком много времени | Средняя | Высокое | Batch обработка в фоне, progress UI |
| Слишком много уведомлений при первом запуске | Высокая | Среднее | Батчинг, max 3 уведомления за раз |
| Производительность БД с большими библиотеками | Средняя | Среднее | Индексы, кеширование, lazy loading |
| Пользователи хотят разблокировать секретные достижения | Низкая | Низкое | Подсказки в описаниях, сообщество |
| Сложность добавления новых достижений | Средняя | Среднее | Data-driven JSON, гайд для разработчиков |

---

## Следующие шаги

После утверждения плана:

1. **Создать задачи в трекере** (GitHub Issues / Jira / etc.)
2. **Настроить ветвление Git** (feature/achievement-system)
3. **Начать с Фазы 1** (Foundation)
4. **Регулярные ревью** после каждой фазы

---

## Приложение: Структура файлов

```
aniyomi/
├── domain/src/main/java/.../achievement/
│   ├── model/
│   │   ├── Achievement.kt
│   │   ├── AchievementProgress.kt
│   │   ├── AchievementType.kt
│   │   ├── AchievementCategory.kt
│   │   └── AchievementReward.kt
│   └── repository/
│       └── AchievementRepository.kt
│
├── data/src/main/java/.../achievement/
│   ├── database/
│   │   ├── AchievementsTable.sq
│   │   ├── AchievementProgressTable.sq
│   │   └── AchievementEventsTable.sq
│   ├── handler/
│   │   ├── AchievementHandler.kt
│   │   ├── AchievementEventBus.kt
│   │   ├── AchievementCalculator.kt
│   │   └── checkers/
│   │       ├── QuantityAchievementChecker.kt
│   │       ├── EventAchievementChecker.kt
│   │       ├── DiversityAchievementChecker.kt
│   │       └── StreakAchievementChecker.kt
│   └── repository/
│       └── AchievementRepositoryImpl.kt
│
├── presentation/src/main/java/.../achievement/
│   ├── ui/
│   │   ├── AchievementScreen.kt
│   │   ├── AchievementGrid.kt
│   │   ├── AchievementCard.kt
│   │   ├── AchievementDetailDialog.kt
│   │   └── components/
│   │       ├── AchievementFilterTabRow.kt
│   │       └── AchievementUnlockBanner.kt
│   └── screenmodel/
│       └── AchievementScreenModel.kt
│
└── app/src/main/
    ├── assets/achievements/
    │   └── achievements.json
    └── res/
        └── drawable/
            └── ic_badge_*.xml (векторные иконки)
```

---

**Конец плана**
