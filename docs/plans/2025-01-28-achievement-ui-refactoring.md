# Рефакторинг UI-компонентов экрана достижений

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Исправить верстку блока сравнения статистики (унификация размеров карточек, тема Aurora), переработать модуль "Активность за год" (сетка, метки месяцев, привязка данных), обеспечить UI и Unit-тестирование.

**Architecture:** Компоненты используют Jetpack Compose с кастомной Aurora theme. Требуется стандартизация цветов через `AuroraTheme.colors`, исправление layout-проблем с `heightIn(min = 100.dp)`, и реализация корректной логики отображения активности.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, JUnit5, Compose UI Test

---

## Анализ текущих проблем

### Проблема 1: Неравномерные размеры карточек в блоке сравнения
- **Файл:** `AchievementStatsComparison.kt:223` - `heightIn(min = 100.dp)` не гарантирует одинаковую высоту
- **Причина:** Контент разной длины (время "38ч 20м" vs "8") + перенос строки у "Глав прочитано"

### Проблема 2: Серые цвета вместо Aurora theme
- **Файл:** `AchievementStatsComparison.kt:218` - хардкод `Color(0xFF4ADE80)` и `Color(0xFFF87171)`
- **Файл:** `AchievementStatsComparison.kt:179-180` - хардкод золотого `Color(0xFFFFB800)`
- **Причина:** Не используются семантические цвета из `AuroraColors`

### Проблема 3: Нет данных для графика активности
- **Файл:** `AchievementScreenModel.kt` - `activityData`, `currentMonthStats` не заполняются из репозитория
- **Причина:** Отсутствует интеграция с источниками данных

### Проблема 4: Проблемы с отображением сетки активности
- **Файл:** `AchievementActivityGraph.kt:188-210` - месячные метки позиционируются некорректно
- **Причина:** Расчет `weekWidth` не учитывает реальный размер ячеек

---

## Task 1: Добавить семантические цвета в AuroraColors

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/theme/AuroraTheme.kt`

**Step 1: Добавить семантические цвета в data class**

```kotlin
@Immutable
data class AuroraColors(
    // ... existing colors ...
    // Semantic colors for consistent UI
    val success: Color,
    val warning: Color,
    val error: Color,
    val achievementGold: Color,
) {
```

**Step 2: Обновить companion object для Dark темы**

В `Dark` instance добавить:
```kotlin
success = Color(0xFF4ADE80),
warning = Color(0xFFFBBF24),
error = Color(0xFFF87171),
achievementGold = Color(0xFFFFB800),
```

**Step 3: Обновить companion object для Light темы**

В `Light` instance добавить:
```kotlin
success = Color(0xFF22C55E),
warning = Color(0xFFF59E0B),
error = Color(0xFFEF4444),
achievementGold = Color(0xFFFFB800),
```

**Step 4: Обновить fromColorScheme функцию**

```kotlin
return AuroraColors(
    // ... existing ...
    success = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E),
    warning = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B),
    error = if (isDark) Color(0xFFF87171) else Color(0xFFEF4444),
    achievementGold = Color(0xFFFFB800),
)
```

**Step 5: UI-тест - проверка цветов**

Создать Preview с семантическими цветами:
```kotlin
@Preview(showBackground = true)
@Composable
private fun AuroraColorsSemanticPreview() {
    val colors = AuroraColors.Dark
    CompositionLocalProvider(LocalAuroraColors provides colors) {
        Column(
            modifier = Modifier
                .background(colors.background)
                .padding(16.dp)
        ) {
            Text("Success", color = colors.success)
            Text("Warning", color = colors.warning)
            Text("Error", color = colors.error)
            Text("Achievement Gold", color = colors.achievementGold)
        }
    }
}
```

**Step 6: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/theme/AuroraTheme.kt
git commit -m "feat(achievements): add semantic colors to AuroraColors

- Add success, warning, error, achievementGold colors
- Support both Dark and Light themes
- Update fromColorScheme to generate semantic colors"
```

---

