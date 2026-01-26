# Архитектура системы достижений

## Обзор

Система достижений (Achievement System) - это модульная система для отслеживания, проверки и награждения пользователей за их активность в приложении Aniyomi.

## Архитектурные принципы

### 1. Разделение ответственности

Система разделена на слои:

```
┌─────────────────────────────────────────────────┐
│              Presentation Layer                  │
│  (AchievementScreen, AchievementCard, etc.)     │
└─────────────────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│                 Domain Layer                     │
│  (Achievement, AchievementProgress, Repository) │
└─────────────────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│                  Data Layer                      │
│  (AchievementHandler, Database, EventBus)       │
└─────────────────────────────────────────────────┘
```

### 2. Event-Driven Architecture

Система использует событийно-ориентированную архитектуру:

```
User Action → Event → EventBus → Handler → Checker → Progress Update → UI Update
```

### 3. Reactive Programming

Использование Kotlin Flow для реактивных обновлений:

```kotlin
// В AchievementScreenModel
combine(
    repository.getAll(),
    repository.getAllProgress(),
    pointsManager.subscribeToPoints(),
) { achievements, progress, userPoints ->
    // Объединение потоков данных
}
```

## Компоненты системы

### AchievementHandler

**Назначение:** Главный обработчик достижений

**Ответственности:**
- Подписка на события из EventBus
- Делегирование проверки соответствующим checker'ам
- Обновление прогресса в базе данных
- Уведомление о разблокировке

**Ключевые методы:**
```kotlin
class AchievementHandler(
    private val eventBus: AchievementEventBus,
    private val repository: AchievementRepository,
    private val diversityChecker: DiversityAchievementChecker,
    private val streakChecker: StreakAchievementChecker,
    private val pointsManager: PointsManager,
    private val unlockableManager: UnlockableManager,
) {
    fun start() // Запуск обработки событий
    var unlockCallback: AchievementUnlockCallback? // Callback для уведомлений
}
```

**Поток обработки:**
```
EventBus.receive()
    ↓
processEvent(event)
    ↓
checkAndUpdateProgress(achievement, event)
    ↓
calculateProgress(achievement, event, currentProgress)
    ↓
repository.insertOrUpdateProgress()
    ↓
onAchievementUnlocked()
```

### AchievementEventBus

**Назначение:** Шина событий для передачи событий достижения

**Реализация:**
```kotlin
class AchievementEventBus {
    private val _events = MutableSharedFlow<AchievementEvent>()
    val events: SharedFlow<AchievementEvent> = _events.asSharedFlow()

    suspend fun send(event: AchievementEvent) {
        _events.emit(event)
    }
}
```

**Преимущества:**
- Развязка компонентов (loose coupling)
- Асинхронная обработка
- Возможность множественной подписки

### AchievementCalculator

**Назначение:** Вычисление прогресса для достижений

**Ответственности:**
- Агрегация статистики пользователя
- Вычисление текущего прогресса
- Поддержка разных типов достижений

```kotlin
class AchievementCalculator(
    private val repository: AchievementRepository
) {
    suspend fun getChapterReadCount(): Int
    suspend fun getEpisodeWatchedCount(): Int
    suspend fun getMangaInLibraryCount(): Int
    suspend fun getAnimeInLibraryCount(): Int
}
```

### DiversityAchievementChecker

**Назначение:** Проверка достижений разнообразия

**Типы разнообразия:**
- Жанры (Genre)
- Источники (Source)
- Авторы (Author)

```kotlin
class DiversityAchievementChecker(
    private val calculator: AchievementCalculator
) {
    fun getMangaGenreDiversity(): Int
    fun getAnimeGenreDiversity(): Int
    fun getGenreDiversity(): Int
    fun getMangaSourceDiversity(): Int
    fun getAnimeSourceDiversity(): Int
    fun getSourceDiversity(): Int
}
```

### StreakAchievementChecker

