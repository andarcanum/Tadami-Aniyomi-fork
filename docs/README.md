# Система достижений (Achievement System)

## Обзор

Система достижений (Achievement System) - это модульная система для отслеживания, проверки и награждения пользователей за их активность в приложении Aniyomi.

## Быстрый старт

### Для разработчиков

1. **Добавить новое достижение**

   Откройте `app/src/main/assets/achievements/achievements.json` и добавьте новую запись:

   ```json
   {
     "id": "your_achievement_id",
     "type": "quantity",
     "category": "manga",
     "threshold": 100,
     "points": 150,
     "title": "Ваше достижение",
     "description": "Описание достижения"
   }
   ```

   Подробности в [ACHIEVEMENTS_GUIDE.md](./ACHIEVEMENTS_GUIDE.md)

2. **Отправить событие**

   ```kotlin
   // В месте действия пользователя
   eventBus.emit(AchievementEvent.ChapterRead(chapterId, mangaId))
   ```

3. **Проверить разблокировку**

   Система автоматически проверит достижения и добавит очки.

## Документация

| Документ | Описание |
|----------|----------|
| [ACHIEVEMENTS_GUIDE.md](./ACHIEVEMENTS_GUIDE.md) | Руководство по добавлению достижений |
| [ACHIEVEMENTS_ARCHITECTURE.md](./ACHIEVEMENTS_ARCHITECTURE.md) | Архитектура и компоненты системы |
| [ACHIEVEMENTS_TESTING.md](./ACHIEVEMENTS_TESTING.md) | Тестирование системы |

## Структура

```
data/src/main/java/tachiyomi/data/achievement/
├── handler/
│   ├── AchievementHandler.kt          # Главный обработчик
│   ├── AchievementEventBus.kt         # Шина событий
│   ├── AchievementCalculator.kt       # Калькулятор прогресса
│   ├── PointsManager.kt               # Менеджер очков
│   └── checkers/
│       ├── DiversityAchievementChecker.kt  # Проверка разнообразия
│       └── StreakAchievementChecker.kt     # Проверка серий
├── database/
│   └── AchievementsDatabase.kt        # SQLite БД
└── loader/
    └── AchievementLoader.kt           # Загрузчик из JSON

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
    └── AchievementScreenModel.kt      # ViewModel

app/src/main/assets/achievements/
└── achievements.json                  # Конфигурация достижений
```

## Типы достижений

| Тип | Описание | Пример |
|-----|----------|--------|
| `quantity` | Количество действий | Прочитать 100 глав |
| `event` | Одноразовое событие | Прочитать первую главу |
| `diversity` | Разнообразие | Использовать 5 жанров |
| `streak` | Дни подряд | Использовать 7 дней подряд |

## Категории

| Категория | Описание |
|-----------|----------|
| `anime` | Только для аниме |
| `manga` | Только для манги |
| `both` | Общие (аниме + манга) |
| `secret` | Секретные |

## Поток данных

```
User Action
    ↓
AchievementEvent
    ↓
EventBus
    ↓
AchievementHandler
    ↓
Checker (Diversity/Streak)
    ↓
Calculate Progress
    ↓
Update Database
    ↓
PointsManager
    ↓
UnlockableManager
    ↓
UI Update
```

## Примеры использования

### Создание достижения

```kotlin
// В achievements.json
{
  "id": "read_1000_chapters",
  "type": "quantity",
  "category": "manga",
  "threshold": 1000,
  "points": 500,
  "title": "Манга-легенда",
  "description": "Прочитайте 1000 глав"
}
```

### Отправка события

```kotlin
class ChapterReader(
    private val eventBus: AchievementEventBus
) {
    fun onChapterRead(chapterId: Long, mangaId: Long) {
        // ... логика чтения ...

        // Отправляем событие
        eventBus.emit(
            AchievementEvent.ChapterRead(
                chapterId = chapterId,
                mangaId = mangaId
            )
        )
    }
}
```

### Подписка на разблокировку

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Устанавливаем callback для уведомлений
        achievementHandler.unlockCallback = object : AchievementHandler.AchievementUnlockCallback {
            override fun onAchievementUnlocked(achievement: Achievement) {
                // Показать баннер разблокировки
                showUnlockBanner(achievement)
            }
        }
    }
}
```

## Тестирование

### Unit тесты

```bash
# Все тесты
./gradlew test

# Конкретный тест
./gradlew :data:test --tests "tachiyomi.data.achievement.PointsManagerTest"
```

### UI тесты

```bash
# Все UI тесты
./gradlew connectedAndroidTest

# Только тесты достижений
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=eu.kanade.tachiyomi.ui.achievement.AchievementScreenTest
```

Подробности в [ACHIEVEMENTS_TESTING.md](./ACHIEVEMENTS_TESTING.md)

## Производительность

- **LazyVerticalGrid**: Виртуализация длинных списков
- **Flow**: Реактивные обновления
- **Кэширование**: 5 минут для diversity-достижений
- **Batch операции**: Пачки по 50 для обновлений

## Безопасность

- Валидация данных при загрузке из JSON
- Обработка ошибок в coroutines
- SupervisorJob для изоляции ошибок
- Логирование всех операций

## Расширение

### Добавление нового типа достижения

1. Добавьте тип в `AchievementType`
2. Создайте checker
3. Добавьте логику в `AchievementHandler`

### Добавление нового события

1. Добавьте событие в `AchievementEvent`
2. Обработайте в `AchievementHandler`

## Устранение проблем

### Достижение не разблокируется

1. Проверьте `threshold` в JSON
2. Проверьте отправку событий
3. Проверьте логи: `adb logcat | grep Achievement`

### Прогресс не сохраняется

1. Проверьте инициализацию БД
2. Проверьте вызовы `repository.insertOrUpdateProgress()`
3. Проверьте миграции БД

### Иконка не отображается

1. Проверьте наличие файла в `app/src/main/res/drawable/`
2. Проверьте имя в `badge_icon`
3. Очистите кэш приложения

## Ссылки

- [Руководство по добавлению](./ACHIEVEMENTS_GUIDE.md)
- [Архитектура системы](./ACHIEVEMENTS_ARCHITECTURE.md)
- [Тестирование](./ACHIEVEMENTS_TESTING.md)
- [JSON конфигурация](../app/src/main/assets/achievements/achievements.json)

## Лицензия

Этот проект является частью Aniyomi и распространяется под той же лицензией.

## Контакты

Для вопросов и предложений по системе достижений создайте issue в репозитории.
