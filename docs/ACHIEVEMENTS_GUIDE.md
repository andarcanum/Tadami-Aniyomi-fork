# Руководство по добавлению достижений

Это руководство поможет вам добавить новые достижения в приложение Aniyomi.

## Содержание

- [Структура системы](#структура-системы)
- [Как добавить новое достижение](#как-новое-достижение)
- [Типы достижений](#типы-достижений)
- [Категории](#категории)
- [Проверка достижений](#проверка-достижений)
- [События системы](#события-системы)
- [Награды](#награды)

---

## Структура системы

Система достижений состоит из следующих компонентов:

```
data/src/main/java/tachiyomi/data/achievement/
├── handler/
│   ├── AchievementHandler.kt          # Главный обработчик достижений
│   ├── AchievementEventBus.kt         # Шина событий
│   ├── AchievementCalculator.kt       # Калькулятор прогресса
│   ├── PointsManager.kt               # Менеджер очков
│   └── checkers/
│       ├── DiversityAchievementChecker.kt  # Проверка разнообразия
│       └── StreakAchievementChecker.kt     # Проверка серий
├── database/
│   └── AchievementsDatabase.kt        # База данных SQLite
├── loader/
│   └── AchievementLoader.kt           # Загрузчик из JSON
└── repository/
    └── AchievementRepositoryImpl.kt   # Реализация репозитория

domain/src/main/java/tachiyomi/domain/achievement/
├── model/
│   ├── Achievement.kt                 # Модель достижения
│   ├── AchievementProgress.kt         # Модель прогресса
│   ├── AchievementType.kt             # Типы достижений
│   ├── AchievementCategory.kt         # Категории
│   └── UserPoints.kt                  # Очки пользователя
└── repository/
    └── AchievementRepository.kt       # Интерфейс репозитория

app/src/main/java/eu/kanade/presentation/achievement/
├── ui/
│   └── AchievementScreen.kt           # Экран достижений
├── components/
│   ├── AchievementCard.kt             # Карточка достижения
│   ├── AchievementTabsAndGrid.kt      # Сетка с табами
│   └── AchievementDetailDialog.kt     # Диалог деталей
└── screenmodel/
    └── AchievementScreenModel.kt      # ViewModel для экрана

app/src/main/assets/achievements/
└── achievements.json                  # Конфигурация достижений
```

---

## Как добавить новое достижение

### Шаг 1: Откройте конфигурационный файл

Файл находится по пути: `app/src/main/assets/achievements/achievements.json`

### Шаг 2: Добавьте новую запись в массив `achievements`

```json
{
  "version": 1,
  "achievements": [
    {
      "id": "your_achievement_id",
      "type": "quantity",
      "category": "manga",
      "threshold": 100,
      "points": 150,
      "title": "Название достижения",
      "description": "Описание достижения",
      "badge_icon": "ic_badge_your_achievement",
      "is_hidden": false,
      "is_secret": false
    }
  ]
}
```

### Шаг 3: Увеличьте версию

После добавления достижений увеличьте поле `version`:

```json
{
  "version": 2,  // Был 1, стал 2
  "achievements": [...]
}
```

Это позволит системе определить, что нужно обновить достижения.

### Шаг 4: Добавьте иконку (опционально)

Если указали `badge_icon`, добавьте векторную иконку в:

```
app/src/main/res/drawable/ic_badge_your_achievement.xml
```

Пример векторной иконки:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFD700"
        android:pathData="M12,2L15.09,8.26L22,9.27L17,14.14L18.18,21.02L12,17.77L5.82,21.02L7,14.14L2,9.27L8.91,8.26L12,2Z"/>
</vector>
```

---

## Поля достижения

| Поле | Обязательное | Тип | Описание |
|------|--------------|-----|----------|
| `id` | Да | String | Уникальный идентификатор (только латинские буквы, цифры, подчеркивания) |
| `type` | Да | String | Тип достижения (см. [Типы достижений](#типы-достижений)) |
| `category` | Да | String | Категория (см. [Категории](#категории)) |
| `threshold` | Условно | Int | Пороговое значение (обязательно для `quantity`, `diversity`, `streak`) |
| `points` | Да | Int | Количество очков за получение |
| `title` | Да | String | Название достижения (на русском) |
| `description` | Нет | String | Описание (на русском) |
| `badge_icon` | Нет | String | Имя файла иконки без расширения |
| `is_hidden` | Нет | Boolean | Скрывать название/описание пока не получено (по умолчанию `false`) |
| `is_secret` | Нет | Boolean | Является ли секретным (по умолчанию `false`) |
| `unlockable_id` | Нет | String | ID разблокируемого контента (тема, значок и т.д.) |

---

## Типы достижений

### 1. `quantity` - Количество

Срабатывает при достижении определенного количества действий.

**Примеры:**
- Прочитать N глав
- Посмотреть N серий
- Добавить N тайтлов в библиотеку

```json
{
  "id": "read_1000_chapters",
  "type": "quantity",
  "category": "manga",
  "threshold": 1000,
  "points": 500,
  "title": "Манга-легенда",
  "description": "Прочитайте 1000 глав",
  "badge_icon": "ic_badge_1000_chapters"
}
```

**Как работает:**
- Автоматически инкрементируется при событиях `ChapterRead` или `EpisodeWatched`
- Прогресс сохраняется в базе данных
- Разблокируется когда `progress >= threshold`

### 2. `event` - Одноразовое событие

Срабатывает при выполнении определенного действия один раз.

**Примеры:**
- Прочитать первую главу
- Добавить первый тайтл в библиотеку
- Закончить первую мангу

```json
{
  "id": "first_chapter",
  "type": "event",
  "category": "manga",
  "threshold": 1,
  "points": 10,
  "title": "Первые шаги",
  "description": "Прочитайте свою первую главу",
  "badge_icon": "ic_badge_first_chapter"
}
```

**Как работает:**
- Прогресс: 0 (не получено) или 1 (получено)
- Срабатывает при первом совпадении события

### 3. `diversity` - Разнообразие

Срабатывает при достижении разнообразия в чем-либо.

**Примеры:**
- Прочитать тайтлы из N разных жанров
- Использовать N разных источников
- Прочитать тайтлы N разных авторов

```json
{
  "id": "genre_explorer",
  "type": "diversity",
  "category": "both",
  "threshold": 10,
  "points": 300,
  "title": "Мастер жанров",
  "description": "Читайте тайтлы из 10 разных жанров",
  "badge_icon": "ic_badge_genre_master"
}
```

**Как работает:**
- Прогресс вычисляется динамически через `DiversityAchievementChecker`
- Поддерживаемые типы: жанры, источники, авторы
- ID должен содержать ключевое слово: `genre`, `source`, `author`

### 4. `streak` - Серия

Срабатывает при использовании приложения N дней подряд.

**Примеры:**
- Использовать приложение 7 дней подряд
- Использовать приложение 30 дней подряд

```json
{
  "id": "week_warrior",
  "type": "streak",
  "category": "both",
  "threshold": 7,
  "points": 200,
  "title": "Воин недели",
  "description": "Используйте приложение 7 дней подряд",
  "badge_icon": "ic_badge_week_warrior"
}
```

**Как работает:**
- Прогресс вычисляется через `StreakAchievementChecker`
- Серия сбрасывается при пропуске дня
- Логируется при чтении главы или просмотре серии

---

## Категории

### `anime` - Только для аниме

Достижения, относящиеся только к аниме.

```json
{
  "id": "watch_100_episodes",
  "type": "quantity",
  "category": "anime",
  "threshold": 100,
  "points": 100,
  "title": "Любитель аниме",
  "description": "Посмотрите 100 серий"
}
```

### `manga` - Только для манги

Достижения, относящиеся только к манге.

```json
{
  "id": "read_100_chapters",
  "type": "quantity",
  "category": "manga",
  "threshold": 100,
  "points": 100,
  "title": "Любитель манги",
  "description": "Прочитайте 100 глав"
}
```

### `both` - Общие достижения

Достижения, которые могут относиться и к аниме, и к манге.

```json
{
  "id": "genre_explorer",
  "type": "diversity",
  "category": "both",
  "threshold": 5,
  "points": 150,
  "title": "Исследователь жанров",
  "description": "Читайте 5 разных жанров"
}
```

### `secret` - Секретные достижения

Скрытые достижения, которые не видны пока не получены.

```json
{
  "id": "secret_achievement",
  "type": "event",
  "category": "secret",
  "threshold": 1,
  "points": 500,
  "title": "Секретное достижение",
  "description": "Это секретное достижение",
  "is_hidden": true,
  "is_secret": true
}
```

---

## Проверка достижений

### Автоматическая проверка

Система автоматически проверяет достижения при следующих событиях:

| Событие | Когда срабатывает | Какие достижения проверяет |
|---------|-------------------|---------------------------|
| `ChapterRead` | Пользователь прочитал главу | `quantity`, `event` (manga) |
| `EpisodeWatched` | Пользователь посмотрел серию | `quantity`, `event` (anime) |
| `LibraryAdded` | Тайтл добавлен в библиотеку | `event` |
| `LibraryRemoved` | Тайтл удален из библиотеки | - |
| `MangaCompleted` | Манга помечена как прочитанная | `event` |
| `AnimeCompleted` | Аниме помечено как просмотренное | `event` |

### Ручная проверка

Для проверки достижений вручную (например, для тестирования):

```kotlin
// В AchievementRepositoryImplTest
@Test
fun testManualAchievementUnlock() {
    // Создаем достижение
    val achievement = Achievement(
        id = "test_achievement",
        type = AchievementType.QUANTITY,
        category = AchievementCategory.MANGA,
        threshold = 10,
        points = 100,
        title = "Тестовое достижение",
    )

    // Создаем прогресс
    val progress = AchievementProgress(
        achievementId = "test_achievement",
        progress = 10,
        maxProgress = 10,
        isUnlocked = true,
        unlockedAt = System.currentTimeMillis(),
        lastUpdated = System.currentTimeMillis(),
    )

    // Сохраняем
    repository.insertOrUpdateProgress(progress)

    // Проверяем
    val saved = repository.getProgress("test_achievement").first()
    assertTrue(saved.isUnlocked)
}
```

---

## События системы

### AchievementEvent

Базовый класс для всех событий системы достижений.

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

### Отправка события

```kotlin
// В месте, где происходит событие (например, при чтении главы)
class ChapterReader(
    private val eventBus: AchievementEventBus
) {
    fun onChapterRead(chapterId: Long, mangaId: Long) {
        // ... логика чтения главы ...

        // Отправляем событие
        eventBus.send(
            AchievementEvent.ChapterRead(
                chapterId = chapterId,
                mangaId = mangaId
            )
        )
    }
}
```

### Обработка события

```kotlin
// AchievementHandler автоматически обрабатывает события
// При получении события он:
// 1. Определяет тип события
// 2. Находит релевантные достижения
// 3. Вычисляет новый прогресс
// 4. Обновляет прогресс в БД
// 5. Если разблокировано - отправляет уведомление
```

---

## Награды

### Разблокируемый контент

Вы можете привязать к достижению разблокируемый контент:

```json
{
  "id": "content_master",
  "type": "quantity",
  "category": "both",
  "threshold": 1000,
  "points": 500,
  "title": "Мастер контента",
  "description": "Прочитайте/посмотрите 1000 единиц контента",
  "badge_icon": "ic_badge_master",
  "unlockable_id": "theme_achievement_gold"
}
```

### Типы разблокируемого контента

| Префикс | Тип | Пример |
|---------|-----|--------|
| `theme_` | Тема оформления | `theme_achievement_gold` |
| `badge_` | Значок профиля | `badge_achievement_master` |
| `display_` | Стиль отображения | `display_grid_large` |

### Менеджер разблокировок

```kotlin
class UnlockableManager {
    fun unlockAchievementRewards(achievement: Achievement) {
        achievement.unlockableId?.let { unlockableId ->
            when {
                unlockableId.startsWith("theme_") -> {
                    // Разблокируем тему
                    unlockTheme(unlockableId)
                }
                unlockableId.startsWith("badge_") -> {
                    // Разблокируем значок
                    unlockBadge(unlockableId)
                }
                // ... другие типы
            }
        }
    }
}
```

---

## Примеры

### Пример 1: Простое достижение-количество

```json
{
  "id": "read_50_chapters",
  "type": "quantity",
  "category": "manga",
  "threshold": 50,
  "points": 50,
  "title": "Читатель",
  "description": "Прочитайте 50 глав",
  "badge_icon": "ic_badge_50_chapters"
}
```

### Пример 2: Достижение-разнообразие

```json
{
  "id": "source_explorer",
  "type": "diversity",
  "category": "both",
  "threshold": 10,
  "points": 200,
  "title": "Исследователь источников",
  "description": "Используйте 10 разных источников",
  "badge_icon": "ic_badge_source_explorer"
}
```

### Пример 3: Секретное достижение

```json
{
  "id": "easter_egg",
  "type": "event",
  "category": "secret",
  "threshold": 1,
  "points": 1000,
  "title": "Пасхалка",
  "description": "Найдите секрет",
  "badge_icon": "ic_badge_easter_egg",
  "is_hidden": true,
  "is_secret": true
}
```

### Пример 4: Достижение с наградой

```json
{
  "id": "master_achiever",
  "type": "quantity",
  "category": "both",
  "threshold": 50,
  "points": 500,
  "title": "Мастер достижений",
  "description": "Разблокируйте 50 достижений",
  "badge_icon": "ic_badge_master_achiever",
  "unlockable_id": "badge_achievement_master"
}
```

---

## Тестирование

### Unit-тесты

Тесты для достижений находятся в `data/src/test/java/tachiyomi/data/achievement/`:

```kotlin
class AchievementRepositoryImplTest {
    @Test
    fun `getProgress returns progress for achievement`() {
        // ...
    }
}
```

### UI-тесты

UI-тесты находятся в `app/src/androidTest/java/eu/kanade/tachiyomi/ui/achievement/`:

```kotlin
class AchievementScreenTest {
    @Test
    fun achievementScreen_displaysAchievements() {
        // ...
    }
}
```

---

## Устранение проблем

### Достижение не разблокируется

1. Проверьте, что `threshold` указан правильно
2. Проверьте, что события отправляются корректно
3. Проверьте логи через `logcat`
4. Убедитесь, что AchievementHandler запущен

```kotlin
// Проверка логов
logcat(LogPriority.DEBUG) { "Achievement progress: $progress" }
```

### Прогресс не сохраняется

1. Проверьте, что база данных инициализирована
2. Проверьте, что `repository.insertOrUpdateProgress()` вызывается
3. Проверьте миграции БД

### Иконка не отображается

1. Проверьте, что файл иконки существует в `app/src/main/res/drawable/`
2. Проверьте, что имя файла совпадает с `badge_icon` (без расширения)
3. Очистите кэш приложения

---

## Дополнительные ресурсы

- [Android Compose Testing](https://developer.android.com/jetpack/compose/testing)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [SQLite Delight](https://cashapp.github.io/sqldelight/)

---

## Изменения

| Версия | Дата | Описание |
|--------|------|----------|
| 1.0 | 2025-01-26 | Первая версия документации |