**Назначение:** Отслеживание серий активности (streaks)

**Функционал:**
- Логирование активности пользователя
- Вычисление текущей серии
- Определение пропущенных дней

```kotlin
class StreakAchievementChecker(
    private val context: Context,
    private val preferences: SharedPreferences
) {
    fun logChapterRead()
    fun logEpisodeWatched()
    fun getCurrentStreak(): Int
    fun logActivity(type: String)

    private fun shouldResetStreak(): Boolean
    private fun updateStreak()
}
```

### PointsManager

**Назначение:** Управление очками пользователя

**Функционал:**
- Добавление очков
- Подписка на изменения очков
- Вычисление уровня

```kotlin
class PointsManager(
    private val context: Context,
    private val preferences: SharedPreferences
) {
    fun addPoints(points: Int)
    fun incrementUnlocked()
    fun subscribeToPoints(): Flow<UserPoints>
    fun getPoints(): UserPoints

    private fun calculateLevel(totalPoints: Int): Int
    private fun savePoints()
}
```

### AchievementLoader

**Назначение:** Загрузка достижений из JSON

**Функционал:**
- Парсинг JSON файла
- Версионирование
- Миграция данных

```kotlin
class AchievementLoader(
    private val context: Context,
    private val repository: AchievementRepository
) {
    suspend fun loadAchievements(): Result<Int>
    fun parseAchievements(json: String): List<AchievementJson>
    suspend fun migrateToDatabase(version: Int)

    private fun getAssetVersion(): Int
    private fun saveVersion(version: Int)
}
```

### UnlockableManager

**Назначение:** Управление разблокируемым контентом

**Функционал:**
- Разблокировка тем
- Разблокировка значков
- Разблокировка стилей отображения

```kotlin
class UnlockableManager(
    private val context: Context,
    private val preferences: SharedPreferences
) {
    fun unlockAchievementRewards(achievement: Achievement)
    fun isUnlocked(unlockableId: String): Boolean
    fun getUnlockedItems(): Set<String>

    private fun unlockTheme(themeId: String)
    private fun unlockBadge(badgeId: String)
    private fun unlockDisplayStyle(styleId: String)
}
```

## Модели данных

### Achievement

**Модель достижения:**

```kotlin
@Immutable
data class Achievement(
    val id: String,                    // Уникальный ID
    val type: AchievementType,          // Тип (quantity, event, diversity, streak)
    val category: AchievementCategory,  // Категория (anime, manga, both, secret)
    val threshold: Int?,                // Пороговое значение
    val points: Int,                    // Очки за получение
    val title: String,                  // Название
    val description: String?,           // Описание
    val badgeIcon: String?,             // Иконка
    val isHidden: Boolean,              // Скрыто ли
    val isSecret: Boolean,              // Секретное ли
    val unlockableId: String?,          // ID награды
    val version: Int,                   // Версия
    val createdAt: Long,                // Дата создания
)
```

### AchievementProgress

**Модель прогресса:**

```kotlin
@Immutable
data class AchievementProgress(
    val achievementId: String,  // ID достижения
    val progress: Int,          // Текущий прогресс
    val maxProgress: Int,       // Максимальный прогресс
    val isUnlocked: Boolean,    // Разблокировано ли
    val unlockedAt: Long?,      // Время разблокировки
    val lastUpdated: Long,      // Время обновления
)
```

### UserPoints

**Модель очков пользователя:**

```kotlin
@Immutable
data class UserPoints(
    val totalPoints: Int = 0,          // Общие очки
    val unlockedCount: Int = 0,        // Количество разблокированных
    val level: Int = 1,                // Уровень
    val nextLevelThreshold: Int = 100, // Следующий уровень
) {
    val progressToNextLevel: Float
        get() = (totalPoints % nextLevelThreshold).toFloat() / nextLevelThreshold
}
```

### AchievementEvent

**Модель события:**

