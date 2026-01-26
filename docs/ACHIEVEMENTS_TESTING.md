# Тестирование системы достижений

## Обзор

В этом документе описаны различные типы тестов для системы достижений и инструкции по их запуску.

## Структура тестов

```
app/src/androidTest/java/eu/kanade/tachiyomi/ui/achievement/
└── AchievementScreenTest.kt    # UI тесты экрана достижений

data/src/test/java/tachiyomi/data/achievement/
├── AchievementTestBase.kt          # Базовый класс для тестов
├── AchievementRepositoryImplTest.kt # Тесты репозитория
├── PointsManagerTest.kt            # Тесты менеджера очков
├── DiversityAchievementCheckerTest.kt # Тесты проверщика разнообразия
├── StreakAchievementCheckerTest.kt   # Тесты проверщика серий
└── AchievementCalculatorTest.kt      # Тесты калькулятора
```

## UI тесты

### AchievementScreenTest

Тестирует UI компоненты экрана достижений с использованием Compose Testing.

#### Тестируемые сценарии:

1. **Отображение экрана достижений**
   - Проверка заголовка
   - Отображение карточек достижений
   - Отображение статусов (получено/в процессе)

2. **Фильтрация по категориям**
   - Переключение между MANGA, ANIME, BOTH, SECRET
   - Корректное отображение достижений каждой категории

3. **Отображение секретных достижений**
   - Скрытие названия и описания для заблокированных секретных достижений
   - Отображение "???" вместо названия
   - Отображение иконки замка

4. **Диалог деталей**
   - Открытие диалога при клике на карточку
   - Отображение детальной информации
   - Закрытие диалога

5. **Производительность**
   - Отображение большого количества элементов (100+)
   - Время рендеринга < 1 секунды
   - Отсутствие лагов при скролле

6. **Сохранение состояния**
   - Сохранение выбранной категории при rotate
   - Сохранение выбранного достижения
   - Сохранение прогресса

#### Запуск UI тестов:

```bash
# Все UI тесты
./gradlew connectedAndroidTest

# Только тесты достижений
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=eu.kanade.tachiyomi.ui.achievement.AchievementScreenTest
```

## Unit тесты

### AchievementRepositoryImplTest

Тестирует операции репозитория достижений.

#### Тестируемые сценарии:

1. **CRUD операции**
   - Вставка достижений
   - Получение всех достижений
   - Получение достижений по категории
   - Обновление прогресса

2. **Прогресс**
   - Вставка прогресса
   - Обновление прогресса
   - Получение прогресса по ID
   - Удаление прогресса

3. **Очки пользователя**
   - Получение очков
   - Обновление очков
   - Удаление очков

#### Пример теста:

```kotlin
@Test
fun `insertAndGetProgress returns progress`() = runTest {
    // Arrange
    val achievement = createMockAchievement()
    val progress = AchievementProgress(
        achievementId = achievement.id,
        progress = 5,
        maxProgress = 10,
        isUnlocked = false,
        lastUpdated = System.currentTimeMillis(),
    )

    // Act
    repository.insertOrUpdateProgress(progress)
    val result = repository.getProgress(achievement.id).first()

    // Assert
    assertEquals(5, result.progress)
    assertEquals(10, result.maxProgress)
    assertFalse(result.isUnlocked)
}
```

### PointsManagerTest

Тестирует менеджер очков пользователя.

#### Тестируемые сценарии:

1. **Добавление очков**
   - Добавление очков за достижение
   - Корректное подсчитывание общей суммы

2. **Уровни**
   - Вычисление уровня на основе очков
   - Правильное определение следующего уровня

3. **Счетчик разблокированных**
   - Инкремент при разблокировке достижения
   - Корректное подсчитывание общего количества

### DiversityAchievementCheckerTest

Тестирует проверщик разнообразия.

#### Тестируемые сценарии:

1. **Жанры**
   - Подсчет уникальных жанров
   - Разделение через запятую
   - Кэширование результатов

2. **Источники**
   - Подсчет уникальных источников
   - Кэширование результатов

3. **Категории**
   - Только манга
   - Только аниме
   - Манга + аниме

### StreakAchievementCheckerTest

Тестирует проверщик серий активности.

#### Тестируемые сценарии:

1. **Подсчет серии**
   - Корректный подсчет последовательных дней
   - Обработка пропущенных дней
   - Обработка случая "сегодня еще нет активности"

2. **Логирование активности**
   - Логирование прочитанных глав
   - Логирование просмотренных серий
   - Upsert в базу данных

3. **Граничные случаи**
   - Начало серии (1 день)
   - Длинная серия (365+ дней)
   - Разные часовые пояса (UTC)

### AchievementCalculatorTest

Тестирует калькулятор прогресса.

#### Тестируемые сценарии:

1. **Инициализация**
   - Расчет начального прогресса для всех достижений
   - Корректное вычисление для разных типов

2. **Quantity достижения**
   - Подсчет прочитанных глав
   - Подсчет просмотренных серий
   - Общее количество (manga + anime)

3. **Event достижения**
   - Проверка первых действий
   - Корректное определение статуса

4. **Diversity достижения**
   - Использование DiversityAchievementChecker
   - Корректное вычисление разнообразия

5. **Streak достижения**
   - Использование StreakAchievementChecker
   - Корректное вычисление серии

## Запуск тестов

### Все unit тесты:

```bash
# Все тесты data модуля
./gradlew :data:test

# Все тесты домена
./gradlew :domain:test

# Все тесты приложения
./gradlew :app:test

# Все тесты проекта
./gradlew test
```

### Конкретные тесты:

