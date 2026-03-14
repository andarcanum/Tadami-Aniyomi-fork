# Testing Patterns

**Analysis Date:** 2026-03-14

## Test Framework

**Runner:**
- JUnit Jupiter (JUnit 5) - Version 5.11.4
- Configured via `gradle/libs.versions.toml`

**Assertion Library:**
- Kotest assertions - Version 5.9.1 (`io.kotest:kotest-assertions-core`)
- Common matchers: `shouldBe`, `shouldContainExactly`, `shouldThrow`

**Mocking Library:**
- MockK - Version 1.13.17 (`io.mockk:mockk`)
- Supports coroutines: `coEvery`, `coJustRun`, `coVerify`

**Test Support:**
- Kotlin Test - Version 2.1.0
- Robolectric - Version 4.13 (for Android instrumentation tests)

**Run Commands:**
```bash
./gradlew testDebugUnitTest              # Run all unit tests
./gradlew testDebugUnitTest --tests "*X*" # Targeted tests
./gradlew testDebugUnitTest             # Debug variant tests only
```

## Test File Organization

**Location:**
- Co-located with source in `src/test/` directory
- Mirror source directory structure
- Example: `domain/src/test/java/tachiyomi/domain/entries/novel/interactor/`

**Naming:**
- `<ClassName>Test.kt` pattern: `NovelTest.kt`, `NetworkToLocalNovelTest.kt`
- Test classes match the class being tested

**Structure:**
```
src/
├── main/java/
│   └── tachiyomi/domain/entries/novel/
│       ├── model/Novel.kt
│       └── interactor/NetworkToLocalNovel.kt
└── test/java/
    └── tachiyomi/domain/entries/novel/
        ├── model/NovelTest.kt
        └── interactor/NetworkToLocalNovelTest.kt
```

## Test Structure

**Suite Organization:**
```kotlin
@Execution(ExecutionMode.CONCURRENT)
class NovelTest {

    @Test
    fun `create returns default novel`() {
        val novel = Novel.create()

        novel.id shouldBe -1L
        novel.source shouldBe -1L
    }
}
```

**Setup/Teardown:**
```kotlin
class NovelLibraryScreenModelTest {

    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        // Initialize mocks and dependencies
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```

**Patterns:**
- `@Execution(ExecutionMode.CONCURRENT)` for parallel test execution
- `@BeforeEach` / `@AfterEach` for lifecycle management
- Test dispatcher setup for coroutine testing

## Mocking

**Framework:** MockK

**Patterns:**
```kotlin
// Property mocking
every { getLibraryNovel.subscribe() } returns libraryFlow

// Coroutine mocking
coEvery { chapterRepository.getChapterByNovelId(any(), any()) } returns emptyList()

// Relaxed mocks for event buses
eventBus = mockk(relaxed = true)

// Verification
verify {
    eventBus.tryEmit(
        match {
            it is AchievementEvent.LibraryAdded &&
                it.entryId == novelId &&
                it.type == AchievementCategory.NOVEL
        },
    )
}
```

**What to Mock:**
- Repository interfaces
- Database handlers
- Preference stores
- Event buses

**What NOT to Mock:**
- Domain models (use real objects)
- Simple value objects

## Inline Fakes

**Pattern (preferred for simple cases):**
```kotlin
private class FakeNovelRepository(
    private val existing: Novel? = null,
    lookupResults: List<Novel?> = emptyList(),
    private val insertedId: Long? = 100L,
) : NovelRepository {
    private val lookupResults = lookupResults.toMutableList()
    var inserted: Novel? = null

    override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? {
        if (lookupResults.isNotEmpty()) {
            return lookupResults.removeAt(0)
        }
        return existing
    }

    override suspend fun insertNovel(novel: Novel): Long? {
        inserted = novel
        return insertedId
    }
    // ... other methods throw error("not used")
}
```

**When to use Fakes:**
- Repository implementations (more control than MockK)
- Preference stores
- Complex stateful dependencies

## Fixtures and Factories