```kotlin
sealed class AchievementEvent {
    data class ChapterRead(
        val chapterId: Long,
        val mangaId: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : AchievementEvent()

    data class EpisodeWatched(
        val episodeId: Long,
        val animeId: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : AchievementEvent()

    data class LibraryAdded(
        val type: AchievementCategory,
        val id: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : AchievementEvent()

    data class LibraryRemoved(
        val type: AchievementCategory,
        val id: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : AchievementEvent()

    data class MangaCompleted(
        val mangaId: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : AchievementEvent()

    data class AnimeCompleted(
        val animeId: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : AchievementEvent()
}
```

## База данных

### Схема

```sql
-- Таблица достижений
CREATE TABLE achievements (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    category TEXT NOT NULL,
    threshold INTEGER,
    points INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    badge_icon TEXT,
    is_hidden INTEGER NOT NULL DEFAULT 0,
    is_secret INTEGER NOT NULL DEFAULT 0,
    unlockable_id TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL
);

-- Таблица прогресса
CREATE TABLE achievement_progress (
    achievement_id TEXT PRIMARY KEY,
    progress INTEGER NOT NULL DEFAULT 0,
    max_progress INTEGER NOT NULL DEFAULT 0,
    is_unlocked INTEGER NOT NULL DEFAULT 0,
    unlocked_at INTEGER,
    last_updated INTEGER NOT NULL,
    FOREIGN KEY (achievement_id) REFERENCES achievements(id) ON DELETE CASCADE
);

-- Лог активности (для streak)
CREATE TABLE achievement_activity_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL,
    type TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);

CREATE INDEX idx_activity_log_date ON achievement_activity_log(date);
```

### Миграции

Версионирование схемы через `AchievementsDatabase.sqm`:

```sql
-- Версия 1
CREATE TABLE achievements (...);

-- Версия 2 (пример миграции)
ALTER TABLE achievements ADD COLUMN unlockable_id TEXT;
```

## Flow данных

### 1. Инициализация

```
Application.start()
    ↓
AchievementHandler.start()
    ↓
AchievementLoader.loadAchievements()
    ↓
AchievementRepository.insertAchievements()
```

### 2. Пользовательское действие

```
User reads chapter
    ↓
ChapterReader.onChapterRead()
    ↓
AchievementEventBus.send(ChapterRead)
    ↓
AchievementHandler.processEvent()
    ↓
AchievementCalculator.getChapterReadCount()
    ↓
AchievementHandler.checkAndUpdateProgress()
    ↓
AchievementRepository.insertOrUpdateProgress()
    ↓
PointsManager.addPoints()
    ↓
UnlockableManager.unlockAchievementRewards()
    ↓
unlockCallback.onAchievementUnlocked()
```

### 3. UI обновление

```
AchievementRepository.getAllProgress()
    ↓
AchievementScreenModel.combine()
    ↓
AchievementScreenModel.state.update()
    ↓
AchievementScreen recompose
    ↓
AchievementCard recompose
```

## Производительность

### Оптимизации

1. **LazyVerticalGrid**
   - Виртуализация длинных списков
   - Реюз карточек

2. **Flow операторы**
   - `combine` для агрегации
   - `distinctUntilChanged` для избежания лишних обновлений

3. **SQLite индексы**
   - Индекс на `achievement_id`
   - Индекс на дату в `activity_log`

4. **Корутины**
   - Асинхронная обработка событий
   - SupervisorJob для изоляции ошибок

### Мониторинг производительности

```kotlin
// Логирование времени выполнения
val startTime = System.currentTimeMillis()
// ... операция ...
val duration = System.currentTimeMillis() - startTime
logcat(LogPriority.DEBUG) { "Operation took ${duration}ms" }
```

## Безопасность

### Валидация данных

```kotlin
fun validateAchievement(achievement: Achievement): Boolean {
    return achievement.id.isNotBlank() &&
           achievement.points > 0 &&
           achievement.title.isNotBlank()
}
```