```bash
# Только тесты репозитория
./gradlew :data:test --tests "tachiyomi.data.achievement.AchievementRepositoryImplTest"

# Только тесты менеджера очков
./gradlew :data:test --tests "tachiyomi.data.achievement.PointsManagerTest"

# Только тесты проверщика разнообразия
./gradlew :data:test --tests "tachiyomi.data.achievement.DiversityAchievementCheckerTest"

# Только тесты проверщика серий
./gradlew :data:test --tests "tachiyomi.data.achievement.StreakAchievementCheckerTest"
```

### С покрытием кода:

```bash
# Запуск с генерацией отчета о покрытии
./gradlew test jacocoTestReport

# Просмотр отчета
open data/build/reports/jacoco/test/html/index.html
```

## Интеграционные тесты

### Тест потока разблокировки достижения

Проверяет полный цикл от события до разблокировки:

```kotlin
@Test
fun testAchievementUnlockFlow() = runTest {
    // 1. Создаем достижение
    val achievement = Achievement(
        id = "test_achievement",
        type = AchievementType.QUANTITY,
        category = AchievementCategory.MANGA,
        threshold = 10,
        points = 100,
        title = "Тестовое достижение",
    )

    // 2. Регистрируем достижение
    repository.insertOrUpdate(achievement)

    // 3. Отправляем события
    repeat(10) {
        eventBus.emit(AchievementEvent.ChapterRead(it.toLong(), 1))
    }

    // 4. Ждем обработки
    delay(1000)

    // 5. Проверяем прогресс
    val progress = repository.getProgress(achievement.id).first()
    assertTrue(progress.isUnlocked)
    assertEquals(10, progress.progress)

    // 6. Проверяем очки
    val points = pointsManager.getPoints()
    assertEquals(100, points.totalPoints)
    assertEquals(1, points.unlockedCount)
}
```

## Тестирование производительности

### Бенчмарк рендеринга

```kotlin
@Test
fun testLargeListPerformance() {
    // Создаем 1000 достижений
    val largeList = (0..1000).map { createMockAchievement(it) }

    val startTime = System.currentTimeMillis()

    composeTestRule.setContent {
        AchievementGrid(achievements = largeList)
    }

    val renderTime = System.currentTimeMillis() - startTime

    // Рендер должен занять менее 1 секунды
    assertTrue(renderTime < 1000) { "Render took ${renderTime}ms" }
}
```

### Бенчмарк вычислений

```kotlin
@Test
fun testCalculationPerformance() = runTest {
    val startTime = System.currentTimeMillis()

    calculator.calculateInitialProgress()

    val duration = System.currentTimeMillis() - startTime

    // Вычисления должны занять менее 5 секунд
    assertTrue(duration < 5000) { "Calculation took ${duration}ms" }
}
```

## Отладка тестов

### Логирование

Используйте logcat для отладки:

```bash
# Фильтрация логов по тегу
adb logcat | grep "Achievement"

# Или по ключевым словам
adb logcat | grep -E "test|debug|achievement"
```

### Mock данные

Для создания mock данных используйте вспомогательные функции:

```kotlin
fun createMockAchievement(
    id: String = "test_$randomId()",
    type: AchievementType = AchievementType.QUANTITY,
    category: AchievementCategory = AchievementCategory.MANGA,
    threshold: Int = 10,
    points: Int = 100,
) = Achievement(
    id = id,
    type = type,
    category = category,
    threshold = threshold,
    points = points,
    title = "Test Achievement",
    description = "Test Description",
)

fun createMockProgress(
    achievementId: String,
    progress: Int = 5,
    isUnlocked: Boolean = false,
) = AchievementProgress(
    achievementId = achievementId,
    progress = progress,
    maxProgress = 10,
    isUnlocked = isUnlocked,
    lastUpdated = System.currentTimeMillis(),
)
```

## CI/CD

### GitHub Actions

Пример конфигурации для CI:

```yaml
name: Achievement Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run unit tests
        run: ./gradlew test
      - name: Run UI tests
        run: ./gradlew connectedAndroidTest
      - name: Generate coverage report
        run: ./gradlew jacocoTestReport
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

## Рекомендации

1. **Изоляция тестов**
   - Каждый тест должен быть независимым
   - Используйте `@Before` для setup
   - Используйте `@After` для cleanup

2. **Скорость тестов**
   - Unit тесты должны быть быстрыми (< 100ms)
   - UI тесты могут быть медленнее (< 5s)
   - Интеграционные тесты самые медленные (< 30s)

3. **Покрытие кода**
   - Стремитесь к 80%+ покрытию
   - Особое внимание критическим путям
   - Тестируйте граничные случаи

4. **Читаемость**
   - Используйте понятные имена тестов
   - Следуйте паттерну Arrange-Act-Assert
   - Добавляйте комментарии для сложных тестов

## Решение проблем

### Тесты падают с ошибкой БД

```kotlin
// Используйте in-memory БД для тестов
@Before
fun setup() {
    database = createInMemoryDatabase()
    repository = AchievementRepositoryImpl(database)
}

@After
fun tearDown() {
    database.close()
}
```

### UI тесты падают с timeout

```kotlin
// Увеличьте timeout
composeTestRule.waitUntil(timeoutMillis = 10000) {
    // условие
}
```

### Тесты не видят ресурсы

```kotlin
// Используйте Context для доступа к ресурсам
val context = InstrumentationRegistry.getInstrumentation().targetContext
val loader = AchievementLoader(context, repository)
```

## Дополнительные ресурсы

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Compose Testing Documentation](https://developer.android.com/jetpack/compose/testing)
- [Kotlin Coroutines Test](https://kotlinlang.org/docs/coroutines-test-guide.html)
- [MockK Documentation](https://mockk.io/)