## Task 2: Исправить цвета в AchievementStatsComparison

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/achievement/components/AchievementStatsComparison.kt`

**Step 1: Заменить хардкод цветов на тематические**

В `StatItem` function (line 218):
```kotlin
// Было:
val changeColor = if (isIncrease) Color(0xFF4ADE80) else Color(0xFFF87171)

// Стало:
val changeColor = if (isIncrease) colors.success else colors.error
```

**Step 2: Обновить иконку достижений**

В вызове `StatItem` для achievements (lines 177-185):
```kotlin
// Было:
iconBackground = Color(0xFFFFB800).copy(alpha = 0.15f),
iconTint = Color(0xFFFFB800),

// Стало:
iconBackground = colors.achievementGold.copy(alpha = 0.15f),
iconTint = colors.achievementGold,
```

**Step 3: UI-тест - Preview с разными темами**

Запустить Preview `AchievementStatsComparisonPreview` и `AchievementStatsComparisonLightPreview` для проверки цветов.

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/achievement/components/AchievementStatsComparison.kt
git commit -m "refactor(achievements): use semantic colors in stats comparison

- Replace hardcoded success/error colors with theme colors
- Use achievementGold from AuroraColors"
```

---

## Task 3: Унифицировать размеры карточек статистики

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/achievement/components/AchievementStatsComparison.kt:196-233`

**Step 1: Использовать фиксированную высоту вместо min height**

```kotlin
// Было:
Column(
    modifier = modifier
        .heightIn(min = 100.dp)
        // ...

// Стало:
Column(
    modifier = modifier
        .height(120.dp)
        // ...
```

**Step 2: Добавить weight для равномерного распределения контента**

```kotlin
Column(
    modifier = modifier
        .height(120.dp)
        .background(/* ... */)
        .border(/* ... */)
        .padding(12.dp), // уменьшить padding с 16 до 12
) {
    // Icon
    Box(/* ... */)

    Spacer(modifier = Modifier.height(8.dp)) // уменьшить

    // Value
    Text(/* ... */)

    Spacer(modifier = Modifier.height(2.dp))

    // Label - ограничить до 1 строки
    Text(
        text = label,
        color = colors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1, // добавить
        softWrap = false, // добавить
    )

    Spacer(modifier = Modifier.weight(1f)) // заполнить пространство

    // Change indicator
    // ...
}
```

**Step 3: UI-тест - проверка равномерности**

Создать Preview с максимально разным контентом:
```kotlin
@Preview(showBackground = true, widthDp = 400)
@Composable
private fun AchievementStatsComparisonUniformPreview() {
    val colors = AuroraColors.Dark
    CompositionLocalProvider(LocalAuroraColors provides colors) {
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(16.dp),
        ) {
            AchievementStatsComparison(
                currentMonth = MonthStats(
                    chaptersRead = 9999, // длинное число
                    episodesWatched = 1, // короткое
                    timeInAppMinutes = 9999, // длинное время
                    achievementsUnlocked = 999, // среднее
                ),
                previousMonth = MonthStats(
                    chaptersRead = 5000,
                    episodesWatched = 1,
                    timeInAppMinutes = 5000,
                    achievementsUnlocked = 500,
                ),
            )
        }
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/achievement/components/AchievementStatsComparison.kt
git commit -m "fix(achievements): unify stat card sizes

- Use fixed height (120.dp) instead of min height
- Add maxLines=1 for labels to prevent overflow
- Use weight() to distribute content evenly"
```

---

## Task 4: Исправить позиционирование меток месяцев в графике активности

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/achievement/components/AchievementActivityGraph.kt:179-212`

**Step 1: Переработать MonthLabelsRow с правильным расчетом позиций**

```kotlin
@Composable
private fun MonthLabelsRow(
    monthLabels: List<Pair<Int, String>>,
    colors: AuroraColors,
) {
    val density = LocalDensity.current
    val cellSize = 12.dp
    val cellSpacing = 2.dp
    val columnSpacing = 2.dp // spacing between week columns

    // Calculate actual week column width (7 cells + 6 spacings between cells)
    val weekColumnWidth = with(density) {
        (cellSize.toPx() * 7) + (cellSpacing.toPx() * 6)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        monthLabels.forEach { (weekIndex, monthName) ->
            // Calculate position based on week index
            val position = with(density) {
                (weekIndex * (weekColumnWidth + columnSpacing.toPx())).toDp()
            }

            Text(
                text = monthName,
                color = colors.textSecondary.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = position + 4.dp), // небольшой отступ
            )
        }
    }
}
```

**Step 2: Добавить горизонтальную сетку для наглядности**

Добавить в `WeekColumn` разделители:
```kotlin
@Composable
private fun WeekColumn(
    weekDays: List<DayActivity?>,
    weekIndex: Int,
    colors: AuroraColors,
    animationProgress: Float,
    totalWeeks: Int,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.drawBehind {
            // Draw subtle grid line for every 4th week (month boundary)
            if (weekIndex % 4 == 0 && weekIndex > 0) {
                drawLine(
                    color = colors.divider.copy(alpha = 0.2f),
                    start = Offset(-1.dp.toPx(), 0f),
                    end = Offset(-1.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    ) {
        // ... existing day cells ...
    }
}
```

**Step 3: Улучшить видимость ячеек сетки**

В `DayCell` (line 271-279) улучшить бордер:
```kotlin
.border(
    width = 0.5.dp,
    color = when (dayActivity.level) {
        0 -> colors.divider.copy(alpha = 0.4f) // более видимый бордер для пустых
        else -> Color.Transparent
    },
    shape = RoundedCornerShape(2.dp),
)
```

**Step 4: UI-тест - проверка меток месяцев**

Запустить Preview и проверить, что метки месяцев:
- Правильно позиционируются над соответствующими неделями
- Не перекрываются
- Видны в обеих темах

**Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/achievement/components/AchievementActivityGraph.kt
git commit -m "fix(achievements): fix month label positioning in activity graph

- Correct week column width calculation
- Add subtle grid lines for month boundaries
- Improve empty cell border visibility"
```

---

## Task 5: Создать ActivityDataRepository для получения данных активности

**Files:**
- Create: `domain/src/main/java/tachiyomi/domain/achievement/repository/ActivityDataRepository.kt`
- Create: `data/src/main/java/tachiyomi/data/achievement/ActivityDataRepositoryImpl.kt`

**Step 1: Создать доменный репозиторий**

```kotlin
package tachiyomi.domain.achievement.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import java.time.LocalDate

interface ActivityDataRepository {
    /**
     * Get activity data for the last N days
     */
    fun getActivityData(days: Int = 365): Flow<List<DayActivity>>

    /**
     * Get statistics for specific month
     */
    suspend fun getMonthStats(year: Int, month: Int): MonthStats

    /**
     * Get current month stats
     */
    suspend fun getCurrentMonthStats(): MonthStats

    /**
     * Get previous month stats
     */
    suspend fun getPreviousMonthStats(): MonthStats

    /**
     * Record a reading activity
     */
    suspend fun recordReading(chaptersCount: Int)

    /**
     * Record a watching activity
     */
    suspend fun recordWatching(episodesCount: Int)

    /**
     * Record app open
     */
    suspend fun recordAppOpen()
}
```

**Step 2: Создать модели для доменного слоя**

```kotlin
package tachiyomi.domain.achievement.model

import java.time.LocalDate

/**
 * Represents a single day's activity
 */
data class DayActivity(
    val date: LocalDate,
    val level: Int, // 0-4
    val type: ActivityType,
)

enum class ActivityType {
    READING,
    WATCHING,
    APP_OPEN,
}

/**
 * Statistics for a specific month
 */
data class MonthStats(
    val chaptersRead: Int,
    val episodesWatched: Int,
    val timeInAppMinutes: Int,
    val achievementsUnlocked: Int,
)
```

**Step 3: Создать реализацию репозитория**

```kotlin
package tachiyomi.data.achievement

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tachiyomi.domain.achievement.model.ActivityType
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import java.time.LocalDate
import java.time.YearMonth

class ActivityDataRepositoryImpl : ActivityDataRepository {

    // In-memory storage for MVP - replace with database later
    private val activityRecords = mutableListOf<ActivityRecord>()

    override fun getActivityData(days: Int): Flow<List<DayActivity>> = flow {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())

        val activities = activityRecords
            .filter { it.date in startDate..endDate }
            .groupBy { it.date }
            .map { (date, records) ->
                val maxLevel = calculateActivityLevel(records)
                val dominantType = records.groupBy { it.type }
                    .maxByOrNull { it.value.size }
                    ?.key ?: ActivityType.APP_OPEN

                DayActivity(
                    date = date,
                    level = maxLevel,
                    type = dominantType,
                )
            }

        emit(activities)
    }

    override suspend fun getMonthStats(year: Int, month: Int): MonthStats {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        val monthRecords = activityRecords.filter {
            it.date in startDate..endDate
        }

        return MonthStats(
            chaptersRead = monthRecords
                .filter { it.type == ActivityType.READING }
                .sumOf { it.count },
            episodesWatched = monthRecords
                .filter { it.type == ActivityType.WATCHING }
                .sumOf { it.count },
            timeInAppMinutes = calculateTimeInApp(monthRecords),
            achievementsUnlocked = 0, // TODO: integrate with achievement repository
        )
    }

    override suspend fun getCurrentMonthStats(): MonthStats {
        val now = LocalDate.now()
        return getMonthStats(now.year, now.monthValue)
    }

    override suspend fun getPreviousMonthStats(): MonthStats {
        val previousMonth = LocalDate.now().minusMonths(1)
        return getMonthStats(previousMonth.year, previousMonth.monthValue)
    }

    override suspend fun recordReading(chaptersCount: Int) {
        activityRecords.add(ActivityRecord(
            date = LocalDate.now(),
            type = ActivityType.READING,
            count = chaptersCount,
        ))
    }

    override suspend fun recordWatching(episodesCount: Int) {
        activityRecords.add(ActivityRecord(
            date = LocalDate.now(),
            type = ActivityType.WATCHING,
            count = episodesCount,
        ))
    }

    override suspend fun recordAppOpen() {
        activityRecords.add(ActivityRecord(
            date = LocalDate.now(),
            type = ActivityType.APP_OPEN,
            count = 1,
        ))
    }

    private fun calculateActivityLevel(records: List<ActivityRecord>): Int {
        val totalCount = records.sumOf { it.count }
        return when {
            totalCount >= 20 -> 4
            totalCount >= 10 -> 3
            totalCount >= 5 -> 2
            totalCount >= 1 -> 1
            else -> 0
        }
    }

    private fun calculateTimeInApp(records: List<ActivityRecord>): Int {
        // Estimate: each reading session ~15 min, watching ~20 min, app open ~5 min
        return records.sumOf { record ->
            when (record.type) {
                ActivityType.READING -> record.count * 15
                ActivityType.WATCHING -> record.count * 20
                ActivityType.APP_OPEN -> record.count * 5
            }
        }
    }

    private data class ActivityRecord(
        val date: LocalDate,
        val type: ActivityType,
        val count: Int,
    )
}
```

**Step 4: Unit-тест для репозитория**

Создать: `data/src/test/java/tachiyomi/data/achievement/ActivityDataRepositoryTest.kt`

```kotlin
package tachiyomi.data.achievement

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.ActivityType
import java.time.LocalDate
import kotlin.test.assertEquals

class ActivityDataRepositoryTest {

    private lateinit var repository: ActivityDataRepositoryImpl

    @BeforeEach
    fun setup() {
        repository = ActivityDataRepositoryImpl()
    }

    @Test
    fun `recordReading should add reading activity`() = runBlocking {
        // When
        repository.recordReading(5)

        // Then
        val activities = repository.getActivityData(1).first()
        assertEquals(1, activities.size)
        assertEquals(ActivityType.READING, activities[0].type)
        assertEquals(2, activities[0].level) // 5 chapters = level 2
    }

    @Test
    fun `getMonthStats should calculate correct totals`() = runBlocking {
        // Given
        repository.recordReading(10)
        repository.recordWatching(5)
        repository.recordAppOpen()

        // When
        val stats = repository.getCurrentMonthStats()

        // Then
        assertEquals(10, stats.chaptersRead)
        assertEquals(5, stats.episodesWatched)
        assertEquals(265, stats.timeInAppMinutes) // 10*15 + 5*20 + 1*5
    }

    @Test
    fun `activity level should be calculated correctly`() = runBlocking {
        // Given - level 4 (20+ items)
        repeat(5) { repository.recordReading(5) } // 25 total

        // When
        val activities = repository.getActivityData(1).first()

        // Then
        assertEquals(4, activities[0].level)
    }
}
```

**Step 5: Запустить тесты**

```bash
./gradlew :data:test --tests "tachiyomi.data.achievement.ActivityDataRepositoryTest"
```

Expected: All tests pass

**Step 6: Commit**

```bash
git add domain/src/main/java/tachiyomi/domain/achievement/repository/ActivityDataRepository.kt
git add domain/src/main/java/tachiyomi/domain/achievement/model/DayActivity.kt
git add data/src/main/java/tachiyomi/data/achievement/ActivityDataRepositoryImpl.kt
git add data/src/test/java/tachiyomi/data/achievement/ActivityDataRepositoryTest.kt
git commit -m "feat(achievements): add ActivityDataRepository for activity tracking

- Create domain repository interface
- Implement in-memory repository
- Add unit tests for activity recording and stats calculation"
```

---

## Task 6: Интегрировать ActivityDataRepository в AchievementScreenModel

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/achievement/screenmodel/AchievementScreenModel.kt`

**Step 1: Добавить зависимость в конструктор**

```kotlin
class AchievementScreenModel(
    private val achievementRepository: AchievementRepository,
    private val achievementLoader: AchievementLoader,
    private val achievementPointsManager: AchievementPointsManager,
    private val activityDataRepository: ActivityDataRepository, // добавить
) : StateScreenModel<AchievementScreenState>(AchievementScreenState.Loading) {
```

**Step 2: Обновить init блок для загрузки данных активности**

```kotlin
init {
    screenModelScope.launch {
        // Load achievements
        achievementLoader.loadAchievements()
    }

    screenModelScope.launch {
        // Combine all data streams
        combine(
            achievementRepository.getAllAchievements(),
            achievementRepository.getAchievementProgress(),
            achievementPointsManager.totalPoints,
            activityDataRepository.getActivityData(365), // добавить
        ) { achievements, progress, points, activityData ->
            // Load month stats
            val currentStats = activityDataRepository.getCurrentMonthStats()
            val previousStats = activityDataRepository.getPreviousMonthStats()

            AchievementScreenState.Success(
                achievements = achievements,
                progress = progress,
                userPoints = points,
                activityData = activityData, // теперь реальные данные
                currentMonthStats = currentStats,
                previousMonthStats = previousStats,
            )
        }.catch { error ->
            // Log error and emit error state or empty state
            error.printStackTrace()
        }.collect { state ->
            mutableState.value = state
        }
    }
}
```

**Step 3: Обновить State sealed class**

```kotlin
sealed class AchievementScreenState {
    data object Loading : AchievementScreenState()

    data class Success(
        val achievements: List<Achievement> = emptyList(),
        val progress: Map<String, AchievementProgress> = emptyMap(),
        val userPoints: Int = 0,
        val selectedCategory: AchievementCategory = AchievementCategory.BOTH,
        val activityData: List<DayActivity> = emptyList(), // теперь реальный тип
        val currentMonthStats: MonthStats = MonthStats(0, 0, 0, 0),
        val previousMonthStats: MonthStats = MonthStats(0, 0, 0, 0),
    ) : AchievementScreenState() {
        // ... existing computed properties ...
    }
}
```

**Step 4: Добавить импорты**

```kotlin
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import kotlinx.coroutines.flow.catch
```

**Step 5: Unit-тест для ScreenModel**

Создать: `app/src/test/java/eu/kanade/presentation/achievement/screenmodel/AchievementScreenModelTest.kt`

```kotlin
package eu.kanade.presentation.achievement.screenmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.AchievementLoader
import tachiyomi.domain.achievement.repository.AchievementPointsManager
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var achievementRepository: AchievementRepository
    private lateinit var achievementLoader: AchievementLoader
    private lateinit var achievementPointsManager: AchievementPointsManager
    private lateinit var activityDataRepository: ActivityDataRepository

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        achievementRepository = mock()
        achievementLoader = mock()
        achievementPointsManager = mock()
        activityDataRepository = mock()

        whenever(achievementRepository.getAllAchievements()).thenReturn(flowOf(emptyList()))
        whenever(achievementRepository.getAchievementProgress()).thenReturn(flowOf(emptyMap()))
        whenever(achievementPointsManager.totalPoints).thenReturn(flowOf(100))
        whenever(activityDataRepository.getActivityData(365)).thenReturn(flowOf(emptyList()))
    }

    @Test
    fun `should load activity data on init`() = runTest(testDispatcher) {
        // Given
        val activityData = listOf(
            DayActivity(
                date = LocalDate.now(),
                level = 3,
                type = ActivityType.READING,
            )
        )
        val monthStats = MonthStats(10, 5, 200, 2)

        whenever(activityDataRepository.getActivityData(365)).thenReturn(flowOf(activityData))
        whenever(activityDataRepository.getCurrentMonthStats()).thenReturn(monthStats)
        whenever(activityDataRepository.getPreviousMonthStats()).thenReturn(monthStats)

        // When
        val viewModel = AchievementScreenModel(
            achievementRepository,
            achievementLoader,
            achievementPointsManager,
            activityDataRepository,
        )

        // Then
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<AchievementScreenState.Success>(state)
        assertEquals(1, state.activityData.size)
        assertEquals(3, state.activityData[0].level)
    }
}
```

**Step 6: Запустить тесты**

```bash
./gradlew :app:test --tests "eu.kanade.presentation.achievement.screenmodel.AchievementScreenModelTest"
```

**Step 7: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/achievement/screenmodel/AchievementScreenModel.kt
git add app/src/test/java/eu/kanade/presentation/achievement/screenmodel/AchievementScreenModelTest.kt
git commit -m "feat(achievements): integrate ActivityDataRepository into screen model

- Add repository dependency to AchievementScreenModel
- Load real activity data and month stats
- Add unit tests for data loading"
```

---

## Task 7: Обновить DI для ActivityDataRepository

**Files:**
- Modify: `app/src/main/java/eu/kanade/domain/DomainModule.kt` (или аналогичный DI модуль)

**Step 1: Найти DI модуль**

Найти файл где регистрируются репозитории.

**Step 2: Добавить биндинг репозитория**

```kotlin
single<ActivityDataRepository> { ActivityDataRepositoryImpl() }
```

Или для Koin/Dagger/Hilt - соответствующий синтаксис.

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/domain/DomainModule.kt
git commit -m "chore(di): register ActivityDataRepository in DI module"
```

---

## Task 8: Исправить импорты в AchievementActivityGraph

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/achievement/components/AchievementActivityGraph.kt`

**Step 1: Заменить локальные data class на доменные модели**

Удалить локальные определения:
```kotlin
// Удалить эти строки (lines 67-80):
// data class DayActivity(...)
// enum class ActivityType { ... }
```

**Step 2: Обновить импорты**

```kotlin
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.ActivityType
```

**Step 3: UI-тест - проверка компиляции**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/eu/kanade/presentation/achievement/components/AchievementActivityGraph.kt
git commit -m "refactor(achievements): use domain models in ActivityGraph

- Replace local DayActivity/ActivityType with domain models
- Remove duplicate type definitions"
```

---

## Task 9: Создать интеграционный UI-тест для экрана достижений

**Files:**
- Create: `app/src/androidTest/java/eu/kanade/presentation/achievement/AchievementScreenTest.kt`

**Step 1: Создать UI-тест**

```kotlin
package eu.kanade.presentation.achievement

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import eu.kanade.presentation.achievement.ui.AchievementScreen
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.LocalAuroraColors
import org.junit.Rule
import org.junit.Test
import tachiyomi.domain.achievement.model.*
import java.time.LocalDate

class AchievementScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun achievementScreen_displaysStatsComparison() {
        // Given
        val currentStats = MonthStats(
            chaptersRead = 127,
            episodesWatched = 45,
            timeInAppMinutes = 2340,
            achievementsUnlocked = 8,
        )
        val previousStats = MonthStats(
            chaptersRead = 98,
            episodesWatched = 62,
            timeInAppMinutes = 1890,
            achievementsUnlocked = 5,
        )

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                AchievementScreen(
                    // ... provide required parameters
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Сравнение с прошлым месяцем").assertIsDisplayed()
        composeTestRule.onNodeWithText("127").assertIsDisplayed()
        composeTestRule.onNodeWithText("Глав прочитано").assertIsDisplayed()
    }

    @Test
    fun achievementScreen_displaysActivityGraph() {
        // Given
        val activityData = generateTestActivityData()

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                AchievementActivityGraph(activityData = activityData)
            }
        }

        // Then
        composeTestRule.onNodeWithText("Активность за год").assertIsDisplayed()
        composeTestRule.onNodeWithText("Меньше").assertIsDisplayed()
        composeTestRule.onNodeWithText("Больше").assertIsDisplayed()

        // Check month labels are displayed
        composeTestRule.onNodeWithText("ЯНВ").assertIsDisplayed()
        composeTestRule.onNodeWithText("ФЕВ").assertIsDisplayed()
    }

    @Test
    fun statCards_haveUniformHeight() {
        // Given - stats with very different value lengths
        val currentStats = MonthStats(
            chaptersRead = 9999,
            episodesWatched = 1,
            timeInAppMinutes = 9999,
            achievementsUnlocked = 999,
        )

        // When
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                AchievementStatsComparison(
                    currentMonth = currentStats,
                    previousMonth = currentStats,
                )
            }
        }

        // Then - all stat cards should be visible
        composeTestRule.onNodeWithText("9999").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }

    private fun generateTestActivityData(): List<DayActivity> {
        return (0..365).map { daysAgo ->
            DayActivity(
                date = LocalDate.now().minusDays(daysAgo.toLong()),
                level = (0..4).random(),
                type = ActivityType.entries.random(),
            )
        }
    }
}
```

**Step 2: Запустить UI-тесты**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "eu.kanade.presentation.achievement.AchievementScreenTest"
```

**Step 3: Commit**

```bash
git add app/src/androidTest/java/eu/kanade/presentation/achievement/AchievementScreenTest.kt
git commit -m "test(achievements): add UI tests for achievement screen

- Test stats comparison display
- Test activity graph rendering
- Test stat card uniformity"
```

---

## Task 10: Финальная верификация и ревью

**Step 1: Запустить полный набор тестов**

```bash
./gradlew :domain:test :data:test :app:test
```

**Step 2: Проверить компиляцию**

```bash
./gradlew :app:compileDebugKotlin
```

**Step 3: Проверить Detekt/Ktlint если используется**

```bash
./gradlew detekt ktlintCheck
```

**Step 4: Создать сводку изменений**

```bash
git log --oneline --since="1 day ago"
```

**Step 5: Final commit с тегом**

```bash
git tag -a v1.0-achievement-refactor -m "Achievement UI refactoring complete"
```

---

## Summary of Changes

| Компонент | Изменения |
|-----------|-----------|
| `AuroraTheme.kt` | Добавлены семантические цвета (success, warning, error, achievementGold) |
| `AchievementStatsComparison.kt` | Унифицированы размеры карточек (120.dp), заменены хардкод цвета |
| `AchievementActivityGraph.kt` | Исправлено позиционирование меток месяцев, улучшена видимость сетки |
| `ActivityDataRepository` | Новый репозиторий для данных активности |
| `AchievementScreenModel.kt` | Интеграция репозитория, реальные данные для UI |
| Тесты | Unit-тесты для репозитория и view model, UI-тесты для компонентов |

---

## Testing Checklist

- [ ] Все карточки статистики имеют одинаковую высоту (120.dp)
- [ ] Цвета соответствуют теме Aurora (не серые)
- [ ] Метки месяцев корректно позиционируются над графиком
- [ ] Сетка активности видна (бордеры ячеек)
- [ ] Данные активности загружаются из репозитория
- [ ] Unit-тесты проходят
- [ ] UI-тесты проходят
- [ ] Компиляция успешна