**Test Data Builders:**
```kotlin
private fun libraryNovel(
    id: Long,
    title: String,
    total: Long = 10L,
    read: Long = 1L,
    status: Long = 0L,
    lastRead: Long = 0L,
    fetchInterval: Int = 0,
): LibraryNovel {
    return LibraryNovel(
        novel = Novel.create().copy(
            id = id,
            title = title,
            url = "https://example.com/$id",
            source = 1L,
            favorite = true,
            status = status,
            fetchInterval = fetchInterval,
        ),
        category = 0L,
        totalChapters = total,
        readCount = read,
        bookmarkCount = 0L,
        latestUpload = 0L,
        chapterFetchedAt = 0L,
        lastRead = lastRead,
    )
}
```

**Location:**
- Private factory methods within test class
- Helper functions at bottom of test file

## Coroutine Testing

**Test Dispatcher Setup:**
```kotlin
private lateinit var testDispatcher: TestDispatcher

@BeforeEach
fun setup() {
    testDispatcher = StandardTestDispatcher()
    Dispatchers.setMain(testDispatcher)
}

@AfterEach
fun tearDown() {
    Dispatchers.resetMain()
}
```

**Test Execution:**
```kotlin
@Test
fun `filters library novels by search query`() = runTest(testDispatcher) {
    libraryFlow.value = listOf(first, second)
    val screenModel = trackedNovelLibraryScreenModel(...)

    testDispatcher.scheduler.advanceUntilIdle()
    screenModel.search("Second")
    testDispatcher.scheduler.advanceUntilIdle()

    screenModel.state.value.items.shouldContainExactly(second)
}
```

**Patterns:**
- Use `runTest(testDispatcher)` for coroutine tests
- `testDispatcher.scheduler.advanceUntilIdle()` to process pending coroutines
- `Dispatchers.setMain(testDispatcher)` in setup, `resetMain()` in teardown

## Database Testing

**In-Memory SQLite:**
```kotlin
@BeforeEach
fun setup() {
    driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    NovelDatabase.Schema.create(driver)
    database = NovelDatabase(
        driver = driver,
        novel_historyAdapter = Novel_history.Adapter(...),
        novelsAdapter = Novels.Adapter(...),
    )
    handler = AndroidNovelDatabaseHandler(
        db = database,
        driver = driver,
        queryDispatcher = Dispatchers.Default,
        transactionDispatcher = Dispatchers.Default,
    )
}
```

**Test Database Factory:**
```kotlin
fun createTestNovelDatabase(driver: SqlDriver): NovelDatabase {
    NovelDatabase.Schema.create(driver)
    return NovelDatabase(
        driver = driver,
        novel_historyAdapter = Novel_history.Adapter(
            last_readAdapter = DateColumnAdapter,
        ),
        novelsAdapter = Novels.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
        ),
    )
}
```

## Coverage

**Requirements:** None explicitly enforced

**View Coverage:** Not observed in build configuration

## Test Types

**Unit Tests:**
- Focus on domain interactors: `NetworkToLocalNovelTest.kt`
- Domain models: `NovelTest.kt`
- Pure functions and business logic

**Integration Tests:**
- Repository implementations: `NovelRepositoryImplTest.kt`
- Database handlers: `NovelDatabaseHandlerTest.kt`
- Test with real in-memory database

**ScreenModel Tests:**
- UI state management: `NovelLibraryScreenModelTest.kt`
- Filter/sort logic
- User interactions

## Common Patterns

**Async Testing:**
```kotlin
@Test
fun `inserts novel when not found`() {
    runBlocking {
        val repository = FakeNovelRepository()
        val interactor = NetworkToLocalNovel(repository)

        val novel = Novel.create().copy(url = "/novel/1", title = "Example", source = 1L)
        val result = interactor.await(novel)

        result.id shouldBe 100L
        repository.inserted shouldBe novel
    }
}
```

**Error Testing:**
```kotlin
@Test
fun `throws when insert returns null and second lookup missing`() {
    runBlocking {
        val repository = FakeNovelRepository(
            lookupResults = listOf(null, null),
            insertedId = null,
        )
        val interactor = NetworkToLocalNovel(repository)

        val novel = Novel.create().copy(url = "/novel/1", title = "Remote", source = 1L)

        shouldThrow<IllegalStateException> {
            interactor.await(novel)
        }
    }
}
```

**Flow/State Testing:**
```kotlin
@Test
fun `get library novel returns favorites`() = runTest {
    repository.insertNovel(...)

    val library = repository.getLibraryNovelAsFlow().first()

    library.size shouldBe 1
    library.first().novel.title shouldBe "Library"
}
```

---

*Testing analysis: 2026-03-14*