### Обработка ошибок

```kotlin
eventBus.events
    .catch { e ->
        logcat(LogPriority.ERROR, e) { "Error in achievement event stream" }
    }
    .collect { event ->
        try {
            processEvent(event)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error processing achievement event: $event" }
        }
    }
```

## Тестирование

### Unit-тесты

- `AchievementRepositoryImplTest` - тесты репозитория
- `PointsManagerTest` - тесты менеджера очков
- `DiversityAchievementCheckerTest` - тесты проверщика разнообразия
- `StreakAchievementCheckerTest` - тесты проверщика серий
- `AchievementCalculatorTest` - тесты калькулятора

### UI-тесты

- `AchievementScreenTest` - тесты экрана достижений
- Тестирование отображения карточек
- Тестирование диалогов
- Тестирование производительности

### Интеграционные тесты

```kotlin
@Test
fun testAchievementUnlockFlow() = runTest {
    // 1. Создаем достижение
    val achievement = createMockAchievement()

    // 2. Отправляем событие
    eventBus.send(AchievementEvent.ChapterRead(1, 1))

    // 3. Проверяем прогресс
    val progress = repository.getProgress(achievement.id).first()
    assertEquals(1, progress.progress)

    // 4. Проверяем очки
    val points = pointsManager.getPoints()
    assertEquals(achievement.points, points.totalPoints)
}
```

## Расширение системы

### Добавление нового типа достижения

1. Добавьте тип в `AchievementType`:
```kotlin
enum class AchievementType {
    QUANTITY,
    EVENT,
    DIVERSITY,
    STREAK,
    YOUR_NEW_TYPE, // Добавьте сюда
}
```

2. Создайте checker:
```kotlin
class YourNewTypeChecker {
    fun calculateProgress(): Int { /* ... */ }
}
```

3. Добавьте логику в `AchievementHandler`:
```kotlin
private fun calculateProgress(/* ... */): Int {
    return when (achievement.type) {
        AchievementType.YOUR_NEW_TYPE -> yourNewTypeChecker.calculateProgress()
        // ...
    }
}
```

### Добавление нового события

1. Добавьте событие в `AchievementEvent`:
```kotlin
sealed class AchievementEvent {
    data class YourNewEvent(
        val param: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : AchievementEvent()
}
```

2. Обработайте в `AchievementHandler`:
```kotlin
private suspend fun processEvent(event: AchievementEvent) {
    when (event) {
        is AchievementEvent.YourNewEvent -> handleYourNewEvent(event)
        // ...
    }
}
```

## Диагностика

### Логирование

Система использует logcat для логирования:

```kotlin
logcat(LogPriority.INFO) { "Achievement unlocked: ${achievement.title}" }
logcat(LogPriority.ERROR, e) { "Failed to add points" }
```

### Отладка

Для отладки достижений:

```bash
# Фильтр логов по тегу
adb logcat | grep "Achievement"

# Или по ключевым словам
adb logcat | grep -E "achievement|progress|unlock"
```

## Будущие улучшения

### Планируемые функции

1. **Социальные функции**
   - Таблица лидеров
   - Сравнение с друзьями

2. **Уведомления**
   - Push-уведомления о достижениях
   - Баннер разблокировки

3. **Аналитика**
   - Статистика по достижениям
   - Графики прогресса

4. **Кастомизация**
   - Пользовательские достижения
   - Создание челленджей

### Технический долг

1. Перейти на Room вместо SQLite Delight
2. Добавить кэширование для частых запросов
3. Оптимизировать запросы к базе данных
4. Добавить unit-тесты для всех компонентов

## Заключение

Система достижений спроектирована с учетом принципов чистой архитектуры, реактивного программирования и событийно-ориентированной архитектуры. Она легко расширяема и поддерживает различные типы достижений.
